use crate::platform::{
    auth::AuthUser,
    envelope::{envelope_json, envelope_response},
    state::AppState,
};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde::Serialize;
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, MySql, MySqlPool, Row, Transaction};
use std::borrow::Cow;
use std::io::Write;

#[derive(Debug)]
enum ExamError {
    Parameter(&'static str),
    Forbidden,
    Terminal(&'static str),
    Database(sqlx::Error),
    Json(serde_json::Error),
}

impl From<sqlx::Error> for ExamError {
    fn from(error: sqlx::Error) -> Self {
        Self::Database(error)
    }
}

impl From<serde_json::Error> for ExamError {
    fn from(error: serde_json::Error) -> Self {
        Self::Json(error)
    }
}

fn exam_response(result: Result<Value, ExamError>) -> Response {
    match result {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(ExamError::Parameter(message)) => {
            envelope_response(StatusCode::OK, 202, message, message)
        }
        Err(ExamError::Forbidden) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Err(ExamError::Terminal(message)) => {
            envelope_response(StatusCode::OK, 208, message, message)
        }
        Err(error) => {
            match error {
                ExamError::Database(error) => tracing::error!(%error, "exam database failure"),
                ExamError::Json(error) => tracing::error!(%error, "exam snapshot JSON failure"),
                _ => unreachable!(),
            }
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

async fn complete_exam_transaction(
    transaction: Transaction<'_, MySql>,
    result: Result<Value, ExamError>,
) -> Response {
    match result {
        Ok(data) => exam_response(transaction.commit().await.map(|_| data).map_err(Into::into)),
        Err(error) => {
            if let Err(rollback) = transaction.rollback().await {
                tracing::error!(%rollback, "exam transaction rollback failed");
            }
            exam_response(Err(error))
        }
    }
}

fn request_text(value: Option<&Value>) -> Result<Option<Cow<'_, str>>, ExamError> {
    match value {
        None | Some(Value::Null) => Ok(None),
        Some(Value::String(text)) => Ok(Some(Cow::Borrowed(text))),
        Some(Value::Number(number)) => Ok(Some(Cow::Owned(number.to_string()))),
        Some(Value::Bool(boolean)) => {
            Ok(Some(Cow::Borrowed(if *boolean { "true" } else { "false" })))
        }
        Some(_) => Err(ExamError::Parameter("请求参数错误")),
    }
}

fn request_integer(value: Option<&Value>) -> Result<Option<i32>, ExamError> {
    let invalid = || ExamError::Parameter("请求参数错误");
    match value {
        None | Some(Value::Null) => Ok(None),
        Some(Value::Number(number)) => {
            if let Some(integer) = number.as_i64() {
                return i32::try_from(integer).map(Some).map_err(|_| invalid());
            }
            let integer = number.as_f64().ok_or_else(invalid)?.trunc();
            if !integer.is_finite()
                || integer < f64::from(i32::MIN)
                || integer > f64::from(i32::MAX)
            {
                return Err(invalid());
            }
            Ok(Some(integer as i32))
        }
        Some(Value::String(text)) if text.trim().is_empty() => Ok(None),
        Some(Value::String(text)) => text.trim().parse().map(Some).map_err(|_| invalid()),
        Some(_) => Err(invalid()),
    }
}

async fn is_admin(pool: &MySqlPool, user_id: &str) -> Result<bool, ExamError> {
    Ok(sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM t_user_role ur JOIN t_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.is_admin=0")
        .bind(user_id).fetch_one(pool).await? > 0)
}

fn exam_json(row: &MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "title": row.get::<Option<String>, _>("title"),
        "startTime": row.get::<Option<String>, _>("start_time"),
        "endTime": row.get::<Option<String>, _>("end_time"),
        "duration": row.get::<Option<String>, _>("duration"),
        "createUserId": row.get::<Option<String>, _>("create_user_id"),
        "createTime": row.get::<Option<String>, _>("create_time"),
        "teacher": row.get::<Option<String>, _>("teacher"),
        "state": row.get::<Option<i32>, _>("state"),
    })
}

fn candidate_json(row: &MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "userId": row.get::<Option<String>, _>("user_id"),
        "examId": row.get::<Option<String>, _>("exam_id"),
        "content": row.get::<Option<String>, _>("content"),
        "state": row.get::<Option<i32>, _>("state"),
        "score": row.get::<Option<i32>, _>("score"),
        "startTime": row.get::<Option<String>, _>("start_time"),
        "endTime": row.get::<Option<String>, _>("end_time"),
        "isSelfTesting": row.get::<Option<i32>, _>("is_self_testing"),
    })
}

async fn locked_exam(
    transaction: &mut Transaction<'_, MySql>,
    id: &str,
) -> Result<MySqlRow, ExamError> {
    sqlx::query("SELECT * FROM t_theory_knowledge_exam WHERE id=? FOR UPDATE")
        .bind(id)
        .fetch_optional(&mut **transaction)
        .await?
        .ok_or(ExamError::Parameter("未查询到考试"))
}

// The exam PK lock serializes lifecycle writers. Discover children without unindexed
// locking scans, then take only the candidate PK lock (user -> exam -> candidate).
async fn locked_candidate(
    transaction: &mut Transaction<'_, MySql>,
    exam_id: &str,
    user_id: &str,
) -> Result<MySqlRow, ExamError> {
    let ids = sqlx::query_scalar::<_, String>(
        "SELECT id FROM t_theory_knowledge_exam_user WHERE exam_id=? AND user_id=? LIMIT 2",
    )
    .bind(exam_id)
    .bind(user_id)
    .fetch_all(&mut **transaction)
    .await?;
    if ids.len() > 1 {
        return Err(ExamError::Parameter("同一考试存在重复考生记录"));
    }
    let id = ids.first().ok_or(ExamError::Forbidden)?;
    Ok(
        sqlx::query("SELECT * FROM t_theory_knowledge_exam_user WHERE id=? FOR UPDATE")
            .bind(id)
            .fetch_one(&mut **transaction)
            .await?,
    )
}

fn require_active_candidate(exam: &MySqlRow, candidate: &MySqlRow) -> Result<(), ExamError> {
    if exam.get::<Option<i32>, _>("state") != Some(2)
        || !matches!(candidate.get::<Option<i32>, _>("state"), Some(1 | 2))
        || candidate.get::<Option<String>, _>("end_time").is_some()
    {
        return Err(ExamError::Terminal("考试或答卷已结束，不能继续作答"));
    }
    Ok(())
}

const PAPER_SECTIONS: [(&str, &str); 5] = [
    ("single_choice_list", "singleChoice"),
    ("multiple_choice_list", "multipleChoice"),
    ("judge_list", "judge"),
    ("completion_list", "completion"),
    ("short_answer", "shortAnswer"),
];

struct GsonFormatter;

impl serde_json::ser::Formatter for GsonFormatter {
    fn write_string_fragment<W: ?Sized + Write>(
        &mut self,
        writer: &mut W,
        fragment: &str,
    ) -> std::io::Result<()> {
        let mut start = 0;
        for (index, character) in fragment.char_indices() {
            let escape: &[u8] = match character {
                '<' => b"\\u003c",
                '>' => b"\\u003e",
                '&' => b"\\u0026",
                '=' => b"\\u003d",
                '\'' => b"\\u0027",
                '\u{2028}' => b"\\u2028",
                '\u{2029}' => b"\\u2029",
                _ => continue,
            };
            writer.write_all(&fragment.as_bytes()[start..index])?;
            writer.write_all(escape)?;
            start = index + character.len_utf8();
        }
        writer.write_all(&fragment.as_bytes()[start..])
    }
}

fn write_gson_json(output: &mut Vec<u8>, value: &impl Serialize) {
    value
        .serialize(&mut serde_json::Serializer::with_formatter(
            output,
            GsonFormatter,
        ))
        .expect("serializing JSON to Vec cannot fail");
}

fn gson_json(value: &Value) -> String {
    let mut output = Vec::new();
    write_gson_json(&mut output, value);
    String::from_utf8(output).expect("JSON encoder only emits UTF-8")
}

fn question_list_json(questions: &[Value]) -> Result<String, ExamError> {
    let mut output = vec![b'['];
    for (index, question) in questions.iter().enumerate() {
        if !question.is_object() {
            return Err(ExamError::Parameter("试卷题目格式错误"));
        }
        if index != 0 {
            output.push(b',');
        }
        output.push(b'{');
        let mut first = true;
        // Gson serializes TestPaperQuestionDto in declaration order and omits null fields.
        for key in [
            "id",
            "testpaperId",
            "parentId",
            "score",
            "sort",
            "type",
            "topic",
            "options",
            "answer",
            "analysis",
        ] {
            let Some(value) = question.get(key).filter(|value| !value.is_null()) else {
                continue;
            };
            let numeric = matches!(key, "score" | "sort" | "type");
            let integer = if numeric {
                request_integer(Some(value))?
            } else {
                None
            };
            if numeric && integer.is_none() {
                continue;
            }
            if !first {
                output.push(b',');
            }
            first = false;
            output.push(b'"');
            output.extend_from_slice(key.as_bytes());
            output.extend_from_slice(b"\":");
            if numeric {
                write_gson_json(&mut output, &integer);
            } else {
                write_gson_json(&mut output, &request_text(Some(value))?);
            }
        }
        output.push(b'}');
    }
    output.push(b']');
    Ok(String::from_utf8(output).expect("JSON encoder only emits UTF-8"))
}

fn completion_slots(topic: &str) -> usize {
    let mut rest = topic;
    let mut count = 0;
    while let Some((offset, marker)) = ["________", "(___)", "$_$"]
        .into_iter()
        .filter_map(|marker| rest.find(marker).map(|offset| (offset, marker)))
        .min_by_key(|(offset, _)| *offset)
    {
        count += 1;
        rest = &rest[offset + marker.len()..];
    }
    count
}

fn exam_time(time: chrono::DateTime<chrono::Utc>) -> String {
    time.with_timezone(&chrono::FixedOffset::east_opt(8 * 3600).expect("UTC+8 is valid"))
        .format("%Y-%m-%d %H:%M:%S")
        .to_string()
}

fn project_questions(snapshot: &str, section: &str) -> Result<String, ExamError> {
    let mut questions = if snapshot.trim().is_empty() || snapshot == "null" {
        Vec::new()
    } else {
        serde_json::from_str::<Vec<Value>>(snapshot)?
    };
    for question in &mut questions {
        question["analysis"] = Value::Null;
        question["answer"] = json!(match section {
            "multipleChoice" => "[]".to_owned(),
            "completion" => gson_json(&json!(vec![
                "";
                completion_slots(
                    question["topic"].as_str().unwrap_or("")
                )
            ])),
            "shortAnswer" => String::new(),
            _ => "\"\"".to_owned(),
        });
    }
    question_list_json(&questions)
}

async fn projected_paper(
    transaction: &mut Transaction<'_, MySql>,
    exam: &MySqlRow,
    candidate: Option<&MySqlRow>,
    controller: bool,
) -> Result<Value, ExamError> {
    let row =
        sqlx::query("SELECT * FROM t_theory_knowledge_exam_test_paper WHERE exam_id=? LIMIT 1")
            .bind(exam.get::<String, _>("id"))
            .fetch_optional(&mut **transaction)
            .await?
            .ok_or(ExamError::Parameter("未查询到试卷快照"))?;
    let review = candidate.is_some_and(|candidate| {
        candidate.get::<Option<i32>, _>("state") == Some(4)
            && (candidate.get::<Option<i32>, _>("is_self_testing") == Some(0)
                || exam.get::<Option<i32>, _>("state") == Some(4))
    });
    let mut paper = json!({
        "id": row.get::<String, _>("id"), "name": row.get::<Option<String>, _>("name"),
        "testPaperId": row.get::<Option<String>, _>("test_paper_id"), "total": row.get::<Option<i32>, _>("total"),
        "passTheExamThan": row.get::<Option<String>, _>("pass_the_exam_than"), "examId": row.get::<Option<String>, _>("exam_id"),
        "passMark": row.get::<Option<i32>, _>("pass_mark"),
    });
    for (column, section) in PAPER_SECTIONS {
        let snapshot = row.get::<Option<String>, _>(column);
        let value = if controller || review {
            snapshot
        } else {
            Some(project_questions(
                snapshot.as_deref().unwrap_or(""),
                section,
            )?)
        };
        let key = if section == "shortAnswer" {
            section.to_owned()
        } else {
            format!("{section}List")
        };
        paper[&key] = json!(value);
    }
    Ok(paper)
}

async fn can_control_exam(
    transaction: &mut Transaction<'_, MySql>,
    actor: &str,
    exam: &MySqlRow,
    admin: bool,
) -> Result<bool, ExamError> {
    let self_testing = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM t_theory_knowledge_exam_user WHERE exam_id=? AND is_self_testing=0",
    )
    .bind(exam.get::<String, _>("id"))
    .fetch_one(&mut **transaction)
    .await?
        > 0;
    Ok(!self_testing
        && (admin
            || exam.get::<Option<String>, _>("create_user_id").as_deref() == Some(actor)
            || exam.get::<Option<String>, _>("teacher").as_deref() == Some(actor)))
}

pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let state_filter = body.get("state").and_then(Value::as_bool).unwrap_or(true);
    let states = if state_filter { vec![1, 2] } else { vec![3, 4] };
    let rows=match sqlx::query("SELECT DISTINCT e.*,t.user_name AS teacher_name FROM t_theory_knowledge_exam e JOIN t_theory_knowledge_exam_user u ON u.exam_id=e.id LEFT JOIN t_user t ON t.id=e.teacher WHERE u.is_self_testing=1").fetch_all(pool).await{Ok(r)=>r,Err(_)=>return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")};
    let admin=sqlx::query_scalar::<_,i64>("SELECT COUNT(*) FROM t_user_role ur JOIN t_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.is_admin=0").bind(&user.id).fetch_one(pool).await.unwrap_or(0)>0;
    let data=rows.into_iter().filter(|row|states.contains(&row.get::<Option<i32>,_>("state").unwrap_or_default())&&(admin||row.get::<Option<String>,_>("create_user_id").as_deref()==Some(user.id.as_str())||row.get::<Option<String>,_>("teacher").as_deref()==Some(user.id.as_str()))).map(|row|json!({"dis":row.get::<String,_>("id"),"userName":row.get::<Option<String>,_>("teacher_name"),"id":row.get::<String,_>("id"),"title":row.get::<Option<String>,_>("title"),"start_time":row.get::<Option<String>,_>("start_time"),"end_time":row.get::<Option<String>,_>("end_time"),"duration":row.get::<Option<String>,_>("duration"),"create_user_id":row.get::<Option<String>,_>("create_user_id"),"create_time":row.get::<Option<String>,_>("create_time"),"teacher":row.get::<Option<String>,_>("teacher"),"state":row.get::<Option<i32>,_>("state")})).collect::<Vec<_>>();
    envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", "")
}

pub(crate) async fn detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result = async {
        let id = request_text(body.get("id"))?.unwrap_or_default();
        let admin = is_admin(pool, &user.id).await?;
        let mut transaction = pool.begin().await?;
        let exam = sqlx::query("SELECT e.*,u.user_name AS teacher_name FROM t_theory_knowledge_exam e LEFT JOIN t_user u ON u.id=e.teacher WHERE e.id=?")
            .bind(id.as_ref()).fetch_optional(&mut *transaction).await?
            .ok_or(ExamError::Parameter("未查询到考试"))?;
        let candidate = sqlx::query("SELECT * FROM t_theory_knowledge_exam_user WHERE exam_id=? AND user_id=? LIMIT 1")
            .bind(id.as_ref()).bind(&user.id).fetch_optional(&mut *transaction).await?;
        let controller = can_control_exam(&mut transaction, &user.id, &exam, admin).await?;
        if !controller && candidate.is_none() { return Err(ExamError::Forbidden); }
        let rows = sqlx::query("SELECT c.*,u.user_account AS user_name,u.user_img,u.wkno FROM t_theory_knowledge_exam_user c LEFT JOIN t_user u ON u.id=c.user_id WHERE c.exam_id=?")
            .bind(id.as_ref()).fetch_all(&mut *transaction).await?;
        let users = rows.iter().filter(|row| controller || row.get::<Option<String>, _>("user_id").as_deref() == Some(user.id.as_str())).map(|row| json!({
            "userName": row.get::<Option<String>, _>("user_name"), "user_img": row.get::<Option<String>, _>("user_img"),
            "wkno": row.get::<Option<String>, _>("wkno"), "id": row.get::<String, _>("id"),
            "user_id": row.get::<Option<String>, _>("user_id"), "exam_id": row.get::<Option<String>, _>("exam_id"),
            "content": row.get::<Option<String>, _>("content"), "state": row.get::<Option<i32>, _>("state"),
            "score": row.get::<Option<i32>, _>("score"), "start_time": row.get::<Option<String>, _>("start_time"),
            "end_time": row.get::<Option<String>, _>("end_time"), "is_self_testing": row.get::<Option<i32>, _>("is_self_testing"),
        })).collect::<Vec<_>>();
        let paper = projected_paper(&mut transaction, &exam, candidate.as_ref(), controller).await?;
        let exam = json!({
            "userName": exam.get::<Option<String>, _>("teacher_name"), "id": exam.get::<String, _>("id"),
            "title": exam.get::<Option<String>, _>("title"), "start_time": exam.get::<Option<String>, _>("start_time"),
            "end_time": exam.get::<Option<String>, _>("end_time"), "duration": exam.get::<Option<String>, _>("duration"),
            "create_user_id": exam.get::<Option<String>, _>("create_user_id"), "create_time": exam.get::<Option<String>, _>("create_time"),
            "teacher": exam.get::<Option<String>, _>("teacher"), "state": exam.get::<Option<i32>, _>("state"),
        });
        transaction.commit().await?;
        Ok(json!({"exam": exam, "user": users, "paper": paper}))
    }.await;
    exam_response(result)
}

pub(crate) async fn self_test(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let admin = match is_admin(pool, &user.id).await {
        Ok(admin) => admin,
        Err(error) => return exam_response(Err(error)),
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return exam_response(Err(error.into())),
    };
    let result = async {
        crate::statistical::lock_user(&mut transaction, &user.id).await?;
        let supplied_id = request_text(body.get("id"))?.filter(|id| !id.trim().is_empty());
        let existing = if let Some(id) = supplied_id.as_deref() {
            let exam = locked_exam(&mut transaction, id).await?;
            if exam.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) && !admin {
                return Err(ExamError::Forbidden);
            }
            let candidates = sqlx::query("SELECT * FROM t_theory_knowledge_exam_user WHERE exam_id=? LIMIT 2")
                .bind(id).fetch_all(&mut *transaction).await?;
            if candidates.len() != 1 || candidates[0].get::<Option<i32>, _>("is_self_testing") != Some(0)
                || candidates[0].get::<Option<String>, _>("user_id") != exam.get::<Option<String>, _>("create_user_id") {
                return Err(ExamError::Forbidden);
            }
            let candidate = locked_candidate(&mut transaction, id, candidates[0].get::<String, _>("user_id").as_str()).await?;
            if matches!(exam.get::<Option<i32>, _>("state"), Some(3 | 4))
                || matches!(candidate.get::<Option<i32>, _>("state"), Some(3 | 4))
                || candidate.get::<Option<String>, _>("end_time").is_some() {
                return Err(ExamError::Terminal("考试已结束，不能重建自测"));
            }
            Some(exam)
        } else { None };
        // Java dereferences a missing paper and accepts {}; reject both before any write.
        let paper = body.get("testPaper").filter(|paper| paper.as_object().is_some_and(|paper| !paper.is_empty()))
            .ok_or(ExamError::Parameter("试卷不能为空"))?;
        let sections = PAPER_SECTIONS.iter().map(|(_, section)| {
            let questions = match paper.get(*section) {
                None | Some(Value::Null) => &[][..],
                Some(Value::Array(items)) => items.as_slice(),
                _ => return Err(ExamError::Parameter("试卷题目格式错误")),
            };
            question_list_json(questions)
        }).collect::<Result<Vec<_>, _>>()?;
        let exam_id = existing.as_ref().map(|exam| exam.get::<String, _>("id")).unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
        let creator = existing.as_ref().and_then(|exam| exam.get::<Option<String>, _>("create_user_id")).unwrap_or_else(|| user.id.clone());
        let create_time = existing.as_ref().map(|exam| exam.get::<Option<String>, _>("create_time"))
            .unwrap_or_else(|| Some(crate::platform::clock::now_time_millis().to_string()));
        let start_time = exam_time(chrono::Utc::now());
        let title = request_text(body.get("title"))?;
        let duration = request_text(body.get("duration"))?;
        let teacher = request_text(body.get("teacher"))?;
        if existing.is_some() {
            sqlx::query("UPDATE t_theory_knowledge_exam SET title=?,start_time=?,end_time=NULL,duration=?,teacher=?,state=2 WHERE id=?")
                .bind(title.as_deref()).bind(&start_time).bind(duration.as_deref()).bind(teacher.as_deref()).bind(&exam_id).execute(&mut *transaction).await?;
            delete_exam_children(&mut transaction, &exam_id).await?;
        } else {
            sqlx::query("INSERT INTO t_theory_knowledge_exam (id,title,start_time,duration,create_user_id,create_time,teacher,state) VALUES (?,?,?,?,?,?,?,2)")
                .bind(&exam_id).bind(title.as_deref()).bind(&start_time).bind(duration.as_deref()).bind(&creator).bind(&create_time).bind(teacher.as_deref()).execute(&mut *transaction).await?;
        }
        let name = request_text(paper.get("name"))?;
        let source_id = request_text(paper.get("testPaperId"))?;
        let pass_ratio = request_text(paper.get("passTheExamThan"))?;
        sqlx::query("INSERT INTO t_theory_knowledge_exam_test_paper (id,name,test_paper_id,total,pass_the_exam_than,exam_id,pass_mark,single_choice_list,multiple_choice_list,judge_list,completion_list,short_answer) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")
            .bind(uuid::Uuid::new_v4().to_string()).bind(name.as_deref()).bind(source_id.as_deref()).bind(request_integer(paper.get("total"))?).bind(pass_ratio.as_deref()).bind(&exam_id).bind(request_integer(paper.get("passMark"))?)
            .bind(&sections[0]).bind(&sections[1]).bind(&sections[2]).bind(&sections[3]).bind(&sections[4]).execute(&mut *transaction).await?;
        sqlx::query("INSERT INTO t_theory_knowledge_exam_user (id,exam_id,user_id,state,score,is_self_testing,start_time) VALUES (?,?,?,2,0,0,?)")
            .bind(uuid::Uuid::new_v4().to_string()).bind(&exam_id).bind(&creator).bind(&start_time).execute(&mut *transaction).await?;
        Ok(exam_json(&sqlx::query("SELECT * FROM t_theory_knowledge_exam WHERE id=?").bind(&exam_id).fetch_one(&mut *transaction).await?))
    }.await;
    complete_exam_transaction(transaction, result).await
}

pub(crate) async fn teacher_start(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let admin = match is_admin(pool, &user.id).await {
        Ok(admin) => admin,
        Err(error) => return exam_response(Err(error)),
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return exam_response(Err(error.into())),
    };
    let mut notification = None;
    let result = async {
        let kind = request_text(body.get("type"))?
            .and_then(|value| value.parse::<i32>().ok())
            .ok_or(ExamError::Parameter("请求参数错误"))?;
        let exam_id = request_text(body.get("examId"))?.unwrap_or_default();
        crate::statistical::lock_user(&mut transaction, &user.id).await?;
        let exam = locked_exam(&mut transaction, &exam_id).await?;
        if !admin {
            if kind != 2 {
                return Err(ExamError::Forbidden);
            }
            locked_candidate(&mut transaction, &exam_id, &user.id).await?;
        }
        if !(2..=4).contains(&kind) {
            return Err(ExamError::Parameter("考试状态必须为2、3或4"));
        }
        let current = exam.get::<Option<i32>, _>("state");
        if current == Some(kind) {
            return Ok(exam_json(&exam));
        }
        let now = exam_time(chrono::Utc::now());
        match kind {
            2 => {
                if current != Some(1) {
                    return Err(ExamError::Terminal("考试已结束，不能重新开始"));
                }
                sqlx::query("UPDATE t_theory_knowledge_exam SET state=2,start_time=? WHERE id=?")
                    .bind(&now).bind(exam_id.as_ref()).execute(&mut *transaction).await?;
            }
            3 => {
                if current != Some(2) {
                    return Err(ExamError::Terminal("考试不在进行中，不能结束"));
                }
                sqlx::query("UPDATE t_theory_knowledge_exam SET state=3,end_time=? WHERE id=?")
                    .bind(&now).bind(exam_id.as_ref()).execute(&mut *transaction).await?;
                let ids = sqlx::query_scalar::<_, String>("SELECT id FROM t_theory_knowledge_exam_user WHERE exam_id=? ORDER BY id")
                    .bind(exam_id.as_ref()).fetch_all(&mut *transaction).await?;
                for id in ids {
                    sqlx::query("UPDATE t_theory_knowledge_exam_user SET state=3,end_time=? WHERE id=? AND state IN (1,2)")
                        .bind(&now).bind(id).execute(&mut *transaction).await?;
                }
            }
            4 => {
                if current != Some(3) {
                    return Err(ExamError::Terminal("考试尚未结束，不能完成阅卷"));
                }
                sqlx::query("UPDATE t_theory_knowledge_exam SET state=4 WHERE id=?")
                    .bind(exam_id.as_ref()).execute(&mut *transaction).await?;
            }
            _ => unreachable!(),
        }
        let data = exam_json(&sqlx::query("SELECT * FROM t_theory_knowledge_exam WHERE id=?")
            .bind(exam_id.as_ref()).fetch_one(&mut *transaction).await?);
        let recipients = sqlx::query_scalar::<_, Option<String>>("SELECT user_id FROM t_theory_knowledge_exam_user WHERE exam_id=?")
            .bind(exam_id.as_ref()).fetch_all(&mut *transaction).await?;
        notification = Some((recipients, json!({"exam": data})));
        Ok(data)
    }.await;
    match result {
        Ok(data) => match transaction.commit().await {
            Ok(()) => {
                if let Some((recipients, map)) = notification {
                    for recipient in recipients.into_iter().flatten() {
                        super::ws::send_classic_map(&recipient, 18001, map.clone()).await;
                    }
                }
                exam_response(Ok(data))
            }
            Err(error) => exam_response(Err(error.into())),
        },
        Err(error) => complete_exam_transaction(transaction, Err(error)).await,
    }
}

pub(crate) async fn student_state(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    save_candidate(state, user, body, false).await
}

pub(crate) async fn realtime(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    save_candidate(state, user, body, true).await
}

async fn save_candidate(state: AppState, user: AuthUser, body: Value, realtime: bool) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let admin = match is_admin(pool, &user.id).await {
        Ok(admin) => admin,
        Err(error) => return exam_response(Err(error)),
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return exam_response(Err(error.into())),
    };
    let mut teacher_notification = None;
    let result = async {
        let kind = if realtime {
            0
        } else {
            request_text(body.get("type"))?
                .and_then(|value| value.parse::<i32>().ok())
                .ok_or(ExamError::Parameter("请求参数错误"))?
        };
        if !realtime && !(1..=3).contains(&kind) {
            return Err(ExamError::Parameter("考生状态必须为1、2或3"));
        }
        let exam_id = request_text(body.get("examId"))?.unwrap_or_default();
        let content = request_text(body.get("content"))?;
        crate::statistical::lock_user(&mut transaction, &user.id).await?;
        let exam = locked_exam(&mut transaction, &exam_id).await?;
        let candidate = locked_candidate(&mut transaction, &exam_id, &user.id).await?;
        require_active_candidate(&exam, &candidate)?;
        let candidate_id = candidate.get::<String, _>("id");
        let mut paper = None;
        if !realtime && kind == 2 {
            let controller = can_control_exam(&mut transaction, &user.id, &exam, admin).await?;
            paper =
                Some(projected_paper(&mut transaction, &exam, Some(&candidate), controller).await?);
            let start_time = candidate.get::<Option<String>, _>("start_time");
            if start_time.as_deref().map_or(true, str::is_empty) {
                sqlx::query("UPDATE t_theory_knowledge_exam_user SET start_time=? WHERE id=?")
                    .bind(exam_time(chrono::Utc::now()))
                    .bind(&candidate_id)
                    .execute(&mut *transaction)
                    .await?;
            }
        } else {
            sqlx::query("UPDATE t_theory_knowledge_exam_user SET content=? WHERE id=?")
                .bind(content.as_deref())
                .bind(&candidate_id)
                .execute(&mut *transaction)
                .await?;
            if !realtime && kind == 3 {
                sqlx::query("UPDATE t_theory_knowledge_exam_user SET end_time=? WHERE id=?")
                    .bind(exam_time(chrono::Utc::now()))
                    .bind(&candidate_id)
                    .execute(&mut *transaction)
                    .await?;
            }
        }
        if !realtime {
            sqlx::query("UPDATE t_theory_knowledge_exam_user SET state=? WHERE id=?")
                .bind(kind)
                .bind(&candidate_id)
                .execute(&mut *transaction)
                .await?;
        }
        let candidate = candidate_json(
            &sqlx::query("SELECT * FROM t_theory_knowledge_exam_user WHERE id=?")
                .bind(&candidate_id)
                .fetch_one(&mut *transaction)
                .await?,
        );
        let teacher = exam
            .get::<Option<String>, _>("teacher")
            .filter(|teacher| !teacher.trim().is_empty());
        if realtime {
            if let Some(teacher) = teacher {
                teacher_notification = Some((teacher, 18003, json!({"student": candidate})));
            }
            return Ok(candidate);
        }
        let mut data = serde_json::Map::new();
        data.insert("exam".to_owned(), exam_json(&exam));
        data.insert("student".to_owned(), candidate);
        if let Some(paper) = paper {
            data.insert("paper".to_owned(), paper);
        }
        let data = Value::Object(data);
        if let Some(teacher) = teacher {
            teacher_notification = Some((teacher, 18002, data.clone()));
        }
        Ok(data)
    }
    .await;
    match result {
        Ok(data) => match transaction.commit().await {
            Ok(()) => {
                if let Some((teacher, code, map)) = teacher_notification {
                    super::ws::send_classic_map(&teacher, code, map).await;
                }
                exam_response(Ok(data))
            }
            Err(error) => exam_response(Err(error.into())),
        },
        Err(error) => complete_exam_transaction(transaction, Err(error)).await,
    }
}
fn normalize_answer(value: &Value) -> Result<String, ()> {
    let text = match value {
        Value::Null => String::new(),
        Value::Array(items) => items
            .iter()
            .map(|item| match item {
                Value::Null => String::new(),
                Value::String(value) => value.clone(),
                Value::Bool(value) => value.to_string(),
                Value::Number(value) => value.to_string(),
                other => other.to_string(),
            })
            .collect::<Vec<_>>()
            .join(","),
        Value::String(value) => value.clone(),
        Value::Bool(value) => value.to_string(),
        Value::Number(value) => value.to_string(),
        other => other.to_string(),
    };
    Ok(text.trim_matches(|c| c <= '\u{0020}').to_string())
}

fn normalize_stored_answer(value: &str) -> String {
    serde_json::from_str::<Value>(value)
        .ok()
        .and_then(|parsed| normalize_answer(&parsed).ok())
        .unwrap_or_else(|| value.trim_matches(|c| c <= '\u{0020}').to_string())
}

fn score_section(snapshot: &str, answers: &mut Value, key: &str) -> Result<i32, ExamError> {
    let questions = if snapshot.trim().is_empty() || snapshot == "null" {
        Vec::new()
    } else {
        serde_json::from_str::<Vec<Value>>(snapshot)?
    };
    let Some(submitted) = answers.get_mut(key).and_then(Value::as_array_mut) else {
        return Ok(0);
    };
    let mut score = 0_i32;
    for question in &questions {
        let id = request_text(question.get("id"))?;
        let answer = submitted.iter_mut().find(|item| {
            item.is_object()
                && item.get("id").is_some_and(|value| {
                    value.is_string() || value.is_number() || value.is_boolean()
                })
                && request_text(item.get("id")).ok().flatten().as_deref() == id.as_deref()
        });
        if let Some(item) = answer {
            let expected = request_text(question.get("answer"))?;
            let actual = normalize_answer(item.get("answer").unwrap_or(&Value::Null))
                .expect("answer normalization is infallible");
            let correct = actual == normalize_stored_answer(expected.as_deref().unwrap_or(""));
            let points = request_integer(question.get("score"))
                .map_err(|_| {
                    ExamError::Database(sqlx::Error::Protocol(
                        "invalid stored question score".to_owned(),
                    ))
                })?
                .unwrap_or(0);
            let awarded = if correct { points } else { 0 };
            item["teacherScore"] = json!(awarded);
            score = score.wrapping_add(awarded);
        }
    }
    Ok(score)
}

fn parse_answer_content(content: Option<&str>) -> Result<Option<Value>, ExamError> {
    let Some(content) = content.filter(|content| !content.trim().is_empty()) else {
        return Ok(None);
    };
    let value: Value =
        serde_json::from_str(content).map_err(|_| ExamError::Parameter("作答内容不是合法 JSON"))?;
    Ok(value.is_object().then_some(value))
}

fn same_answer_content(current: Option<&str>, previous: Option<&str>) -> bool {
    if current == previous {
        return true;
    }
    // Java-owned snapshots retain Gson property order; JSON key order is not an answer change.
    match (
        current.and_then(|value| serde_json::from_str::<Value>(value).ok()),
        previous.and_then(|value| serde_json::from_str::<Value>(value).ok()),
    ) {
        (Some(current), Some(previous)) => current == previous,
        _ => false,
    }
}

pub(crate) async fn finish_self_test(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return exam_response(Err(error.into())),
    };
    let result = async {
        let exam_id = request_text(body.get("examId"))?.unwrap_or_default();
        let content = request_text(body.get("content"))?;
        crate::statistical::lock_user(&mut transaction, &user.id).await?;
        let exam = locked_exam(&mut transaction, &exam_id).await?;
        let candidate = locked_candidate(&mut transaction, &exam_id, &user.id).await?;
        if candidate.get::<Option<i32>, _>("is_self_testing") != Some(0) { return Err(ExamError::Forbidden); }
        let paper = sqlx::query("SELECT * FROM t_theory_knowledge_exam_test_paper WHERE exam_id=? LIMIT 1")
            .bind(exam_id.as_ref()).fetch_optional(&mut *transaction).await?
            .ok_or(ExamError::Parameter("未查询到试卷快照，无法结算自测"))?;
        let mut answers = parse_answer_content(content.as_deref())?;
        let mut score = 0_i32;
        if let Some(answers) = answers.as_mut() {
            for (column, section) in PAPER_SECTIONS {
                let snapshot = paper.get::<Option<String>, _>(column);
                score = score.wrapping_add(score_section(snapshot.as_deref().unwrap_or(""), answers, section)?);
            }
        }
        let content = answers.as_ref().map(gson_json).map(Cow::Owned).or(content);
        if exam.get::<Option<i32>, _>("state") == Some(4) && candidate.get::<Option<i32>, _>("state") == Some(4)
            && same_answer_content(content.as_deref(), candidate.get::<Option<String>, _>("content").as_deref()) {
            return Ok(exam_json(&exam));
        }
        require_active_candidate(&exam, &candidate)?;
        let end_time = exam_time(chrono::Utc::now());
        sqlx::query("UPDATE t_theory_knowledge_exam_user SET content=?,score=?,state=4,end_time=? WHERE id=?")
            .bind(content.as_deref()).bind(score).bind(&end_time).bind(candidate.get::<String, _>("id")).execute(&mut *transaction).await?;
        sqlx::query("UPDATE t_theory_knowledge_exam SET state=4,end_time=? WHERE id=?")
            .bind(&end_time).bind(exam_id.as_ref()).execute(&mut *transaction).await?;
        Ok(exam_json(&sqlx::query("SELECT * FROM t_theory_knowledge_exam WHERE id=?").bind(exam_id.as_ref()).fetch_one(&mut *transaction).await?))
    }.await;
    complete_exam_transaction(transaction, result).await
}

pub(crate) async fn teacher_upload_score(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return exam_response(Err(error.into())),
    };
    let result = async {
        crate::statistical::lock_user(&mut transaction, &user.id).await?;
        let exam_id = request_text(body.get("examId"))?.unwrap_or_default();
        locked_exam(&mut transaction, &exam_id).await?;
        let list = body
            .get("list")
            .and_then(Value::as_array)
            .filter(|items| !items.is_empty())
            .ok_or(ExamError::Parameter("上分列表不能为空"))?;
        let mut targets = Vec::with_capacity(list.len());
        for item in list {
            let uid = request_text(item.get("user_id"))?
                .filter(|uid| !uid.trim().is_empty())
                .ok_or(ExamError::Parameter(
                    "上分列表中存在缺少 user_id 的条目，整批已拒绝",
                ))?;
            let candidate = match locked_candidate(&mut transaction, &exam_id, &uid).await {
                Err(ExamError::Forbidden) => {
                    return Err(ExamError::Parameter(
                        "考生不是本场考试的考生，整批上分已拒绝",
                    ))
                }
                other => other?,
            };
            targets.push(candidate.get::<String, _>("id"));
        }
        for (item, id) in list.iter().zip(targets) {
            let score = request_integer(item.get("score"))?.unwrap_or(0);
            let content = item
                .get("content")
                .filter(|content| !content.is_null())
                .map(gson_json)
                .unwrap_or_else(|| "{}".to_owned());
            sqlx::query(
                "UPDATE t_theory_knowledge_exam_user SET score=?,state=4,content=? WHERE id=?",
            )
            .bind(score)
            .bind(content)
            .bind(id)
            .execute(&mut *transaction)
            .await?;
        }
        Ok(Value::Null)
    }
    .await;
    complete_exam_transaction(transaction, result).await
}
pub(crate) async fn self_list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match self_list_data(pool, &user.id).await {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

async fn self_list_data(pool: &sqlx::MySqlPool, user_id: &str) -> Result<Value, sqlx::Error> {
    let rows = sqlx::query(
        "SELECT e.title,u.start_time,u.score,u.content,u.exam_id \
         FROM t_theory_knowledge_exam_user u \
         LEFT JOIN t_theory_knowledge_exam e ON e.id=u.exam_id \
         WHERE u.user_id=? AND u.is_self_testing=0 ORDER BY u.start_time DESC",
    )
    .bind(user_id)
    .fetch_all(pool)
    .await?;
    Ok(Value::Array(
        rows.iter()
            .map(|row| {
                json!({
                    "title": row.get::<Option<String>, _>("title"),
                    "start_time": row.get::<Option<String>, _>("start_time"),
                    "score": row.get::<Option<i32>, _>("score"),
                    "content": row.get::<Option<String>, _>("content"),
                    "examId": row.get::<Option<String>, _>("exam_id"),
                })
            })
            .collect(),
    ))
}

pub(crate) async fn user_list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let kind = body.get("type").and_then(Value::as_bool);
    let exam_state = body.get("state").and_then(Value::as_bool);
    if kind != Some(true) && exam_state.is_none() {
        return envelope_response(StatusCode::OK, 202, "state 不能为空", "state 不能为空");
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut exam = Value::Null;
    if kind != Some(true) {
        // Match Java's named-query precedence: state 1 does not filter self-testing.
        let rows = sqlx::query(
            "SELECT e.id,e.title,e.start_time,e.end_time,e.duration,e.create_user_id,\
             e.create_time,e.teacher,e.state,u.state AS user_state,t.user_name AS teacher_name \
             FROM t_theory_knowledge_exam_user u \
             LEFT JOIN t_theory_knowledge_exam e ON e.id=u.exam_id \
             LEFT JOIN t_user t ON t.id=e.teacher \
             WHERE u.user_id=? AND ((? AND (e.state=1 OR (e.state=2 AND u.is_self_testing=1))) \
             OR (NOT ? AND e.state=4 AND u.is_self_testing=1)) ORDER BY e.start_time DESC",
        )
        .bind(&user.id)
        .bind(exam_state == Some(true))
        .bind(exam_state == Some(true))
        .fetch_all(pool)
        .await;
        let rows = match rows {
            Ok(rows) => rows,
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        };
        exam = Value::Array(
            rows.iter()
                .map(|row| {
                    json!({
                        "userState": row.get::<Option<i32>, _>("user_state"),
                        "teacherName": row.get::<Option<String>, _>("teacher_name"),
                        "id": row.get::<String, _>("id"),
                        "title": row.get::<Option<String>, _>("title"),
                        "start_time": row.get::<Option<String>, _>("start_time"),
                        "end_time": row.get::<Option<String>, _>("end_time"),
                        "duration": row.get::<Option<String>, _>("duration"),
                        "create_user_id": row.get::<Option<String>, _>("create_user_id"),
                        "create_time": row.get::<Option<String>, _>("create_time"),
                        "teacher": row.get::<Option<String>, _>("teacher"),
                        "state": row.get::<Option<i32>, _>("state"),
                    })
                })
                .collect(),
        );
    }
    let exam_self = if kind != Some(false) {
        match self_list_data(pool, &user.id).await {
            Ok(data) => data,
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        }
    } else {
        Value::Null
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"exam": exam, "examSelf": exam_self})),
        "ok",
        "",
    )
}

pub(crate) async fn find_exam_user(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let admin = match is_admin(pool, &actor.id).await {
        Ok(admin) => admin,
        Err(error) => return exam_response(Err(error)),
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return exam_response(Err(error.into())),
    };
    let result = async {
        let exam_id = request_text(body.get("examId"))?.unwrap_or_default();
        let target = request_text(body.get("userId"))?;
        crate::statistical::lock_user(&mut transaction, &actor.id).await?;
        let exam = locked_exam(&mut transaction, &exam_id).await?;
        let allowed = target.as_deref() == Some(actor.id.as_str())
            || exam.get::<Option<String>, _>("create_user_id").as_deref()
                == Some(actor.id.as_str())
            || exam.get::<Option<String>, _>("teacher").as_deref() == Some(actor.id.as_str())
            || admin;
        if !allowed {
            return Err(ExamError::Forbidden);
        }
        let candidate = sqlx::query(
            "SELECT * FROM t_theory_knowledge_exam_user WHERE exam_id=? AND user_id=? LIMIT 1",
        )
        .bind(exam_id.as_ref())
        .bind(target.as_deref())
        .fetch_optional(&mut *transaction)
        .await?;
        Ok(candidate
            .as_ref()
            .map(candidate_json)
            .unwrap_or(Value::Null))
    }
    .await;
    complete_exam_transaction(transaction, result).await
}

pub(crate) async fn save_formal(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    // Resolve supplied IDs before BEGIN: a pool read while holding the transaction
    // self-deadlocks at size one; an RR read before the parent lock pins stale answers.
    let preflight: Result<_, ExamError> = async {
        let students = body
            .get("stuId")
            .and_then(Value::as_array)
            .ok_or(ExamError::Parameter("请求参数错误"))?;
        let supplied_id = request_text(body.get("id"))?.filter(|id| !id.is_empty());
        let rebuilding = supplied_id.is_some();
        let exam_id = if let Some(id) = supplied_id {
            sqlx::query_scalar::<_, String>("SELECT id FROM t_theory_knowledge_exam WHERE id=?")
                .bind(id.as_ref())
                .fetch_optional(pool)
                .await?
                .ok_or(ExamError::Parameter("未查询到考试"))?
        } else {
            uuid::Uuid::new_v4().to_string()
        };
        Ok((students, rebuilding, exam_id))
    }
    .await;
    let (students, rebuilding, exam_id) = match preflight {
        Ok(preflight) => preflight,
        Err(error) => return exam_response(Err(error)),
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return exam_response(Err(error.into())),
    };
    let result = async {
        // All reads after BEGIN use this transaction, with actor -> parent PK locks.
        crate::statistical::lock_user(&mut transaction, &user.id).await?;
        if rebuilding {
            locked_exam(&mut transaction, &exam_id).await?;
        } else {
            sqlx::query("INSERT INTO t_theory_knowledge_exam (id,title,start_time,duration,create_user_id,create_time,teacher,state) VALUES (?,?,?,?,?,?,?,1)")
                .bind(&exam_id).bind(request_text(body.get("title"))?.as_deref()).bind(request_text(body.get("startTime"))?.as_deref()).bind(request_text(body.get("duration"))?.as_deref()).bind(&user.id).bind(crate::platform::clock::now_time_millis().to_string()).bind(request_text(body.get("teacher"))?.as_deref()).execute(&mut *transaction).await?;
        }
        let touched = sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM t_theory_knowledge_exam_user WHERE exam_id=? AND (state<>1 OR score>0 OR (content IS NOT NULL AND TRIM(content)<>''))")
            .bind(&exam_id).fetch_one(&mut *transaction).await?;
        if touched > 0 {
            return Err(ExamError::Parameter("考试已有作答记录，禁止编辑重建考生名单"));
        }
        let paper = body.get("testPaper").unwrap_or(&Value::Null);
        let sections = PAPER_SECTIONS.iter().map(|(_, section)| {
            question_list_json(paper.get(*section).and_then(Value::as_array).map(Vec::as_slice).unwrap_or(&[]))
        }).collect::<Result<Vec<_>, _>>()?;
        if rebuilding {
            sqlx::query("UPDATE t_theory_knowledge_exam SET title=?,start_time=?,duration=?,create_user_id=?,teacher=?,state=1 WHERE id=?")
                .bind(request_text(body.get("title"))?.as_deref()).bind(request_text(body.get("startTime"))?.as_deref()).bind(request_text(body.get("duration"))?.as_deref()).bind(&user.id).bind(request_text(body.get("teacher"))?.as_deref()).bind(&exam_id).execute(&mut *transaction).await?;
            delete_exam_children(&mut transaction, &exam_id).await?;
        }
        sqlx::query("INSERT INTO t_theory_knowledge_exam_test_paper (id,name,test_paper_id,total,pass_the_exam_than,exam_id,pass_mark,single_choice_list,multiple_choice_list,judge_list,completion_list,short_answer) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")
            .bind(uuid::Uuid::new_v4().to_string()).bind(request_text(paper.get("name"))?.as_deref()).bind(request_text(paper.get("testPaperId"))?.as_deref()).bind(request_integer(paper.get("total"))?).bind(request_text(paper.get("passTheExamThan"))?.as_deref()).bind(&exam_id).bind(request_integer(paper.get("passMark"))?)
            .bind(&sections[0]).bind(&sections[1]).bind(&sections[2]).bind(&sections[3]).bind(&sections[4]).execute(&mut *transaction).await?;
        for student in students.iter().filter_map(Value::as_str) {
            sqlx::query("INSERT INTO t_theory_knowledge_exam_user (id,exam_id,user_id,state,score,is_self_testing) VALUES (?,?,?,1,0,1)")
                .bind(uuid::Uuid::new_v4().to_string()).bind(&exam_id).bind(student).execute(&mut *transaction).await?;
        }
        Ok(Value::Null)
    }.await;
    complete_exam_transaction(transaction, result).await
}

async fn delete_exam_children(
    transaction: &mut Transaction<'_, MySql>,
    exam_id: &str,
) -> Result<(), ExamError> {
    for table in [
        "t_theory_knowledge_exam_test_paper",
        "t_theory_knowledge_exam_user",
    ] {
        let ids = sqlx::query_scalar::<_, String>(&format!(
            "SELECT id FROM {table} WHERE exam_id=? ORDER BY id"
        ))
        .bind(exam_id)
        .fetch_all(&mut **transaction)
        .await?;
        for id in ids {
            sqlx::query(&format!("DELETE FROM {table} WHERE id=?"))
                .bind(id)
                .execute(&mut **transaction)
                .await?;
        }
    }
    Ok(())
}

pub(crate) async fn delete_exam(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return exam_response(Err(error.into())),
    };
    let result = async {
        let id = request_text(body.get("examId").or_else(|| body.get("id")))?.unwrap_or_default();
        crate::statistical::lock_user(&mut transaction, &user.id).await?;
        sqlx::query("SELECT id FROM t_theory_knowledge_exam WHERE id=? FOR UPDATE")
            .bind(id.as_ref())
            .fetch_optional(&mut *transaction)
            .await?;
        delete_exam_children(&mut transaction, &id).await?;
        sqlx::query("DELETE FROM t_theory_knowledge_exam WHERE id=?")
            .bind(id.as_ref())
            .execute(&mut *transaction)
            .await?;
        Ok(Value::Null)
    }
    .await;
    complete_exam_transaction(transaction, result).await
}

pub(crate) async fn analyse(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(exam_id) = body.get("examId").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let exam =
        match sqlx::query("SELECT create_user_id,teacher FROM t_theory_knowledge_exam WHERE id=?")
            .bind(exam_id)
            .fetch_optional(pool)
            .await
        {
            Ok(value) => value,
            Err(error) => {
                tracing::warn!(error=%error, "exam analysis exam query failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
    let Some(exam) = exam else {
        return envelope_response(StatusCode::OK, 202, "未查询到考试", "未查询到考试");
    };
    let admin = match sqlx::query_scalar::<_,i64>("SELECT COUNT(*) FROM t_user_role ur JOIN t_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.is_admin=0").bind(&actor.id).fetch_one(pool).await {
        Ok(value) => value > 0, Err(error) => { tracing::warn!(error=%error, "exam analysis authorization query failed"); return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误",""); }
    };
    if !admin
        && exam.get::<Option<String>, _>("create_user_id").as_deref() != Some(actor.id.as_str())
        && exam.get::<Option<String>, _>("teacher").as_deref() != Some(actor.id.as_str())
    {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let paper = match sqlx::query("SELECT total,pass_mark,single_choice_list,multiple_choice_list,judge_list,completion_list,short_answer FROM t_theory_knowledge_exam_test_paper WHERE exam_id=?").bind(exam_id).fetch_optional(pool).await {
        Ok(value) => value, Err(error) => { tracing::warn!(error=%error, "exam analysis snapshot query failed"); return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误",""); }
    };
    let Some(paper) = paper else {
        return envelope_response(
            StatusCode::OK,
            202,
            "未查询到试卷快照，无法进行考核分析",
            "",
        );
    };
    let (Some(pass_mark), Some(total)) = (
        paper.get::<Option<i32>, _>("pass_mark"),
        paper.get::<Option<i32>, _>("total"),
    ) else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let good_boundary =
        ((i64::from(total) - i64::from(pass_mark)) / 2 + i64::from(pass_mark)) as i32;
    let mut questions = Vec::new();
    for column in [
        "single_choice_list",
        "multiple_choice_list",
        "judge_list",
        "completion_list",
        "short_answer",
    ] {
        let Some(snapshot) = paper.get::<Option<String>, _>(column) else {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        };
        match serde_json::from_str::<Vec<Value>>(&snapshot) {
            Ok(items) => questions.extend(items),
            Err(error) => {
                tracing::warn!(error=%error,column,"exam analysis snapshot JSON invalid");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        }
    }
    let rows = match sqlx::query("SELECT e.user_id,e.score,e.content,e.end_time,u.user_account FROM t_theory_knowledge_exam_user e LEFT JOIN t_user u ON u.id=e.user_id WHERE e.exam_id=?").bind(exam_id).fetch_all(pool).await {
        Ok(rows) => rows, Err(error) => { tracing::warn!(error=%error,"exam analysis candidate query failed"); return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误",""); }
    };
    let (mut failing, mut ordinary, mut good) = (0, 0, 0);
    let mut scores = Vec::with_capacity(rows.len());
    let mut errors = std::collections::HashMap::<String, (i32, Value)>::new();
    for row in rows {
        let Some(user_id) = row.get::<Option<String>, _>("user_id") else {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        };
        let Some(current) = row.get::<Option<i32>, _>("score") else {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        };
        let previous = if let Some(end_time) = row.get::<Option<String>, _>("end_time") {
            match sqlx::query_scalar::<_,Option<i32>>("SELECT score FROM t_theory_knowledge_exam_user WHERE user_id=? AND end_time < ? ORDER BY end_time DESC LIMIT 1").bind(&user_id).bind(end_time).fetch_optional(pool).await { Ok(v)=>v.flatten().unwrap_or(0), Err(error)=>{ tracing::warn!(error=%error,"exam analysis previous score query failed"); return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误",""); } }
        } else {
            0
        };
        scores.push(json!({"userName":row.get::<Option<String>,_>("user_account"),"currentScore":current,"previousScore":previous}));
        if current < pass_mark {
            failing += 1;
        } else if current < good_boundary {
            ordinary += 1;
        } else {
            good += 1;
        }
        let content = row.get::<Option<String>, _>("content").unwrap_or_default();
        let submitted = if content.trim().is_empty() {
            Value::Object(serde_json::Map::new())
        } else {
            match serde_json::from_str::<Value>(&content) {
                Ok(Value::Object(v)) => Value::Object(v),
                _ => {
                    return envelope_response(
                        StatusCode::INTERNAL_SERVER_ERROR,
                        500,
                        "服务器错误",
                        "",
                    )
                }
            }
        };
        for question in &questions {
            let (Some(id), Some(expected), Some(kind)) = (
                question.get("id").and_then(Value::as_str),
                question.get("answer").and_then(Value::as_str),
                question.get("type").and_then(Value::as_i64),
            ) else {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            };
            let Some(section) = [
                "singleChoice",
                "multipleChoice",
                "judge",
                "completion",
                "shortAnswer",
            ]
            .get((kind - 1) as usize) else {
                continue;
            };
            let correct = submitted
                .get(*section)
                .and_then(Value::as_array)
                .and_then(|items| {
                    items
                        .iter()
                        .find(|item| item.get("id").and_then(Value::as_str) == Some(id))
                })
                .map(|item| {
                    item.get("teacherScore")
                        .and_then(Value::as_f64)
                        .map(|v| v > 0.0)
                        .unwrap_or(false)
                        || item
                            .get("answer")
                            .map(|v| {
                                normalize_answer(v).unwrap_or_default()
                                    == normalize_stored_answer(expected)
                            })
                            .unwrap_or(false)
                })
                .unwrap_or(false);
            if !correct {
                let entry = errors.entry(id.to_string()).or_insert((
                    0,
                    json!({"id":id,"topic":question.get("topic"),"type":kind,"number":0}),
                ));
                entry.0 += 1;
                entry.1["number"] = json!(entry.0);
            }
        }
    }
    let mut error_top3 = errors.into_values().map(|(_, v)| v).collect::<Vec<_>>();
    error_top3.sort_by(|a, b| {
        b.get("number")
            .and_then(Value::as_i64)
            .cmp(&a.get("number").and_then(Value::as_i64))
    });
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"scoreList":scores,"errorTop3":error_top3,"failing":failing,"ordinary":ordinary,"good":good}),
        ),
        "ok",
        "",
    )
}

#[cfg(test)]
#[path = "exam_writer_tests.rs"]
mod writer_tests;

#[cfg(test)]
mod self_test_tests {
    use super::*;

    #[test]
    fn numeric_exam_ids_are_looked_up_instead_of_becoming_new_exams() {
        assert_eq!(
            request_text(Some(&json!(987654))).unwrap().as_deref(),
            Some("987654")
        );
        assert!(request_text(Some(&json!({"id": "other"}))).is_err());
    }

    #[test]
    fn exam_timestamps_match_java_utc_plus_eight_for_countdown() {
        let instant = chrono::DateTime::from_timestamp(0, 0).unwrap();
        assert_eq!(exam_time(instant), "1970-01-01 08:00:00");
    }

    #[test]
    fn active_paper_hides_answers_and_preserves_completion_slots() {
        let source = r#"[{"id":"q","topic":"甲________乙(___)丙$_$","answer":"[\"甲\",\"乙\",\"丙\"]","analysis":"解析","score":5}]"#;
        let projected: Value =
            serde_json::from_str(&project_questions(source, "completion").unwrap()).unwrap();
        assert_eq!(projected[0]["answer"], r#"["","",""]"#);
        assert!(projected[0].get("analysis").is_none());
        assert_eq!(completion_slots("________________"), 2);
        assert_eq!(projected[0]["score"], 5);
    }

    #[test]
    fn snapshot_integer_coercion_cannot_silently_zero_or_wrap_scores() {
        let source = json!([{"id":"q", "score":10.0, "sort":"0", "type":1}]);
        let snapshot = question_list_json(source.as_array().unwrap()).unwrap();
        let mut answers = json!({"singleChoice":[{"id":"q", "answer":null}]});
        assert_eq!(
            score_section(&snapshot, &mut answers, "singleChoice").unwrap(),
            10
        );
        assert!(question_list_json(&[json!({"score":2147483648_i64})]).is_err());
        assert!(score_section(
            r#"[{"id":"q","score":2147483648}]"#,
            &mut answers,
            "singleChoice"
        )
        .is_err());
    }

    #[test]
    fn short_answer_scoring_keeps_java_unicode_boundary_whitespace() {
        for (boundary, expected) in [(" \t\r\n\u{001f}", 10), ("\u{3000}", 0), ("\u{00a0}", 0)] {
            let padded = format!("{boundary}标准步骤{boundary}");
            let snapshot = json!([{"id":"q","answer":"标准步骤","score":10}]).to_string();
            let mut answers = json!({"shortAnswer":[{"id":"q","answer":padded}]});
            assert_eq!(
                score_section(&snapshot, &mut answers, "shortAnswer").unwrap(),
                expected
            );
            assert_eq!(answers["shortAnswer"][0]["teacherScore"], expected);
            for stored in [padded.clone(), json!(padded).to_string()] {
                let snapshot = json!([{"id":"q","answer":stored,"score":10}]).to_string();
                let mut answers = json!({"shortAnswer":[{"id":"q","answer":"标准步骤"}]});
                assert_eq!(
                    score_section(&snapshot, &mut answers, "shortAnswer").unwrap(),
                    expected
                );
            }
        }
    }

    #[test]
    fn completion_scoring_trims_csv_boundaries_not_unicode_or_each_blank() {
        for (boundary, expected) in [(" \t\r\n\u{001f}", 10), ("\u{3000}", 0), ("\u{00a0}", 0)] {
            let padded = json!([format!("{boundary}甲"), format!("乙{boundary}")]);
            let snapshot = json!([{"id":"q","answer":"[\"甲\",\"乙\"]","score":10}]).to_string();
            let mut answers = json!({"completion":[{"id":"q","answer":padded}]});
            assert_eq!(
                score_section(&snapshot, &mut answers, "completion").unwrap(),
                expected
            );
            assert_eq!(answers["completion"][0]["teacherScore"], expected);
            for stored in [format!("{boundary}甲,乙{boundary}"), padded.to_string()] {
                let snapshot = json!([{"id":"q","answer":stored,"score":10}]).to_string();
                let mut answers = json!({"completion":[{"id":"q","answer":["甲","乙"]}]});
                assert_eq!(
                    score_section(&snapshot, &mut answers, "completion").unwrap(),
                    expected
                );
            }
        }
        assert_eq!(normalize_answer(&json!(["甲 ", " 乙"])).unwrap(), "甲 , 乙");
    }

    #[test]
    fn settlement_uses_first_answer_and_overwrites_client_awards() {
        let snapshot = r#"[{"id":"q","answer":"[\"A\",\"B\"]","score":10}]"#;
        let mut answers = json!({"multipleChoice":[{"id":"q","answer":["A","B"],"teacherScore":999},{"id":"q","answer":["X"],"teacherScore":999}]});
        assert_eq!(
            score_section(snapshot, &mut answers, "multipleChoice").unwrap(),
            10
        );
        assert_eq!(answers["multipleChoice"][0]["teacherScore"], 10);
        let mut wrong = json!({"multipleChoice":[{"id":"q","answer":["X"],"teacherScore":999},{"id":"q","answer":["A","B"]}]});
        assert_eq!(
            score_section(snapshot, &mut wrong, "multipleChoice").unwrap(),
            0
        );
        assert_eq!(wrong["multipleChoice"][0]["teacherScore"], 0);
    }

    #[test]
    fn blank_non_object_and_non_array_answers_match_zero_score_behavior() {
        assert!(parse_answer_content(Some("  ")).unwrap().is_none());
        assert!(parse_answer_content(Some("[]")).unwrap().is_none());
        assert!(parse_answer_content(Some("not json")).is_err());
        let mut answers = json!({"singleChoice": [null, 1, {"id":"unrelated"}], "judge": null});
        let snapshot = r#"[{"id":"q","answer":"\"A\"","score":5}]"#;
        assert_eq!(
            score_section(snapshot, &mut answers, "singleChoice").unwrap(),
            0
        );
        assert_eq!(score_section(snapshot, &mut answers, "judge").unwrap(), 0);
        assert_eq!(normalize_answer(&json!(["", "b"])).unwrap(), ",b");
    }

    #[test]
    fn retry_accepts_java_json_property_order_but_not_answer_changes() {
        let java = r#"{"singleChoice":[{"id":"q","answer":"A","teacherScore":5}]}"#;
        let rust = r#"{"singleChoice":[{"teacherScore":5,"answer":"A","id":"q"}]}"#;
        assert!(same_answer_content(Some(java), Some(rust)));
        assert!(!same_answer_content(
            Some(java),
            Some(r#"{"singleChoice":[{"id":"q","answer":"B","teacherScore":0}]}"#)
        ));
        assert!(!same_answer_content(None, Some("")));
    }
}
