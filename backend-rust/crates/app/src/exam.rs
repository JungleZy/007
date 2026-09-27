use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;

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
    let rows=match sqlx::query("SELECT DISTINCT e.id,e.title,e.start_time,e.end_time,e.duration,e.create_user_id,e.create_time,e.teacher,e.state FROM t_theory_knowledge_exam e JOIN t_theory_knowledge_exam_user u ON u.exam_id=e.id WHERE u.is_self_testing=1").fetch_all(pool).await{Ok(r)=>r,Err(_)=>return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")};
    let admin=sqlx::query_scalar::<_,i64>("SELECT COUNT(*) FROM t_user_role ur JOIN t_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.is_admin=0").bind(&user.id).fetch_one(pool).await.unwrap_or(0)>0;
    let data=rows.into_iter().filter(|row|states.contains(&row.get::<Option<i32>,_>("state").unwrap_or_default())&&(admin||row.get::<Option<String>,_>("create_user_id").as_deref()==Some(user.id.as_str())||row.get::<Option<String>,_>("teacher").as_deref()==Some(user.id.as_str()))).map(|row|json!({"id":row.get::<String,_>("id"),"title":row.get::<Option<String>,_>("title"),"startTime":row.get::<Option<String>,_>("start_time"),"endTime":row.get::<Option<String>,_>("end_time"),"duration":row.get::<Option<String>,_>("duration"),"createUserId":row.get::<Option<String>,_>("create_user_id"),"createTime":row.get::<Option<String>,_>("create_time"),"teacher":row.get::<Option<String>,_>("teacher"),"state":row.get::<Option<i32>,_>("state")})).collect::<Vec<_>>();
    envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", "")
}

pub(crate) async fn detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|v| !v.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let exam=match sqlx::query("SELECT id,title,start_time,end_time,duration,create_user_id,create_time,teacher,state FROM t_theory_knowledge_exam WHERE id=? LIMIT 1").bind(id).fetch_optional(pool).await{Ok(Some(r))=>r,Ok(None)=>return envelope_response(StatusCode::OK,500,"未查询到考试",""),Err(_)=>return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")};
    let candidate=sqlx::query("SELECT id,user_id,state,score,content,is_self_testing FROM t_theory_knowledge_exam_user WHERE exam_id=? AND user_id=? LIMIT 1").bind(id).bind(&user.id).fetch_optional(pool).await.ok().flatten();
    let admin=sqlx::query_scalar::<_,i64>("SELECT COUNT(*) FROM t_user_role ur JOIN t_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.is_admin=0").bind(&user.id).fetch_one(pool).await.unwrap_or(0)>0;
    let controller = admin
        || exam.get::<Option<String>, _>("create_user_id").as_deref() == Some(user.id.as_str())
        || exam.get::<Option<String>, _>("teacher").as_deref() == Some(user.id.as_str());
    if !controller && candidate.is_none() {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let users = if controller {
        sqlx::query(
            "SELECT user_id,state,score,content FROM t_theory_knowledge_exam_user WHERE exam_id=?",
        )
        .bind(id)
        .fetch_all(pool)
        .await
        .unwrap_or_default()
    } else {
        Vec::new()
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"exam":{"id":exam.get::<String,_>("id"),"title":exam.get::<Option<String>,_>("title"),"startTime":exam.get::<Option<String>,_>("start_time"),"endTime":exam.get::<Option<String>,_>("end_time"),"duration":exam.get::<Option<String>,_>("duration"),"createUserId":exam.get::<Option<String>,_>("create_user_id"),"teacher":exam.get::<Option<String>,_>("teacher"),"state":exam.get::<Option<i32>,_>("state")},"user":users.iter().map(|row|json!({"userId":row.get::<Option<String>,_>("user_id"),"state":row.get::<Option<i32>,_>("state"),"score":row.get::<Option<i32>,_>("score"),"content":row.get::<Option<String>,_>("content")})).collect::<Vec<_>>(),"paper":Value::Null}),
        ),
        "ok",
        "",
    )
}

pub(crate) async fn self_test(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let exam_id = uuid::Uuid::new_v4().to_string();
    let snap_id = uuid::Uuid::new_v4().to_string();
    let now = crate::now_time_millis().to_string();
    let paper = body.get("testPaper").cloned().unwrap_or_else(|| json!({}));
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if sqlx::query("INSERT INTO t_theory_knowledge_exam (id,title,start_time,duration,create_user_id,create_time,state) VALUES (?,?,?,?,?,?,2)").bind(&exam_id).bind(body.get("title").and_then(Value::as_str)).bind(&now).bind(body.get("duration").and_then(Value::as_str)).bind(&user.id).bind(&now).execute(&mut *tx).await.is_err(){let _=tx.rollback().await;return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");}
    if sqlx::query("INSERT INTO t_theory_knowledge_exam_test_paper (id,name,test_paper_id,total,pass_the_exam_than,exam_id,pass_mark,single_choice_list,multiple_choice_list,judge_list,completion_list,short_answer) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)").bind(&snap_id).bind(paper.get("name").and_then(Value::as_str)).bind(paper.get("id").and_then(Value::as_str)).bind(paper.get("total").and_then(Value::as_i64)).bind(paper.get("passTheExamThan").and_then(Value::as_str)).bind(&exam_id).bind(paper.get("passMark").and_then(Value::as_i64)).bind(paper.get("singleChoice").unwrap_or(&Value::Array(Vec::new())).to_string()).bind(paper.get("multipleChoice").unwrap_or(&Value::Array(Vec::new())).to_string()).bind(paper.get("judge").unwrap_or(&Value::Array(Vec::new())).to_string()).bind(paper.get("completion").unwrap_or(&Value::Array(Vec::new())).to_string()).bind(paper.get("shortAnswer").unwrap_or(&Value::Array(Vec::new())).to_string()).execute(&mut *tx).await.is_err(){let _=tx.rollback().await;return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");}
    if sqlx::query("INSERT INTO t_theory_knowledge_exam_user (id,exam_id,user_id,state,score,is_self_testing,start_time) VALUES (?,?,?,2,0,0,?)").bind(uuid::Uuid::new_v4().to_string()).bind(&exam_id).bind(&user.id).bind(&now).execute(&mut *tx).await.is_err(){let _=tx.rollback().await;return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");}
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, Some(json!({"id":exam_id})), "ok", "")
}

pub(crate) async fn teacher_start(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(exam_id) = body.get("examId").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(kind) = body.get("type").and_then(|value| {
        value
            .as_i64()
            .or_else(|| value.as_str().and_then(|value| value.parse().ok()))
    }) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let admin=sqlx::query_scalar::<_,i64>("SELECT COUNT(*) FROM t_user_role ur JOIN t_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.is_admin=0").bind(&user.id).fetch_one(pool).await.unwrap_or(0)>0;
    if !admin {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let current = sqlx::query_scalar::<_, Option<i32>>(
        "SELECT state FROM t_theory_knowledge_exam WHERE id=?",
    )
    .bind(exam_id)
    .fetch_optional(pool)
    .await
    .ok()
    .flatten()
    .flatten();
    let Some(current) = current else {
        return envelope_response(StatusCode::OK, 500, "未查询到考试", "");
    };
    if (kind == 2 && current != 1) || (kind == 3 && current != 2) || (kind == 4 && current != 3) {
        return envelope_response(StatusCode::OK, 208, "操作已不可执行", "");
    }
    let mut tx = pool.begin().await.unwrap();
    if sqlx::query("UPDATE t_theory_knowledge_exam SET state=? WHERE id=?")
        .bind(kind)
        .bind(exam_id)
        .execute(&mut *tx)
        .await
        .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if kind == 3 {
        let _ = sqlx::query(
            "UPDATE t_theory_knowledge_exam_user SET state=3 WHERE exam_id=? AND state IN (1,2)",
        )
        .bind(exam_id)
        .execute(&mut *tx)
        .await;
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"id":exam_id,"state":kind})),
        "ok",
        "",
    )
}

pub(crate) async fn student_state(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(exam_id) = body.get("examId").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(kind) = body.get("type").and_then(|value| {
        value
            .as_i64()
            .or_else(|| value.as_str().and_then(|value| value.parse().ok()))
    }) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let row=sqlx::query("SELECT e.state,s.state AS student_state,s.end_time FROM t_theory_knowledge_exam e LEFT JOIN t_theory_knowledge_exam_user s ON s.exam_id=e.id AND s.user_id=? WHERE e.id=?").bind(&user.id).bind(exam_id).fetch_optional(pool).await.ok().flatten();
    let Some(row) = row else {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    };
    if row.get::<Option<i32>, _>("student_state").is_none() {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    if row.get::<Option<i32>, _>("state") != Some(2)
        || row
            .get::<Option<i32>, _>("student_state")
            .map(|v| v != 1 && v != 2)
            .unwrap_or(true)
        || row.get::<Option<String>, _>("end_time").is_some()
    {
        return envelope_response(StatusCode::OK, 208, "操作已不可执行", "");
    }
    if sqlx::query(
        "UPDATE t_theory_knowledge_exam_user SET state=?,content=? WHERE exam_id=? AND user_id=?",
    )
    .bind(kind)
    .bind(body.get("content").and_then(Value::as_str))
    .bind(exam_id)
    .bind(&user.id)
    .execute(pool)
    .await
    .is_err()
    {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"examId":exam_id,"userId":user.id,"state":kind})),
        "ok",
        "",
    )
}

pub(crate) async fn realtime(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(mut body): Json<Value>,
) -> Response {
    body["type"] = json!(2);
    student_state(State(state), Extension(user), Json(body)).await
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
    Ok(text.trim().to_string())
}

fn normalize_stored_answer(value: &str) -> String {
    serde_json::from_str::<Value>(value)
        .ok()
        .and_then(|parsed| normalize_answer(&parsed).ok())
        .unwrap_or_else(|| value.trim().to_string())
}

fn score_section(snapshot: &str, answers: &Value, key: &str) -> Result<i32, ()> {
    let questions = serde_json::from_str::<Vec<Value>>(snapshot).map_err(|_| ())?;
    let submitted = match answers.get(key) {
        None => None,
        Some(Value::Array(items)) => Some(items),
        Some(_) => return Err(()),
    };
    if let Some(items) = submitted {
        for item in items {
            if !item.is_object() || item.get("id").and_then(Value::as_str).is_none() {
                return Err(());
            }
        }
    }
    let mut score = 0;
    for question in &questions {
        let id = question.get("id").and_then(Value::as_str).ok_or(())?;
        let expected = question.get("answer").and_then(Value::as_str).ok_or(())?;
        let points = question.get("score").and_then(Value::as_i64).ok_or(())? as i32;
        let answer = submitted.and_then(|items| {
            items
                .iter()
                .find(|item| item.get("id").and_then(Value::as_str) == Some(id))
        });
        if let Some(item) = answer {
            let actual = item.get("answer").ok_or(())?;
            if normalize_answer(actual)? == normalize_stored_answer(expected) {
                score += points;
            }
        }
    }
    Ok(score)
}

pub(crate) async fn finish_self_test(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(exam_id) = body.get("examId").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let content = body.get("content").and_then(Value::as_str).unwrap_or("");
    let answers = match serde_json::from_str::<Value>(content) {
        Ok(Value::Object(value)) => Value::Object(value),
        _ => return envelope_response(StatusCode::OK, 202, "作答内容格式错误", ""),
    };
    for key in [
        "singleChoice",
        "multipleChoice",
        "judge",
        "completion",
        "shortAnswer",
    ] {
        if let Some(value) = answers.get(key) {
            let Some(items) = value.as_array() else {
                return envelope_response(StatusCode::OK, 202, "作答内容格式错误", "");
            };
            if items.iter().any(|item| {
                !item.is_object()
                    || item.get("id").and_then(Value::as_str).is_none()
                    || item.get("answer").is_none()
            }) {
                return envelope_response(StatusCode::OK, 202, "作答内容格式错误", "");
            }
        }
    }
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => {
            tracing::warn!(error = %error, "self-test finish transaction start failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let row = match sqlx::query("SELECT e.state,u.state AS student_state,u.is_self_testing,u.end_time FROM t_theory_knowledge_exam e JOIN t_theory_knowledge_exam_user u ON u.exam_id=e.id AND u.user_id=? WHERE e.id=? FOR UPDATE")
        .bind(&user.id).bind(exam_id).fetch_optional(&mut *tx).await {
        Ok(row) => row,
        Err(error) => {
            tracing::warn!(error = %error, "self-test finish state query failed");
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let Some(row) = row else {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    };
    if row.get::<Option<i32>, _>("is_self_testing") != Some(0) {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    if row.get::<Option<i32>, _>("student_state") != Some(2)
        || row.get::<Option<String>, _>("end_time").is_some()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::OK, 208, "操作已不可执行", "");
    }
    let snapshot = match sqlx::query("SELECT single_choice_list,multiple_choice_list,judge_list,completion_list,short_answer FROM t_theory_knowledge_exam_test_paper WHERE exam_id=? FOR UPDATE")
        .bind(exam_id).fetch_optional(&mut *tx).await {
        Ok(snapshot) => snapshot,
        Err(error) => {
            tracing::warn!(error = %error, "self-test finish snapshot query failed");
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let Some(snapshot) = snapshot else {
        let _ = tx.rollback().await;
        return envelope_response(
            StatusCode::INTERNAL_SERVER_ERROR,
            500,
            "未查询到试卷快照，无法结算自测",
            "",
        );
    };
    let sections = [
        ("single_choice_list", "singleChoice"),
        ("multiple_choice_list", "multipleChoice"),
        ("judge_list", "judge"),
        ("completion_list", "completion"),
        ("short_answer", "shortAnswer"),
    ];
    let mut score = 0;
    for (column, key) in sections {
        let Some(value) = snapshot.get::<Option<String>, _>(column) else {
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        };
        match score_section(&value, &answers, key) {
            Ok(value) => score += value,
            Err(()) => {
                let _ = tx.rollback().await;
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        }
    }
    let end_time = crate::now_time_millis().to_string();
    if let Err(error) = sqlx::query("UPDATE t_theory_knowledge_exam_user SET content=?,score=?,state=4,end_time=? WHERE exam_id=? AND user_id=?")
        .bind(content).bind(score).bind(&end_time).bind(exam_id).bind(&user.id)
        .execute(&mut *tx).await {
        tracing::warn!(error = %error, "self-test finish candidate update failed");
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if let Err(error) =
        sqlx::query("UPDATE t_theory_knowledge_exam SET state=4,end_time=? WHERE id=?")
            .bind(&end_time)
            .bind(exam_id)
            .execute(&mut *tx)
            .await
    {
        tracing::warn!(error = %error, "self-test finish exam update failed");
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if let Err(error) = tx.commit().await {
        tracing::warn!(error = %error, "self-test finish transaction commit failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"id":exam_id,"score":score,"state":4})),
        "ok",
        "",
    )
}

pub(crate) async fn teacher_upload_score(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(exam_id) = body.get("examId").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(list) = body.get("list").and_then(Value::as_array) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    if list.is_empty() {
        return envelope_response(StatusCode::OK, 202, "上分列表不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let admin=sqlx::query_scalar::<_,i64>("SELECT COUNT(*) FROM t_user_role ur JOIN t_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.is_admin=0").bind(&user.id).fetch_one(pool).await.unwrap_or(0)>0;
    if !admin {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    for item in list {
        let Some(uid) = item
            .get("user_id")
            .and_then(Value::as_str)
            .filter(|v| !v.is_empty())
        else {
            let _ = tx.rollback().await;
            return envelope_response(
                StatusCode::OK,
                202,
                "上分列表中存在缺少 user_id 的条目，整批已拒绝",
                "",
            );
        };
        let exists = sqlx::query_scalar::<_, i64>(
            "SELECT COUNT(*) FROM t_theory_knowledge_exam_user WHERE exam_id=? AND user_id=?",
        )
        .bind(exam_id)
        .bind(uid)
        .fetch_one(&mut *tx)
        .await
        .unwrap_or(0);
        if exists == 0 {
            let _ = tx.rollback().await;
            return envelope_response(
                StatusCode::OK,
                202,
                "考生不是本场考试的考生，整批上分已拒绝",
                "",
            );
        }
    }
    for item in list {
        let uid = item.get("user_id").and_then(Value::as_str).unwrap_or("");
        let score = item.get("score").and_then(Value::as_i64).unwrap_or(0);
        let content = item
            .get("content")
            .map(Value::to_string)
            .unwrap_or_else(|| "{}".into());
        if sqlx::query("UPDATE t_theory_knowledge_exam_user SET score=?,state=4,content=? WHERE exam_id=? AND user_id=?").bind(score).bind(content).bind(exam_id).bind(uid).execute(&mut *tx).await.is_err(){let _=tx.rollback().await;return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");}
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, None, "ok", "")
}
pub(crate) async fn self_list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT e.title,e.start_time,u.score,u.content,u.exam_id FROM t_theory_knowledge_exam_user u LEFT JOIN t_theory_knowledge_exam e ON e.id=u.exam_id WHERE u.user_id=? AND u.is_self_testing=0 ORDER BY u.start_time DESC").bind(&user.id).fetch_all(pool).await {
        Ok(rows)=>envelope_json(StatusCode::OK,200,Some(json!(rows.iter().map(|row|json!({"title":row.get::<Option<String>,_>("title"),"start_time":row.get::<Option<String>,_>("start_time"),"score":row.get::<Option<i32>,_>("score"),"content":row.get::<Option<String>,_>("content"),"examId":row.get::<Option<String>,_>("exam_id")})).collect::<Vec<_>>())),"ok",""),
        Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")
    }
}

pub(crate) async fn find_exam_user(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(exam_id) = body.get("examId").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(target) = body.get("userId").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let exam = sqlx::query("SELECT create_user_id,teacher FROM t_theory_knowledge_exam WHERE id=?")
        .bind(exam_id)
        .fetch_optional(pool)
        .await
        .ok()
        .flatten();
    let Some(exam) = exam else {
        return envelope_response(StatusCode::OK, 500, "未查询到考试", "");
    };
    let admin=sqlx::query_scalar::<_,i64>("SELECT COUNT(*) FROM t_user_role ur JOIN t_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.is_admin=0").bind(&actor.id).fetch_one(pool).await.unwrap_or(0)>0;
    let allowed = admin
        || target == actor.id
        || exam.get::<Option<String>, _>("create_user_id").as_deref() == Some(actor.id.as_str())
        || exam.get::<Option<String>, _>("teacher").as_deref() == Some(actor.id.as_str());
    if !allowed {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    match sqlx::query("SELECT id,user_id,content,score,state,end_time FROM t_theory_knowledge_exam_user WHERE exam_id=? AND user_id=? LIMIT 1").bind(exam_id).bind(target).fetch_optional(pool).await { Ok(Some(row))=>envelope_json(StatusCode::OK,200,Some(json!({"id":row.get::<String,_>("id"),"userId":row.get::<Option<String>,_>("user_id"),"content":row.get::<Option<String>,_>("content"),"score":row.get::<Option<i32>,_>("score"),"state":row.get::<Option<i32>,_>("state"),"endTime":row.get::<Option<String>,_>("end_time")})),"ok","") , Ok(None)=>envelope_response(StatusCode::OK,500,"未查询到答卷","") , Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","") }
}

pub(crate) async fn save_formal(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(students) = body.get("stuId").and_then(Value::as_array) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let exam_id = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|v| !v.is_empty())
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    let paper = body.get("testPaper").cloned().unwrap_or_else(|| json!({}));
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let touched=sqlx::query_scalar::<_,i64>("SELECT COUNT(*) FROM t_theory_knowledge_exam_user WHERE exam_id=? AND (state<>1 OR score>0 OR content IS NOT NULL)").bind(&exam_id).fetch_one(&mut *tx).await.unwrap_or(0);
    if touched > 0 {
        let _ = tx.rollback().await;
        return envelope_response(
            StatusCode::OK,
            208,
            "考试已有作答记录，禁止编辑重建考生名单",
            "",
        );
    }
    if sqlx::query("INSERT INTO t_theory_knowledge_exam (id,title,start_time,duration,create_user_id,create_time,teacher,state) VALUES (?,?,?,?,?,?,?,1) ON DUPLICATE KEY UPDATE title=VALUES(title),start_time=VALUES(start_time),duration=VALUES(duration),create_user_id=VALUES(create_user_id),teacher=VALUES(teacher),state=1").bind(&exam_id).bind(body.get("title").and_then(Value::as_str)).bind(body.get("startTime").and_then(Value::as_str)).bind(body.get("duration").and_then(Value::as_str)).bind(&user.id).bind(crate::now_time_millis().to_string()).bind(body.get("teacher").and_then(Value::as_str)).execute(&mut *tx).await.is_err(){let _=tx.rollback().await;return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");}
    let _ = sqlx::query("DELETE FROM t_theory_knowledge_exam_test_paper WHERE exam_id=?")
        .bind(&exam_id)
        .execute(&mut *tx)
        .await;
    let _ = sqlx::query("DELETE FROM t_theory_knowledge_exam_user WHERE exam_id=?")
        .bind(&exam_id)
        .execute(&mut *tx)
        .await;
    if sqlx::query("INSERT INTO t_theory_knowledge_exam_test_paper (id,name,test_paper_id,total,pass_the_exam_than,exam_id,pass_mark,single_choice_list,multiple_choice_list,judge_list,completion_list,short_answer) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)").bind(uuid::Uuid::new_v4().to_string()).bind(paper.get("name").and_then(Value::as_str)).bind(paper.get("id").and_then(Value::as_str)).bind(paper.get("total").and_then(Value::as_i64)).bind(paper.get("passTheExamThan").and_then(Value::as_str)).bind(&exam_id).bind(paper.get("passMark").and_then(Value::as_i64)).bind(paper.get("singleChoice").map(Value::to_string).unwrap_or_else(||"[]".into())).bind(paper.get("multipleChoice").map(Value::to_string).unwrap_or_else(||"[]".into())).bind(paper.get("judge").map(Value::to_string).unwrap_or_else(||"[]".into())).bind(paper.get("completion").map(Value::to_string).unwrap_or_else(||"[]".into())).bind(paper.get("shortAnswer").map(Value::to_string).unwrap_or_else(||"[]".into())).execute(&mut *tx).await.is_err(){let _=tx.rollback().await;return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");}
    for student in students.iter().filter_map(Value::as_str) {
        if sqlx::query("INSERT INTO t_theory_knowledge_exam_user (id,exam_id,user_id,state,score,is_self_testing) VALUES (?,?,?,1,0,1)").bind(uuid::Uuid::new_v4().to_string()).bind(&exam_id).bind(student).execute(&mut *tx).await.is_err(){let _=tx.rollback().await;return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");}
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, None, "ok", "")
}

pub(crate) async fn delete_exam(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("examId")
        .and_then(Value::as_str)
        .or_else(|| body.get("id").and_then(Value::as_str))
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = pool.begin().await.unwrap();
    let _ = sqlx::query("DELETE FROM t_theory_knowledge_exam_user WHERE exam_id=?")
        .bind(id)
        .execute(&mut *tx)
        .await;
    let _ = sqlx::query("DELETE FROM t_theory_knowledge_exam_test_paper WHERE exam_id=?")
        .bind(id)
        .execute(&mut *tx)
        .await;
    if sqlx::query("DELETE FROM t_theory_knowledge_exam WHERE id=?")
        .bind(id)
        .execute(&mut *tx)
        .await
        .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, None, "ok", "")
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
        return envelope_response(StatusCode::OK, 500, "未查询到考试", "");
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
            500,
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
        } else if current < total {
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
    error_top3.truncate(3);
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
