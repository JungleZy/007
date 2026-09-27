use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rand::Rng;
use rust_decimal::{Decimal, RoundingStrategy};
use serde::Deserialize;
use serde_json::{json, Map, Value};
use sqlx::{mysql::MySqlRow, MySql, Row, Transaction};
use std::str::FromStr;

const TRAIN_COLUMNS: &str = "id,name,user_id,status,type,speed,number,disturb,start_time,end_time,duration,create_time,content,accuracy,error_number,pass_number,score,train_type";
const TERM_QUERY: &str = "SELECT id,type,`key`,value,sort FROM t_post_radiotelephone_term_data WHERE type=? ORDER BY `key`,id";

#[derive(Debug, Deserialize)]
pub(crate) struct DeleteQuery {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
}

fn database_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "post radiotelephone database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

fn invalid(message: &'static str) -> Response {
    envelope_response(StatusCode::OK, 202, message, message)
}

fn optional_i32(body: &Value, name: &str) -> Result<Option<i32>, Response> {
    match body.get(name) {
        None | Some(Value::Null) => Ok(None),
        Some(value) => value
            .as_i64()
            .and_then(|value| i32::try_from(value).ok())
            .map(Some)
            .ok_or_else(|| invalid("请求参数错误")),
    }
}
fn optional_string(body: &Value, name: &str) -> Result<Option<String>, Response> {
    match body.get(name) {
        None | Some(Value::Null) => Ok(None),
        Some(Value::String(value)) => Ok(Some(value.clone())),
        Some(_) => Err(invalid("请求参数错误")),
    }
}

fn parse_content(value: Option<&Value>) -> Result<Vec<Value>, &'static str> {
    let Some(Value::String(value)) = value else {
        return Err("作答内容不能为空");
    };
    if value.is_empty() {
        return Err("作答内容不能为空");
    }
    serde_json::from_str(value).map_err(|_| "作答内容必须为有效的数组")
}

fn parse_stored_content(value: Option<String>) -> Result<Vec<Value>, &'static str> {
    serde_json::from_str(value.as_deref().ok_or("作答内容不能为空")?)
        .map_err(|_| "作答内容必须为有效的数组")
}

fn object(value: &Value) -> Result<&Map<String, Value>, &'static str> {
    value.as_object().ok_or("作答条目无效")
}

fn text(value: Option<&Value>) -> Result<&str, &'static str> {
    value
        .and_then(Value::as_str)
        .ok_or("作答条目必须包含有效文本")
}

fn json_term(row: &MySqlRow) -> Value {
    json!({
        "id": row.get::<i32, _>("id"),
        "type": row.get::<Option<i32>, _>("type"),
        "key": row.get::<Option<String>, _>("key"),
        "value": row.get::<Option<String>, _>("value"),
        "sort": row.get::<Option<i32>, _>("sort")
    })
}

pub(crate) async fn find_by_type(
    State(state): State<AppState>,
    Extension(_user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let kind = match optional_i32(&body, "type") {
        Ok(kind) => kind,
        Err(response) => return response,
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let rows = if let Some(kind) = kind {
        sqlx::query("SELECT id,type,`key`,value,sort FROM t_post_radiotelephone_term_data WHERE type=? ORDER BY `key`,id")
            .bind(kind)
            .fetch_all(pool)
            .await
    } else {
        sqlx::query("SELECT id,type,`key`,value,sort FROM t_post_radiotelephone_term_data ORDER BY `key`,id")
            .fetch_all(pool)
            .await
    };
    match rows {
        Ok(rows) => envelope_json(
            StatusCode::OK,
            200,
            Some(Value::Array(rows.iter().map(json_term).collect())),
            "ok",
            "",
        ),
        Err(error) => database_error(error, "find terms by type"),
    }
}

pub(crate) async fn find_by_type_and_random(
    State(state): State<AppState>,
    Extension(_user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(kind) = (match optional_i32(&body, "type") {
        Ok(kind) => kind,
        Err(response) => return response,
    }) else {
        return invalid("训练类型不能为空");
    };
    let Some(number) = body.get("number") else {
        return invalid("抽取数量不能为空");
    };
    let Some(number) = number.as_i64().and_then(|value| i32::try_from(value).ok()) else {
        return invalid("抽取数量不能为空");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let rows = match sqlx::query(TERM_QUERY).bind(kind).fetch_all(pool).await {
        Ok(rows) => rows,
        Err(error) => return database_error(error, "find random terms"),
    };
    if rows.is_empty() {
        return invalid("该类型下无词条，无法随机抽取");
    }
    let mut random = rand::thread_rng();
    let mut selected = Vec::new();
    for _ in 0..number.max(0) {
        selected.push(json_term(&rows[random.gen_range(0..rows.len())]));
    }
    envelope_json(StatusCode::OK, 200, Some(Value::Array(selected)), "ok", "")
}

fn input_disturb(body: &Value) -> Result<String, Response> {
    let Some(value) = body.get("disturb") else {
        return Ok(String::new());
    };
    if value.is_null() {
        return Ok(String::new());
    }
    let Some(values) = value.as_array() else {
        return Err(invalid("请求参数错误"));
    };
    let mut result = Vec::with_capacity(values.len());
    for value in values {
        match value {
            Value::String(value) => result.push(Value::String(value.clone())),
            Value::Number(value) => result.push(Value::String(value.to_string())),
            _ => return Err(invalid("请求参数错误")),
        }
    }
    serde_json::to_string(&result).map_err(|_| invalid("请求参数错误"))
}

fn input_decimal(body: &Value, name: &str) -> Result<Option<String>, Response> {
    let Some(value) = body.get(name) else {
        return Ok(None);
    };
    if value.is_null() {
        return Ok(None);
    }
    let text = match value {
        Value::Number(value) => value.to_string(),
        Value::String(value) => value.clone(),
        _ => return Err(invalid("请求参数错误")),
    };
    if Decimal::from_str(&text).is_err() {
        return Err(invalid("请求参数错误"));
    }
    Ok(Some(text))
}

fn decimal_value(value: Option<String>) -> Result<Value, &'static str> {
    let Some(value) = value else {
        return Ok(Value::Null);
    };
    let parsed: Value = serde_json::from_str(&value).map_err(|_| "数据库成绩格式无效")?;
    if parsed.is_number() {
        Ok(parsed)
    } else {
        Err("数据库成绩格式无效")
    }
}

fn disturb_value(value: Option<String>) -> Result<Value, &'static str> {
    let Some(value) = value.filter(|value| !value.is_empty()) else {
        return Ok(Value::Array(Vec::new()));
    };
    let parsed: Value = serde_json::from_str(&value).map_err(|_| "训练干扰格式无效")?;
    let Some(values) = parsed.as_array() else {
        return Err("训练干扰格式无效");
    };
    let mut result = Vec::with_capacity(values.len());
    for value in values {
        let text = match value {
            Value::String(value) => value.clone(),
            Value::Number(value) => value.to_string(),
            _ => return Err("训练干扰格式无效"),
        };
        let value = text.parse::<i32>().map_err(|_| "训练干扰格式无效")?;
        result.push(json!(value));
    }
    Ok(Value::Array(result))
}

fn train_view(row: &MySqlRow, include_content: bool) -> Result<Value, &'static str> {
    let duration = row
        .get::<Option<i64>, _>("effective_duration")
        .map(|value| i32::try_from(value).map_err(|_| "训练时长超出范围"))
        .transpose()?;
    Ok(json!({
        "id": row.get::<String, _>("id"),
        "name": row.get::<Option<String>, _>("name"),
        "status": row.get::<Option<i32>, _>("status"),
        "type": row.get::<Option<i32>, _>("type"),
        "speed": decimal_value(row.get::<Option<String>, _>("speed"))?,
        "number": row.get::<Option<i32>, _>("number"),
        "disturb": disturb_value(row.get::<Option<String>, _>("disturb"))?,
        "duration": duration,
        "content": if include_content {
            json!(row.get::<Option<String>, _>("content"))
        } else {
            Value::Null
        },
        "accuracy": decimal_value(row.get::<Option<String>, _>("accuracy"))?,
        "errorNumber": row.get::<Option<i32>, _>("error_number"),
        "passNumber": row.get::<Option<i32>, _>("pass_number"),
        "score": decimal_value(row.get::<Option<String>, _>("score"))?,
        "trainType": row.get::<Option<i32>, _>("train_type")
    }))
}

async fn select_train(
    transaction: &mut Transaction<'_, MySql>,
    id: &str,
    lock: bool,
) -> Result<Option<MySqlRow>, sqlx::Error> {
    let suffix = if lock { " FOR UPDATE" } else { "" };
    let query = format!(
        "SELECT {TRAIN_COLUMNS}, CASE WHEN status=1 AND start_time IS NOT NULL THEN TIMESTAMPDIFF(SECOND,start_time,NOW(6)) ELSE duration END AS effective_duration FROM t_post_radiotelephone_train WHERE id=? LIMIT 1{suffix}"
    );
    sqlx::query(&query)
        .bind(id)
        .fetch_optional(&mut **transaction)
        .await
}

async fn select_train_pool(state: &AppState, id: &str) -> Result<Option<MySqlRow>, sqlx::Error> {
    let Some(pool) = state.pool.as_ref() else {
        return Ok(None);
    };
    let query = format!(
        "SELECT {TRAIN_COLUMNS}, CASE WHEN status=1 AND start_time IS NOT NULL THEN TIMESTAMPDIFF(SECOND,start_time,NOW(6)) ELSE duration END AS effective_duration FROM t_post_radiotelephone_train WHERE id=? LIMIT 1"
    );
    sqlx::query(&query).bind(id).fetch_optional(pool).await
}

fn owner_response(row: &MySqlRow, user_id: &str) -> Option<Response> {
    if row.get::<Option<String>, _>("user_id").as_deref() != Some(user_id) {
        Some(envelope_response(StatusCode::OK, 207, "无权限", ""))
    } else {
        None
    }
}

pub(crate) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(kind) = (match optional_i32(&body, "type") {
        Ok(value) => value,
        Err(response) => return response,
    }) else {
        return invalid("训练类型不能为空");
    };
    let Some(number) = body
        .get("number")
        .and_then(Value::as_i64)
        .and_then(|value| i32::try_from(value).ok())
    else {
        return invalid("词条数量不能为空");
    };
    let speed = match input_decimal(&body, "speed") {
        Ok(value) => value,
        Err(response) => return response,
    };
    let disturb = match input_disturb(&body) {
        Ok(value) => value,
        Err(response) => return response,
    };
    let name = match optional_string(&body, "name") {
        Ok(value) => value,
        Err(response) => return response,
    };
    let train_type = match optional_i32(&body, "trainType") {
        Ok(value) => value,
        Err(response) => return response,
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start add transaction"),
    };
    let terms = match sqlx::query(TERM_QUERY)
        .bind(kind)
        .fetch_all(&mut *transaction)
        .await
    {
        Ok(terms) => terms,
        Err(error) => return database_error(error, "load add terms"),
    };
    if terms.is_empty() {
        return invalid("该类型下无词条，无法生成训练内容");
    }
    let content = {
        let mut random = rand::thread_rng();
        let mut content = Vec::new();
        for _ in 0..number.max(0) {
            content.push(json_term(&terms[random.gen_range(0..terms.len())]));
        }
        content
    };
    let content = match serde_json::to_string(&Value::Array(content)) {
        Ok(content) => content,
        Err(_) => return invalid("请求参数错误"),
    };
    let id = uuid::Uuid::new_v4().to_string();
    let result = sqlx::query("INSERT INTO t_post_radiotelephone_train (id,name,user_id,status,type,speed,number,disturb,duration,create_time,content,accuracy,error_number,pass_number,train_type) VALUES (?,?,?,?,?,?,?,? ,0,NOW(6),?,0,0,0,?)")
        .bind(&id)
        .bind(name)
        .bind(&user.id)
        .bind(0_i32)
        .bind(kind)
        .bind(speed)
        .bind(number)
        .bind(disturb)
        .bind(content)
        .bind(train_type)
        .execute(&mut *transaction)
        .await;
    if let Err(error) = result {
        return database_error(error, "insert radiotelephone training");
    }
    let row = match select_train(&mut transaction, &id, false).await {
        Ok(Some(row)) => row,
        Ok(None) => {
            return database_error(sqlx::Error::RowNotFound, "reload radiotelephone training")
        }
        Err(error) => return database_error(error, "reload radiotelephone training"),
    };
    let data = match train_view(&row, true) {
        Ok(data) => data,
        Err(message) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, message, "")
        }
    };
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit add transaction");
    }
    envelope_json(StatusCode::OK, 200, Some(data), "ok", "")
}

pub(crate) async fn list_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let query = format!(
        "SELECT {TRAIN_COLUMNS}, CASE WHEN status=1 AND start_time IS NOT NULL THEN TIMESTAMPDIFF(SECOND,start_time,NOW(6)) ELSE duration END AS effective_duration FROM t_post_radiotelephone_train WHERE user_id=? ORDER BY create_time DESC"
    );
    let rows = match sqlx::query(&query).bind(&user.id).fetch_all(pool).await {
        Ok(rows) => rows,
        Err(error) => return database_error(error, "list radiotelephone training"),
    };
    let mut data = Vec::with_capacity(rows.len());
    for row in &rows {
        match train_view(row, false) {
            Ok(value) => data.push(value),
            Err(message) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, message, "")
            }
        }
    }
    envelope_json(StatusCode::OK, 200, Some(Value::Array(data)), "ok", "")
}

pub(crate) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body.get("id").and_then(Value::as_str).map(str::to_owned) else {
        return invalid("训练ID不能为空");
    };
    if id.trim().is_empty() {
        return invalid("训练ID不能为空");
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start begin transaction"),
    };
    let row = match select_train(&mut transaction, &id, true).await {
        Ok(Some(row)) => row,
        Ok(None) => return invalid("未查询到该训练"),
        Err(error) => return database_error(error, "load radiotelephone training for begin"),
    };
    if let Some(response) = owner_response(&row, &user.id) {
        return response;
    }
    match row.get::<Option<i32>, _>("status") {
        Some(2) => return envelope_response(StatusCode::OK, 208, "训练已结束", "训练已结束"),
        Some(1) => {}
        Some(0) => {
            if let Err(error) = sqlx::query(
                "UPDATE t_post_radiotelephone_train SET status=1,start_time=NOW(6) WHERE id=?",
            )
            .bind(&id)
            .execute(&mut *transaction)
            .await
            {
                return database_error(error, "begin radiotelephone training");
            }
        }
        _ => return invalid("训练状态无效"),
    }
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit begin transaction");
    }
    envelope_json(StatusCode::OK, 200, None, "ok", "")
}

fn same_answers(stored: Option<String>, submitted: Option<&Value>) -> Result<bool, &'static str> {
    let previous = parse_stored_content(stored)?;
    let current = parse_content(submitted)?;
    if previous.len() != current.len() {
        return Ok(false);
    }
    for (left, right) in previous.iter().zip(current.iter()) {
        let left = object(left)?;
        let right = object(right)?;
        if left.get("key") != right.get("key")
            || left.get("value") != right.get("value")
            || left.get("answer") != right.get("answer")
        {
            return Ok(false);
        }
    }
    Ok(true)
}

pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body.get("id").and_then(Value::as_str).map(str::to_owned) else {
        return invalid("训练ID不能为空");
    };
    if id.trim().is_empty() {
        return invalid("训练ID不能为空");
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start finish transaction"),
    };
    let row = match select_train(&mut transaction, &id, true).await {
        Ok(Some(row)) => row,
        Ok(None) => return invalid("未查询到该训练"),
        Err(error) => return database_error(error, "load radiotelephone training for finish"),
    };
    if let Some(response) = owner_response(&row, &user.id) {
        return response;
    }
    if row.get::<Option<i32>, _>("status") == Some(2) {
        match same_answers(row.get::<Option<String>, _>("content"), body.get("content")) {
            Ok(true) => {
                if let Err(error) = transaction.commit().await {
                    return database_error(error, "commit idempotent finish");
                }
                return envelope_json(StatusCode::OK, 200, None, "ok", "");
            }
            Ok(false) => {
                return envelope_response(
                    StatusCode::OK,
                    208,
                    "训练已结束，不能修改结果",
                    "训练已结束，不能修改结果",
                )
            }
            Err(message) => return invalid(message),
        }
    }
    if row.get::<Option<i32>, _>("status") != Some(1)
        || row.get::<Option<String>, _>("start_time").is_none()
    {
        return invalid("训练尚未开始");
    }
    for field in ["score", "accuracy", "passNumber", "errorNumber", "duration"] {
        if body.get(field).is_some_and(|value| !value.is_null()) {
            return invalid("成绩和用时由服务端计算，请仅提交作答内容");
        }
    }
    let source = match parse_stored_content(row.get::<Option<String>, _>("content")) {
        Ok(source) => source,
        Err(message) => return invalid(message),
    };
    let answers = match parse_content(body.get("content")) {
        Ok(answers) => answers,
        Err(message) => return invalid(message),
    };
    let number = row.get::<Option<i32>, _>("number");
    let train_type = row.get::<Option<i32>, _>("train_type");
    let kind = row.get::<Option<i32>, _>("type");
    if source.is_empty()
        || source.len() != answers.len()
        || number.map(|value| value as usize) != Some(source.len())
        || !train_type.is_some_and(|value| (0..=1).contains(&value))
        || !kind.is_some_and(|value| (0..=1).contains(&value))
    {
        return invalid("作答条目与训练题面不一致");
    }
    let mut correct = 0_i32;
    let mut captured = source.clone();
    for (index, (source_item, submitted_item)) in source.iter().zip(answers.iter()).enumerate() {
        let target = match object(source_item) {
            Ok(value) => value,
            Err(message) => return invalid(message),
        };
        let submitted = match object(submitted_item) {
            Ok(value) => value,
            Err(message) => return invalid(message),
        };
        let key = match text(target.get("key")) {
            Ok(value) => value,
            Err(message) => return invalid(message),
        };
        let value = match text(target.get("value")) {
            Ok(value) => value,
            Err(message) => return invalid(message),
        };
        let submitted_key = match text(submitted.get("key")) {
            Ok(value) => value,
            Err(message) => return invalid(message),
        };
        let submitted_value = match text(submitted.get("value")) {
            Ok(value) => value,
            Err(message) => return invalid(message),
        };
        if key != submitted_key || value != submitted_value {
            return invalid("作答题面与保存的训练内容不一致");
        }
        let answer = match submitted.get("answer").and_then(Value::as_object) {
            Some(value) => value,
            None => return invalid("作答条目无效"),
        };
        let answer_key = match text(answer.get("key")) {
            Ok(value) => value,
            Err(message) => return invalid(message),
        };
        let answer_value = match text(answer.get("value")) {
            Ok(value) => value,
            Err(message) => return invalid(message),
        };
        let passed = if train_type == Some(0) {
            key == answer_key.trim() && value.trim() == answer_value
        } else if kind == Some(0) {
            key.trim() == answer_key.trim()
        } else {
            value.trim() == answer_value.trim()
        };
        if passed {
            correct += 1;
        }
        let target = match captured[index].as_object_mut() {
            Some(target) => target,
            None => return invalid("作答条目无效"),
        };
        let mut captured_answer = Map::new();
        captured_answer.insert("key".to_owned(), Value::String(answer_key.to_owned()));
        captured_answer.insert("value".to_owned(), Value::String(answer_value.to_owned()));
        target.insert("answer".to_owned(), Value::Object(captured_answer));
        target.insert("trueOrfalse".to_owned(), Value::Bool(passed));
    }
    let elapsed = row.get::<Option<i64>, _>("effective_duration");
    let Some(elapsed) = elapsed else {
        return invalid("训练开始时间无效");
    };
    if elapsed < 0 || elapsed > i64::from(i32::MAX) {
        return invalid("训练开始时间无效");
    }
    let total = captured.len() as i64;
    let accuracy = (Decimal::from(correct as i64) * Decimal::from(100_i64) / Decimal::from(total))
        .round_dp_with_strategy(2, RoundingStrategy::MidpointAwayFromZero)
        .to_string();
    let score = (Decimal::from(correct as i64) * Decimal::from(100_i64) / Decimal::from(total))
        .round_dp_with_strategy(1, RoundingStrategy::MidpointAwayFromZero)
        .to_string();
    let content = match serde_json::to_string(&captured) {
        Ok(content) => content,
        Err(_) => return invalid("作答内容必须为有效的数组"),
    };
    if let Err(error) = sqlx::query("UPDATE t_post_radiotelephone_train SET duration=?,content=?,pass_number=?,error_number=?,accuracy=?,score=?,end_time=NOW(6),status=2 WHERE id=? AND user_id=?")
        .bind(elapsed as i32)
        .bind(content)
        .bind(correct)
        .bind((total - i64::from(correct)) as i32)
        .bind(accuracy)
        .bind(score)
        .bind(&id)
        .bind(&user.id)
        .execute(&mut *transaction)
        .await
    {
        return database_error(error, "finish radiotelephone training");
    }
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit finish transaction");
    }
    envelope_json(StatusCode::OK, 200, None, "ok", "")
}

pub(crate) async fn details(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body.get("id").and_then(Value::as_str).map(str::to_owned) else {
        return invalid("训练ID不能为空");
    };
    if id.trim().is_empty() {
        return invalid("训练ID不能为空");
    }
    if state.pool.is_none() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    let row = match select_train_pool(&state, &id).await {
        Ok(Some(row)) => row,
        Ok(None) => return invalid("未查询到该训练"),
        Err(error) => return database_error(error, "load radiotelephone training details"),
    };
    if let Some(response) = owner_response(&row, &user.id) {
        return response;
    }
    match train_view(&row, true) {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(message) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, message, ""),
    }
}

pub(crate) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<DeleteQuery>,
) -> Response {
    let Some(id) = query.train_id.filter(|value| !value.trim().is_empty()) else {
        return invalid("训练ID不能为空");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start delete transaction"),
    };
    let row = match sqlx::query(
        "SELECT id,user_id FROM t_post_radiotelephone_train WHERE id=? LIMIT 1 FOR UPDATE",
    )
    .bind(&id)
    .fetch_optional(&mut *transaction)
    .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return invalid("未查询到该训练"),
        Err(error) => return database_error(error, "load radiotelephone training for delete"),
    };
    if row.get::<Option<String>, _>("user_id").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let deleted =
        match sqlx::query("DELETE FROM t_post_radiotelephone_train WHERE id=? AND user_id=?")
            .bind(&id)
            .bind(&user.id)
            .execute(&mut *transaction)
            .await
        {
            Ok(result) => result.rows_affected() == 1,
            Err(error) => return database_error(error, "delete radiotelephone training"),
        };
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit delete transaction");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(deleted)), "ok", "")
}
