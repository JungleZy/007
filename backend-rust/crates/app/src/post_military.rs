use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rand::seq::SliceRandom;
use serde_json::{json, Map, Value};
use sqlx::{mysql::MySqlRow, MySql, MySqlPool, Row, Transaction};
use std::collections::{HashMap, HashSet};
use uuid::Uuid;

const NOT_STARTED: i32 = 0;
const UNDERWAY: i32 = 1;
const FINISHED: i32 = 2;

#[derive(Debug, serde::Deserialize)]
pub(crate) struct DeleteQuery {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
}

fn database_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "military term training database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

fn train_json(row: &MySqlRow, types: Vec<String>, papers: Option<Vec<Value>>) -> Value {
    let mut result = json!({
        "id": row.get::<String, _>("id"),
        "userId": row.get::<Option<String>, _>("user_id"),
        "types": types,
        "name": row.get::<Option<String>, _>("name"),
        "status": row.get::<Option<i32>, _>("status"),
        "totalNumber": row.get::<Option<i32>, _>("total_number"),
        "correctNumber": row.get::<Option<i32>, _>("correct_number"),
        "errorNumber": row.get::<Option<i32>, _>("error_number"),
        "accuracy": row.get::<Option<f64>, _>("accuracy"),
        "duration": row.get::<Option<i32>, _>("duration"),
        "score": row.get::<Option<f64>, _>("score")
    });
    if let Some(papers) = papers {
        result["testPaperList"] = Value::Array(papers);
    }
    result
}

fn parse_types(value: Option<String>) -> Result<Vec<String>, String> {
    let Some(value) = value else {
        return Ok(Vec::new());
    };
    serde_json::from_str(&value).map_err(|_| "训练类型数据无效".to_owned())
}

fn option_json(row: &MySqlRow, completed: bool) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "trainId": row.get::<Option<String>, _>("train_id"),
        "title": row.get::<Option<String>, _>("title"),
        "option": row.get::<Option<String>, _>("option"),
        "correctAnswer": if completed { row.get::<Option<String>, _>("correct_answer") } else { None },
        "userAnswer": row.get::<Option<String>, _>("user_answer")
    })
}

async fn type_names(pool: &MySqlPool, ids: &[String]) -> Result<Vec<String>, sqlx::Error> {
    if ids.is_empty() {
        return Ok(Vec::new());
    }
    let placeholders = std::iter::repeat("?")
        .take(ids.len())
        .collect::<Vec<_>>()
        .join(",");
    let query = format!("SELECT id, `key` FROM t_military_term_data WHERE id IN ({placeholders})");
    let mut statement = sqlx::query(&query);
    for id in ids {
        statement = statement.bind(id);
    }
    let mut names = HashMap::new();
    for row in statement.fetch_all(pool).await? {
        names.insert(
            row.get::<String, _>("id"),
            row.get::<Option<String>, _>("key").unwrap_or_default(),
        );
    }
    Ok(ids.iter().filter_map(|id| names.get(id).cloned()).collect())
}

async fn train_types(pool: &MySqlPool, row: &MySqlRow) -> Result<Vec<String>, String> {
    let ids = parse_types(row.get::<Option<String>, _>("types"))?;
    type_names(pool, &ids)
        .await
        .map_err(|_| "服务器错误".to_owned())
}

async fn papers_for_view(
    pool: &MySqlPool,
    train_id: &str,
    completed: bool,
) -> Result<Vec<Value>, sqlx::Error> {
    let rows = sqlx::query(
        "SELECT id,train_id,title,`option`,correct_answer,user_answer \
         FROM t_post_military_term_train_test_paper WHERE train_id=?",
    )
    .bind(train_id)
    .fetch_all(pool)
    .await?;
    Ok(rows.iter().map(|row| option_json(row, completed)).collect())
}

async fn load_owned(
    pool: &MySqlPool,
    id: &str,
    user: &AuthUser,
) -> Result<Result<MySqlRow, Response>, sqlx::Error> {
    let query = "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,accuracy,duration,score \
         FROM t_post_military_term_train WHERE id=? LIMIT 1";
    match sqlx::query(query).bind(id).fetch_optional(pool).await? {
        Some(row)
            if row.get::<Option<String>, _>("user_id").as_deref() == Some(user.id.as_str()) =>
        {
            Ok(Ok(row))
        }
        Some(_) => Ok(Err(envelope_response(StatusCode::OK, 207, "无权限", ""))),
        None => Ok(Err(envelope_response(
            StatusCode::OK,
            202,
            "未查询到该训练",
            "",
        ))),
    }
}

pub(crate) async fn list_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let rows = match sqlx::query(
        "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,accuracy,duration,score \
         FROM t_post_military_term_train WHERE user_id=? ORDER BY create_time DESC",
    )
    .bind(&user.id)
    .fetch_all(pool)
    .await {
        Ok(rows) => rows,
        Err(error) => return database_error(error, "list military term training"),
    };
    let mut result = Vec::with_capacity(rows.len());
    for row in &rows {
        let types = match train_types(pool, row).await {
            Ok(types) => types,
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        };
        result.push(train_json(row, types, None));
    }
    envelope_json(StatusCode::OK, 200, Some(Value::Array(result)), "ok", "")
}

pub(crate) async fn details(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|id| !id.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let row = match load_owned(pool, id, &user).await {
        Ok(Ok(row)) => row,
        Ok(Err(response)) => return response,
        Err(error) => return database_error(error, "load military term training details"),
    };
    let types = match train_types(pool, &row).await {
        Ok(types) => types,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let completed = row.get::<Option<i32>, _>("status") == Some(FINISHED);
    let papers = match papers_for_view(pool, id, completed).await {
        Ok(papers) => papers,
        Err(error) => return database_error(error, "load military term training papers"),
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(train_json(&row, types, Some(papers))),
        "ok",
        "",
    )
}

#[derive(Debug)]
struct CategoryTerm {
    key: String,
    value: String,
}

async fn category_terms(
    transaction: &mut Transaction<'_, MySql>,
    ids: &[String],
) -> Result<HashMap<String, Vec<CategoryTerm>>, sqlx::Error> {
    if ids.is_empty() {
        return Ok(HashMap::new());
    }
    let placeholders = std::iter::repeat("?")
        .take(ids.len())
        .collect::<Vec<_>>()
        .join(",");
    let query = format!("SELECT parent_id,`key`,value FROM t_military_term_data WHERE parent_id IN ({placeholders})");
    let mut result: HashMap<String, Vec<CategoryTerm>> = HashMap::new();
    let mut statement = sqlx::query(&query);
    for id in ids {
        statement = statement.bind(id);
    }
    for row in statement.fetch_all(&mut **transaction).await? {
        let Some(parent_id) = row.get::<Option<String>, _>("parent_id") else {
            continue;
        };
        let Some(key) = row.get::<Option<String>, _>("key") else {
            continue;
        };
        let Some(value) = row.get::<Option<String>, _>("value") else {
            continue;
        };
        result
            .entry(parent_id)
            .or_default()
            .push(CategoryTerm { key, value });
    }
    Ok(result)
}

fn java_hash(value: &str) -> i32 {
    value.chars().fold(0i32, |hash, ch| {
        hash.wrapping_mul(31).wrapping_add(ch as i32)
    })
}

fn make_paper(category: &[CategoryTerm]) -> Result<(String, String, String), String> {
    let distinct = category
        .iter()
        .map(|term| term.value.as_str())
        .collect::<HashSet<_>>();
    if distinct.len() < 4 {
        return Err("有效题目不足4条，无法生成干扰项".to_owned());
    }
    let mut rng = rand::thread_rng();
    let answer = category
        .choose(&mut rng)
        .ok_or_else(|| "训练题目为空".to_owned())?;
    let mut values = vec![answer.value.clone()];
    let mut candidates = category
        .iter()
        .map(|term| term.value.clone())
        .collect::<Vec<_>>();
    candidates.shuffle(&mut rng);
    for value in candidates {
        if values.len() >= 4 {
            break;
        }
        if !values.contains(&value) {
            values.push(value);
        }
    }
    values.sort_by_key(|value| java_hash(value));
    let correct_index = values
        .iter()
        .position(|value| value == &answer.value)
        .ok_or_else(|| "正确答案生成失败".to_owned())?;
    let correct = ((b'A' + correct_index as u8) as char).to_string();
    let mut options = Map::new();
    for (index, value) in values.into_iter().enumerate() {
        options.insert(
            ((b'A' + index as u8) as char).to_string(),
            Value::String(value),
        );
    }
    Ok((
        answer.key.clone(),
        serde_json::to_string(&options).map_err(|_| "选项生成失败".to_owned())?,
        correct,
    ))
}

pub(crate) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let total = match body.get("totalNumber").and_then(Value::as_i64) {
        Some(value) if (0..=i32::MAX as i64).contains(&value) => value as i32,
        _ => return envelope_response(StatusCode::OK, 202, "数量不能为空", ""),
    };
    let requested_types = match body.get("types") {
        None | Some(Value::Null) => Vec::new(),
        Some(Value::Array(values)) => {
            let mut types = Vec::with_capacity(values.len());
            for value in values {
                let Some(value) = value.as_str() else {
                    return envelope_response(StatusCode::OK, 202, "训练类型格式无效", "");
                };
                types.push(value.to_owned());
            }
            types
        }
        Some(_) => return envelope_response(StatusCode::OK, 202, "训练类型格式无效", ""),
    };
    let effective_types = if requested_types.is_empty() {
        match sqlx::query(
            "SELECT id FROM t_military_term_data WHERE parent_id='0' ORDER BY sort,id",
        )
        .fetch_all(pool)
        .await
        {
            Ok(rows) => rows
                .into_iter()
                .map(|row| row.get::<String, _>("id"))
                .collect::<Vec<_>>(),
            Err(error) => return database_error(error, "load military term categories"),
        }
    } else {
        requested_types.clone()
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start military term training transaction"),
    };
    let categories = match category_terms(&mut transaction, &effective_types).await {
        Ok(categories) => categories,
        Err(error) => return database_error(error, "load military term data"),
    };
    let valid_types = effective_types
        .iter()
        .filter(|id| categories.get(*id).is_some_and(|terms| terms.len() >= 4))
        .cloned()
        .collect::<Vec<_>>();
    if valid_types.is_empty() {
        let _ = transaction.rollback().await;
        return envelope_response(
            StatusCode::OK,
            202,
            "该类型条目少于4条，不能生成训练，请重新选择类型",
            "",
        );
    }
    let id = Uuid::new_v4().to_string();
    let stored_types = match serde_json::to_string(&requested_types) {
        Ok(value) => value,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if let Err(error) = sqlx::query(
        "INSERT INTO t_post_military_term_train \
         (id,user_id,types,name,status,total_number,correct_number,error_number,accuracy,duration,score,create_time) \
         VALUES (?,?,?,?,0,?,?,?,0,0,0,NOW())",
    )
    .bind(&id)
    .bind(&user.id)
    .bind(stored_types)
    .bind(body.get("name").and_then(Value::as_str))
    .bind(total)
    .bind(0i32)
    .bind(0i32)
    .execute(&mut *transaction)
    .await {
        return database_error(error, "create military term training");
    }
    let generated_papers = {
        let mut rng = rand::thread_rng();
        let mut generated = Vec::with_capacity(total as usize);
        for _ in 0..total {
            let Some(category_id) = valid_types.choose(&mut rng) else {
                return envelope_response(StatusCode::OK, 202, "训练类型为空", "");
            };
            let Some(terms) = categories.get(category_id) else {
                return envelope_response(StatusCode::OK, 202, "训练类型无效", "");
            };
            let paper = match make_paper(terms) {
                Ok(paper) => paper,
                Err(message) => return envelope_response(StatusCode::OK, 202, &message, ""),
            };
            generated.push(paper);
        }
        generated
    };
    for (title, options, correct) in generated_papers {
        if let Err(error) = sqlx::query(
            "INSERT INTO t_post_military_term_train_test_paper (id,train_id,title,`option`,correct_answer,user_answer) VALUES (?,?,?,?,?,NULL)",
        )
        .bind(Uuid::new_v4().to_string())
        .bind(&id)
        .bind(title)
        .bind(options)
        .bind(correct)
        .execute(&mut *transaction)
        .await
        {
            return database_error(error, "create military term training paper");
        }
    }
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit military term training");
    }
    let types = match type_names(pool, &valid_types).await {
        Ok(types) => types,
        Err(error) => return database_error(error, "load military term names"),
    };
    let row = match sqlx::query(
        "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,accuracy,duration,score \
         FROM t_post_military_term_train WHERE id=?",
    ).bind(&id).fetch_one(pool).await {
        Ok(row) => row,
        Err(error) => return database_error(error, "reload military term training"),
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(train_json(&row, types, None)),
        "ok",
        "",
    )
}

pub(crate) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|id| !id.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start military term begin transaction"),
    };
    let row = match sqlx::query(
        "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,accuracy,duration,score \
         FROM t_post_military_term_train WHERE id=? LIMIT 1 FOR UPDATE",
    ).bind(id).fetch_optional(&mut *transaction).await {
        Ok(Some(row)) if row.get::<Option<String>, _>("user_id").as_deref() == Some(user.id.as_str()) => row,
        Ok(Some(_)) => return envelope_response(StatusCode::OK, 207, "无权限", ""),
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该训练", ""),
        Err(error) => return database_error(error, "load military term for begin"),
    };
    match row.get::<Option<i32>, _>("status") {
        Some(FINISHED) => {
            return envelope_response(StatusCode::OK, 208, "训练已完成，不能重新开始", "")
        }
        Some(UNDERWAY) => {}
        Some(NOT_STARTED) => {
            if let Err(error) = sqlx::query(
                "UPDATE t_post_military_term_train SET status=1,start_time=NOW() WHERE id=?",
            )
            .bind(id)
            .execute(&mut *transaction)
            .await
            {
                return database_error(error, "begin military term training");
            }
        }
        _ => return envelope_response(StatusCode::OK, 202, "训练状态无效", ""),
    }
    let current = match sqlx::query(
        "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,accuracy,duration,score \
         FROM t_post_military_term_train WHERE id=?",
    ).bind(id).fetch_one(&mut *transaction).await {
        Ok(row) => row,
        Err(error) => return database_error(error, "reload military term after begin"),
    };
    let type_ids = match parse_types(current.get::<Option<String>, _>("types")) {
        Ok(ids) => ids,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit military term begin");
    }
    let types = match type_names(pool, &type_ids).await {
        Ok(types) => types,
        Err(error) => return database_error(error, "load military term names after begin"),
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(train_json(&current, types, None)),
        "ok",
        "",
    )
}

fn answer_value(body: &Value) -> Option<&str> {
    body.get("userAnswer").and_then(Value::as_str)
}

pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|id| !id.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    let submitted: &[Value] = match body.get("testPaperList") {
        None | Some(Value::Null) => &[],
        Some(Value::Array(values)) => values,
        Some(_) => return envelope_response(StatusCode::OK, 202, "提交的答案格式无效", ""),
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start military term finish transaction"),
    };
    let train = match sqlx::query(
        "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,accuracy,duration,score,IF(start_time IS NULL,0,1) AS has_start_time \
         FROM t_post_military_term_train WHERE id=? LIMIT 1 FOR UPDATE",
    ).bind(id).fetch_optional(&mut *transaction).await {
        Ok(Some(row)) if row.get::<Option<String>, _>("user_id").as_deref() == Some(user.id.as_str()) => row,
        Ok(Some(_)) => return envelope_response(StatusCode::OK, 207, "无权限", ""),
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该训练", ""),
        Err(error) => return database_error(error, "load military term for finish"),
    };
    let papers = match sqlx::query(
        "SELECT id,train_id,title,`option`,correct_answer,user_answer FROM t_post_military_term_train_test_paper WHERE train_id=? FOR UPDATE",
    ).bind(id).fetch_all(&mut *transaction).await {
        Ok(rows) => rows,
        Err(error) => return database_error(error, "lock military term papers"),
    };
    let paper_ids = papers
        .iter()
        .filter_map(|paper| paper.get::<Option<String>, _>("id"))
        .collect::<HashSet<_>>();
    let mut seen = HashSet::new();
    let mut answers = HashMap::new();
    for answer in submitted {
        let Some(answer_id) = answer
            .get("id")
            .and_then(Value::as_str)
            .filter(|id| !id.is_empty())
        else {
            return envelope_response(StatusCode::OK, 202, "提交的题目无效或重复", "");
        };
        if !paper_ids.contains(answer_id) || !seen.insert(answer_id.to_owned()) {
            return envelope_response(StatusCode::OK, 202, "提交的题目无效或重复", "");
        }
        if answer
            .get("userAnswer")
            .is_some_and(|value| !value.is_null() && !value.is_string())
        {
            return envelope_response(StatusCode::OK, 202, "提交的答案格式无效", "");
        }
        answers.insert(
            answer_id.to_owned(),
            answer_value(answer).map(ToOwned::to_owned),
        );
    }
    for paper in &papers {
        let Some(paper_id) = paper.get::<Option<String>, _>("id") else {
            continue;
        };
        let Some(Some(answer)) = answers.get(&paper_id) else {
            continue;
        };
        let options: HashMap<String, String> = match serde_json::from_str::<HashMap<String, String>>(
            paper
                .get::<Option<String>, _>("option")
                .as_deref()
                .unwrap_or(""),
        ) {
            Ok(options) if !options.is_empty() => options,
            _ => return envelope_response(StatusCode::OK, 202, "题目选项无效", ""),
        };
        let Some(correct) = paper.get::<Option<String>, _>("correct_answer") else {
            return envelope_response(StatusCode::OK, 202, "提交的答案无效", "");
        };
        if !options.contains_key(&correct) || !options.contains_key(answer) {
            return envelope_response(StatusCode::OK, 202, "提交的答案无效", "");
        }
    }
    if train.get::<Option<i32>, _>("status") == Some(FINISHED) {
        let unchanged = papers.iter().all(|paper| {
            let id = paper.get::<Option<String>, _>("id");
            id.as_ref().is_some_and(|id| {
                paper.get::<Option<String>, _>("user_answer") == answers.get(id).cloned().flatten()
            })
        });
        if unchanged {
            let type_ids = match parse_types(train.get::<Option<String>, _>("types")) {
                Ok(ids) => ids,
                Err(_) => {
                    return envelope_response(
                        StatusCode::INTERNAL_SERVER_ERROR,
                        500,
                        "服务器错误",
                        "",
                    )
                }
            };
            if let Err(error) = transaction.commit().await {
                return database_error(error, "commit idempotent military term finish");
            }
            let types = match type_names(pool, &type_ids).await {
                Ok(types) => types,
                Err(error) => return database_error(error, "load completed military term names"),
            };
            return envelope_json(
                StatusCode::OK,
                200,
                Some(train_json(&train, types, None)),
                "ok",
                "",
            );
        }
        return envelope_response(StatusCode::OK, 208, "训练已完成，不能修改结果", "");
    }
    if train.get::<Option<i32>, _>("status") != Some(UNDERWAY) {
        return envelope_response(StatusCode::OK, 202, "训练尚未开始", "");
    }
    if train.get::<Option<i32>, _>("has_start_time") != Some(1) {
        return envelope_response(StatusCode::OK, 208, "训练开始时间缺失，不能完成", "");
    }
    let mut correct = 0i32;
    let mut answered = 0i32;
    for paper in &papers {
        let Some(paper_id) = paper.get::<Option<String>, _>("id") else {
            continue;
        };
        let answer = answers.get(&paper_id).cloned().flatten();
        if let Some(answer_value) = &answer {
            answered += 1;
            let options: HashMap<String, String> = serde_json::from_str(
                paper
                    .get::<Option<String>, _>("option")
                    .as_deref()
                    .unwrap_or("{}"),
            )
            .unwrap_or_default();
            if options.get(answer_value)
                == options.get(
                    paper
                        .get::<Option<String>, _>("correct_answer")
                        .as_deref()
                        .unwrap_or(""),
                )
            {
                correct += 1;
            }
        }
        if let Err(error) =
            sqlx::query("UPDATE t_post_military_term_train_test_paper SET user_answer=? WHERE id=?")
                .bind(answer)
                .bind(paper_id)
                .execute(&mut *transaction)
                .await
        {
            return database_error(error, "save military term answer");
        }
    }
    if let Err(error) = sqlx::query(
        "UPDATE t_post_military_term_train SET status=2,end_time=NOW(),correct_number=?,error_number=?,accuracy=IF(?=0,0,ROUND(?/?*100,2)),score=IF(?=0,0,ROUND(?/?*100,3)),duration=TIMESTAMPDIFF(SECOND,start_time,NOW()) WHERE id=?",
    ).bind(correct).bind(answered - correct).bind(answered).bind(correct).bind(answered).bind(papers.len() as i32).bind(correct).bind(papers.len() as i32).bind(id).execute(&mut *transaction).await {
        return database_error(error, "finish military term training");
    }
    let current = match sqlx::query(
        "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,accuracy,duration,score FROM t_post_military_term_train WHERE id=?",
    ).bind(id).fetch_one(&mut *transaction).await {
        Ok(row) => row,
        Err(error) => return database_error(error, "reload military term after finish"),
    };
    let type_ids = match parse_types(current.get::<Option<String>, _>("types")) {
        Ok(ids) => ids,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit military term finish");
    }
    let types = match type_names(pool, &type_ids).await {
        Ok(types) => types,
        Err(error) => return database_error(error, "load military term names after finish"),
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(train_json(&current, types, None)),
        "ok",
        "",
    )
}

pub(crate) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<DeleteQuery>,
) -> Response {
    let Some(id) = query.train_id.filter(|id| !id.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start military term delete transaction"),
    };
    let owner = match sqlx::query(
        "SELECT user_id FROM t_post_military_term_train WHERE id=? LIMIT 1 FOR UPDATE",
    )
    .bind(&id)
    .fetch_optional(&mut *transaction)
    .await
    {
        Ok(Some(row)) => row.get::<Option<String>, _>("user_id"),
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该训练", ""),
        Err(error) => return database_error(error, "load military term for delete"),
    };
    if owner.as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    if let Err(error) =
        sqlx::query("DELETE FROM t_post_military_term_train_test_paper WHERE train_id=?")
            .bind(&id)
            .execute(&mut *transaction)
            .await
    {
        return database_error(error, "delete military term papers");
    }
    if let Err(error) =
        sqlx::query("DELETE FROM t_post_military_term_train WHERE id=? AND user_id=?")
            .bind(&id)
            .bind(&user.id)
            .execute(&mut *transaction)
            .await
    {
        return database_error(error, "delete military term training");
    }
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit military term delete");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", "")
}
