use axum::{
    extract::{Extension, Multipart, Query, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde_json::{json, Value};
use sqlx::{MySqlPool, Row};
use std::collections::HashMap;

use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;

const EXERCISE_COLS: &str = "id,type,create_user_id,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') start_time_text,DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') end_time_text,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') create_time_text,accuracy,speed,content,duration,status,name,error_num,correct_num";
const STOCK_COLS: &str = "id,type,name,content,create_user_id,word_size,create_time";
const MAX_PREVIEW_BYTES: usize = 10 * 1024 * 1024;

fn db_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "post entering exercise database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

fn exercise_json(row: &sqlx::mysql::MySqlRow, duration: Option<i32>) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "type": row.get::<Option<i32>, _>("type"),
        "startTime": row.get::<Option<&str>, _>("start_time_text"),
        "endTime": row.get::<Option<&str>, _>("end_time_text"),
        "createTime": row.get::<Option<&str>, _>("create_time_text"),
        "accuracy": row.get::<Option<f64>, _>("accuracy"),
        "speed": row.get::<Option<i32>, _>("speed"),
        "content": row.get::<Option<String>, _>("content"),
        "duration": duration.or_else(|| row.get::<Option<i32>, _>("duration")),
        "status": row.get::<Option<i32>, _>("status"),
        "name": row.get::<Option<String>, _>("name"),
        "correctNum": row.get::<Option<i32>, _>("correct_num"),
        "errorNum": row.get::<Option<i32>, _>("error_num")
    })
}

fn exercise_json_without_content(row: &sqlx::mysql::MySqlRow) -> Value {
    let mut value = exercise_json(row, None);
    if let Some(object) = value.as_object_mut() {
        object.insert("content".into(), Value::Null);
    }
    value
}

fn stock_json(row: &sqlx::mysql::MySqlRow, word_size: Option<i32>) -> Value {
    json!({
        "id": row.get::<i32, _>("id"),
        "name": row.get::<Option<String>, _>("name"),
        "type": row.get::<Option<i32>, _>("type"),
        "content": row.get::<Option<String>, _>("content"),
        "wordSize": word_size.or_else(|| row.get::<Option<i32>, _>("word_size"))
    })
}

macro_rules! request_field {
    ($value:expr) => {
        match $value {
            Ok(value) => value,
            Err(response) => return response,
        }
    };
}

fn request_body(body: &Value) -> Result<(), Response> {
    if body.is_null() {
        Err(envelope_response(
            StatusCode::INTERNAL_SERVER_ERROR,
            500,
            "服务器错误",
            "",
        ))
    } else if !body.is_object() {
        Err(StatusCode::BAD_REQUEST.into_response())
    } else {
        Ok(())
    }
}

fn training_id(body: &Value) -> Result<std::borrow::Cow<'_, str>, Response> {
    request_body(body)?;
    crate::domain::entering::entering::request_string(body.get("id"))?
        .filter(|id| !id.trim().is_empty())
        .ok_or_else(|| envelope_response(StatusCode::OK, 202, "训练ID不能为空", ""))
}

pub(super) fn normalize_stock_lines(content: &str, updating: bool) -> Vec<&str> {
    content
        .split(|character| character == '\r' || character == '\n')
        .flat_map(|line| line.split(if updating { "##" } else { "@" }))
        .filter(|line| !line.is_empty())
        .collect()
}

pub(super) fn english_word_size(content: &str) -> i32 {
    // Java String.split(" ") removes trailing empty fields but retains interior ones.
    if content.is_empty() {
        return 1;
    }
    let content = content.trim_end_matches(' ');
    if content.is_empty() {
        return 0;
    }
    i32::try_from(content.split(' ').count()).unwrap_or(i32::MAX)
}

fn utf16_len(content: &str) -> i32 {
    i32::try_from(content.encode_utf16().count()).unwrap_or(i32::MAX)
}

pub(super) fn stock_word_size(kind: Option<i32>, content: &str) -> i32 {
    if kind == Some(2) {
        english_word_size(content)
    } else {
        utf16_len(content.trim_matches(|character| character <= '\u{20}'))
    }
}
fn default_stock_error(kind: i32) -> Response {
    let name = if kind == 1 {
        "五笔军语"
    } else {
        "拼音军语"
    };
    envelope_response(
        StatusCode::OK,
        202,
        &format!("未配置「{name}」类型（type={kind}）的默认词库，请先在词库中添加该类型的文章"),
        "",
    )
}

pub(super) fn content_array(content: Option<&str>) -> Result<Vec<Value>, &'static str> {
    let Some(content) = content else {
        return Err("训练内容不能为空");
    };
    let content = content.trim_matches([' ', '\t', '\r', '\n']);
    if content.is_empty() || content == "null" {
        return Err("训练内容不能为空");
    }
    serde_json::from_str(content).map_err(|_| "训练内容必须为有效的数组")
}

fn text(value: Option<&Value>) -> Result<String, &'static str> {
    value
        .and_then(Value::as_str)
        .map(ToOwned::to_owned)
        .ok_or("训练内容必须包含有效文本")
}

fn english_display(source: &str) -> String {
    let mut result = String::with_capacity(source.len());
    for value in source.chars() {
        if "!.,?;:'{}[]()@#$%^&*".contains(value) {
            result.push(' ');
        }
        result.push(value);
    }
    result
}

pub(super) fn same_answers(
    stored: Option<&str>,
    submitted: Option<&str>,
) -> Result<bool, &'static str> {
    if submitted.is_none() {
        return Ok(false);
    }
    let previous = content_array(stored)?;
    let current = content_array(submitted)?;
    Ok(previous.len() == current.len()
        && previous.iter().zip(current.iter()).all(|(left, right)| {
            left.is_object()
                && right.is_object()
                && left.get("font") == right.get("font")
                && left.get("value") == right.get("value")
        }))
}

pub(super) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    request_field!(request_body(&body));
    let kind = request_field!(crate::platform::extract::request_integer(body.get("type")));
    let name = request_field!(crate::domain::entering::entering::request_string(
        body.get("name")
    ));
    let word_id = request_field!(crate::platform::extract::request_integer(
        body.get("wordId")
    ));
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(kind) = kind else {
        return envelope_response(StatusCode::OK, 202, "训练类型不能为空", "");
    };
    let content = if kind == 1 || kind == 3 {
        match sqlx::query(
            "SELECT content FROM t_post_entering_exercise_word_stock WHERE type=? LIMIT 1",
        )
        .bind(kind)
        .fetch_optional(pool)
        .await
        {
            Ok(Some(row)) => match row.get::<Option<String>, _>("content") {
                Some(content) => content,
                None => return default_stock_error(kind),
            },
            Ok(None) => return default_stock_error(kind),
            Err(error) => return db_error(error, "find default word stock"),
        }
    } else {
        let Some(word_id) = word_id else {
            return envelope_response(StatusCode::OK, 202, "文章不存在！", "");
        };
        match sqlx::query(
            "SELECT content FROM t_post_entering_exercise_word_stock WHERE id=? LIMIT 1",
        )
        .bind(word_id)
        .fetch_optional(pool)
        .await
        {
            Ok(Some(row)) => match row.get::<Option<String>, _>("content") {
                Some(content) => content,
                None => return envelope_response(StatusCode::OK, 202, "文章不存在！", ""),
            },
            Ok(None) => return envelope_response(StatusCode::OK, 202, "文章不存在！", ""),
            Err(error) => return db_error(error, "find word stock"),
        }
    };
    let id = uuid::Uuid::new_v4().to_string();
    match sqlx::query("INSERT INTO t_post_entering_exercise (id,type,create_user_id,start_time,end_time,create_time,accuracy,speed,content,duration,status,name,error_num,correct_num) VALUES (?,?,?,NULL,NULL,NOW(6),?,?,?,?,?,?,?,?)")
        .bind(&id)
        .bind(kind)
        .bind(&user.id)
        .bind(0.0_f64)
        .bind(0_i32)
        .bind(content)
        .bind(0_i32)
        .bind(0_i32)
        .bind(name.as_deref())
        .bind(0_i32)
        .bind(0_i32)
        .execute(pool)
        .await
    {
        Ok(_) => match sqlx::query(&format!("SELECT {EXERCISE_COLS} FROM t_post_entering_exercise WHERE id=?"))
            .bind(&id)
            .fetch_one(pool)
            .await
        {
            Ok(row) => envelope_json(StatusCode::OK, 200, Some(exercise_json(&row, None)), "ok", ""),
            Err(error) => db_error(error, "reload added exercise"),
        },
        Err(error) => db_error(error, "add exercise"),
    }
}

pub(super) async fn list_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    request_field!(request_body(&body));
    let kind = request_field!(crate::platform::extract::request_integer(body.get("type")));
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(kind) = kind else {
        return envelope_response(StatusCode::OK, 202, "训练类型不能为空", "");
    };
    let predicate = match kind {
        0 => "type > 2",
        1 => "type < 2",
        _ => "type = 2",
    };
    let sql = format!(
        "SELECT {EXERCISE_COLS} FROM t_post_entering_exercise WHERE {predicate} AND create_user_id=? ORDER BY create_time DESC"
    );
    match sqlx::query(&sql).bind(&user.id).fetch_all(pool).await {
        Ok(rows) => envelope_json(
            StatusCode::OK,
            200,
            Some(Value::Array(
                rows.iter().map(exercise_json_without_content).collect(),
            )),
            "ok",
            "",
        ),
        Err(error) => db_error(error, "list exercises"),
    }
}

pub(super) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let id = request_field!(training_id(&body));
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return db_error(error, "begin exercise transaction"),
    };
    let sql = format!("SELECT {EXERCISE_COLS} FROM t_post_entering_exercise WHERE id=? FOR UPDATE");
    let row = match sqlx::query(&sql)
        .bind(id.as_ref())
        .fetch_optional(&mut *transaction)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该训练", ""),
        Err(error) => return db_error(error, "find exercise for begin"),
    };
    if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    match row.get::<Option<i32>, _>("status") {
        Some(2) => return envelope_response(StatusCode::OK, 208, "训练已结束", ""),
        Some(1) => return envelope_response(StatusCode::OK, 200, "ok", ""),
        Some(0) => {}
        _ => return envelope_response(StatusCode::OK, 202, "训练状态无效", ""),
    }
    match sqlx::query("UPDATE t_post_entering_exercise SET status=1,start_time=NOW(6) WHERE id=? AND create_user_id=? AND status=0")
        .bind(id.as_ref())
        .bind(&user.id)
        .execute(&mut *transaction)
        .await
    {
        Ok(_) => match transaction.commit().await {
            Ok(()) => envelope_response(StatusCode::OK, 200, "ok", ""),
            Err(error) => db_error(error, "commit begin exercise"),
        },
        Err(error) => db_error(error, "begin exercise"),
    }
}

pub(super) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let id = request_field!(training_id(&body));
    let submitted_content = request_field!(crate::domain::entering::entering::request_string(
        body.get("content")
    ));
    let accuracy = request_field!(crate::domain::entering::entering::request_double(
        body.get("accuracy")
    ));
    let mut has_metrics = accuracy.is_some();
    for field in ["speed", "duration", "correctNum", "errorNum"] {
        has_metrics |=
            request_field!(crate::platform::extract::request_integer(body.get(field))).is_some();
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return db_error(error, "finish exercise transaction"),
    };
    let sql = format!("SELECT {EXERCISE_COLS},TIMESTAMPDIFF(SECOND,start_time,NOW(6)) AS elapsed_seconds FROM t_post_entering_exercise WHERE id=? FOR UPDATE");
    let row = match sqlx::query(&sql)
        .bind(id.as_ref())
        .fetch_optional(&mut *transaction)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该训练", ""),
        Err(error) => return db_error(error, "find exercise for finish"),
    };
    if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let status = row.get::<Option<i32>, _>("status");
    if status == Some(2) {
        match same_answers(
            row.get::<Option<&str>, _>("content"),
            submitted_content.as_deref(),
        ) {
            Ok(true) => return envelope_response(StatusCode::OK, 200, "ok", ""),
            Ok(false) => {}
            Err(message) => return envelope_response(StatusCode::OK, 202, message, ""),
        }
        return envelope_response(StatusCode::OK, 208, "训练已结束，不能修改结果", "");
    }
    if status != Some(1) || row.get::<Option<&str>, _>("start_time_text").is_none() {
        return envelope_response(StatusCode::OK, 202, "训练尚未开始", "");
    }
    if has_metrics {
        return envelope_response(
            StatusCode::OK,
            202,
            "成绩和用时由服务端计算，请仅提交录入内容",
            "",
        );
    }
    let source = match content_array(row.get::<Option<String>, _>("content").as_deref()) {
        Ok(source) if !source.is_empty() => source,
        Ok(_) => return envelope_response(StatusCode::OK, 202, "训练题面或录入内容无效", ""),
        Err(message) => return envelope_response(StatusCode::OK, 202, message, ""),
    };
    let mut answers = match content_array(submitted_content.as_deref()) {
        Ok(answers) if !answers.is_empty() => answers,
        Ok(_) => return envelope_response(StatusCode::OK, 202, "训练题面或录入内容无效", ""),
        Err(message) => return envelope_response(StatusCode::OK, 202, message, ""),
    };
    let Some(kind) = row.get::<Option<i32>, _>("type") else {
        return envelope_response(StatusCode::OK, 202, "训练题面或录入内容无效", "");
    };
    if !(0..=4).contains(&kind) {
        return envelope_response(StatusCode::OK, 202, "训练题面或录入内容无效", "");
    }
    let terms = kind == 1 || kind == 3;
    let english = kind == 2;
    if terms && source.len() != answers.len() {
        return envelope_response(StatusCode::OK, 202, "录入条目与训练题面不一致", "");
    }
    let mut expected = String::new();
    for item in &source {
        let value = match text(Some(item)) {
            Ok(value) => value,
            Err(message) => return envelope_response(StatusCode::OK, 202, message, ""),
        };
        let rendered = if terms {
            value.to_owned()
        } else if !english {
            value
                .trim_matches(|character| character <= '\u{20}')
                .to_owned()
        } else {
            english_display(value.trim_matches(|character| character <= '\u{20}'))
        };
        expected.push_str(&rendered);
    }
    let mut submitted = String::new();
    let mut input = String::new();
    let mut entered: i64 = 0;
    let mut correct: i64 = 0;
    let mut total: i64 = 0;
    for (index, answer) in answers.iter_mut().enumerate() {
        let Some(object) = answer.as_object() else {
            return envelope_response(StatusCode::OK, 202, "录入条目无效", "");
        };
        let font = match text(object.get("font")) {
            Ok(value) => value,
            Err(message) => return envelope_response(StatusCode::OK, 202, message, ""),
        };
        let value = match text(object.get("value")) {
            Ok(value) => value,
            Err(message) => return envelope_response(StatusCode::OK, 202, message, ""),
        };
        entered += i64::from(utf16_len(&value).min(utf16_len(&font)));
        let is_first = object
            .get("isFirst")
            .and_then(Value::as_bool)
            .unwrap_or(false);
        *answer = json!({"font":font,"value":value,"isFirst":is_first,"trueOrfalse":font == value});
        submitted.push_str(&font);
        input.push_str(&value);
        if terms {
            if index >= source.len() || font != text(source.get(index)).unwrap_or_default() {
                return envelope_response(StatusCode::OK, 202, "录入条目与训练题面不一致", "");
            }
            if font == value {
                correct += 1;
            }
            total += 1;
        }
    }
    if expected.is_empty() || expected != submitted {
        return envelope_response(StatusCode::OK, 202, "录入题面与保存的训练内容不一致", "");
    }
    if english {
        let target: Vec<&str> = expected.split(' ').collect();
        let actual: Vec<&str> = input.split(' ').collect();
        total = i64::try_from(target.len())
            .unwrap_or(i64::MAX)
            .max(i64::try_from(actual.len()).unwrap_or(i64::MAX));
        entered = i64::try_from(target.len())
            .unwrap_or(i64::MAX)
            .min(actual.iter().filter(|word| !word.is_empty()).count() as i64);
        correct = target
            .iter()
            .enumerate()
            .filter(|(index, word)| actual.get(*index) == Some(word))
            .count() as i64;
    } else if !terms {
        let expected_len = expected.encode_utf16().count();
        let input_len = input.encode_utf16().count();
        total = i64::try_from(expected_len.max(input_len)).unwrap_or(i64::MAX);
        correct = expected
            .encode_utf16()
            .zip(input.encode_utf16())
            .filter(|(left, right)| left == right)
            .count() as i64;
    }
    if total <= 0 {
        return envelope_response(StatusCode::OK, 202, "训练题面或录入内容无效", "");
    }
    let elapsed = row.get::<Option<i64>, _>("elapsed_seconds").unwrap_or(-1);
    if elapsed < 0 || elapsed > i64::from(i32::MAX) {
        return envelope_response(StatusCode::OK, 202, "训练开始时间无效", "");
    }
    let accuracy_cents = (correct * 10_000 + total / 2) / total;
    let accuracy = accuracy_cents as f64 / 100.0;
    let speed = (entered.saturating_mul(60) / elapsed.max(1)).min(i64::from(i32::MAX));
    let captured = match serde_json::to_string(&answers) {
        Ok(value) => value,
        Err(error) => {
            return db_error(
                sqlx::Error::Decode(Box::new(error)),
                "serialize exercise answers",
            )
        }
    };
    match sqlx::query("UPDATE t_post_entering_exercise SET accuracy=?,speed=?,duration=?,correct_num=?,error_num=?,content=?,end_time=NOW(6),status=2 WHERE id=? AND create_user_id=? AND status=1")
        .bind(accuracy)
        .bind(speed as i32)
        .bind(elapsed as i32)
        .bind(correct as i32)
        .bind((total - correct) as i32)
        .bind(captured)
        .bind(id.as_ref())
        .bind(&user.id)
        .execute(&mut *transaction)
        .await
    {
        Ok(result) if result.rows_affected() == 1 => match transaction.commit().await {
            Ok(()) => envelope_response(StatusCode::OK, 200, "ok", ""),
            Err(error) => db_error(error, "commit finish exercise"),
        },
        Ok(_) => envelope_response(StatusCode::OK, 208, "训练状态已变化，请重新加载", ""),
        Err(error) => db_error(error, "finish exercise"),
    }
}

pub(super) async fn get_word_stock(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    request_field!(request_body(&body));
    let Some(kind) = request_field!(crate::platform::extract::request_integer(body.get("type")))
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query(&format!(
        "SELECT {STOCK_COLS} FROM t_post_entering_exercise_word_stock WHERE type=? LIMIT 1"
    ))
    .bind(kind)
    .fetch_optional(pool)
    .await
    {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(stock_json(&row, None)), "ok", ""),
        Ok(None) => envelope_json(StatusCode::OK, 200, Some(json!({})), "ok", ""),
        Err(error) => db_error(error, "find word stock by type"),
    }
}

pub(super) async fn get_by_id(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let id = request_field!(training_id(&body));
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = format!("SELECT {EXERCISE_COLS},TIMESTAMPDIFF(SECOND,start_time,NOW(6)) AS elapsed_seconds FROM t_post_entering_exercise WHERE id=? LIMIT 1");
    match sqlx::query(&sql)
        .bind(id.as_ref())
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row))
            if row.get::<Option<String>, _>("create_user_id").as_deref()
                == Some(user.id.as_str()) =>
        {
            let duration = if row.get::<Option<i32>, _>("status") == Some(1)
                && row.get::<Option<&str>, _>("start_time_text").is_some()
            {
                row.get::<Option<i64>, _>("elapsed_seconds")
                    .and_then(|value| i32::try_from(value).ok())
            } else {
                None
            };
            envelope_json(
                StatusCode::OK,
                200,
                Some(exercise_json(&row, duration)),
                "ok",
                "",
            )
        }
        Ok(Some(_)) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Ok(None) => envelope_response(StatusCode::OK, 202, "未查询到该训练", ""),
        Err(error) => db_error(error, "find exercise by id"),
    }
}

pub(super) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<HashMap<String, String>>,
) -> Response {
    let Some(id) = query.get("trainId").filter(|id| !id.trim().is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return db_error(error, "begin delete exercise"),
    };
    let row = match sqlx::query(
        "SELECT create_user_id FROM t_post_entering_exercise WHERE id=? FOR UPDATE",
    )
    .bind(id)
    .fetch_optional(&mut *transaction)
    .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该训练", ""),
        Err(error) => return db_error(error, "lock exercise for delete"),
    };
    if row.get::<Option<&str>, _>("create_user_id") != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let result = match sqlx::query("DELETE FROM t_post_entering_exercise WHERE id=?")
        .bind(id)
        .execute(&mut *transaction)
        .await
    {
        Ok(result) => result,
        Err(error) => return db_error(error, "delete exercise"),
    };
    match transaction.commit().await {
        Ok(()) => envelope_json(
            StatusCode::OK,
            200,
            Some(json!(result.rows_affected() == 1)),
            "ok",
            "",
        ),
        Err(error) => db_error(error, "commit delete exercise"),
    }
}

pub(super) async fn word_stock_add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    request_field!(request_body(&body));
    let kind = request_field!(crate::platform::extract::request_integer(body.get("type")));
    let name = request_field!(crate::domain::entering::entering::request_string(
        body.get("name")
    ));
    let id = request_field!(crate::platform::extract::request_integer(body.get("id")));
    request_field!(crate::platform::extract::request_integer(
        body.get("wordSize")
    ));
    let raw_content = request_field!(crate::domain::entering::entering::request_string(
        body.get("content")
    ));
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(raw_content) = raw_content else {
        return envelope_response(StatusCode::OK, 202, "内容不能为空", "");
    };
    let lines = normalize_stock_lines(&raw_content, id.is_some());
    let content = match serde_json::to_string(&lines) {
        Ok(content) => content,
        Err(error) => {
            return db_error(sqlx::Error::Decode(Box::new(error)), "serialize word stock")
        }
    };
    let word_size = stock_word_size(kind, &raw_content);
    if let Some(id) = id {
        match sqlx::query(
            "UPDATE t_post_entering_exercise_word_stock SET name=?,type=?,content=? WHERE id=?",
        )
        .bind(name.as_deref())
        .bind(kind)
        .bind(content)
        .bind(id)
        .execute(pool)
        .await
        {
            Ok(result) if result.rows_affected() == 1 => match sqlx::query(&format!(
                "SELECT {STOCK_COLS} FROM t_post_entering_exercise_word_stock WHERE id=?"
            ))
            .bind(id)
            .fetch_one(pool)
            .await
            {
                Ok(row) => {
                    envelope_json(StatusCode::OK, 200, Some(stock_json(&row, None)), "ok", "")
                }
                Err(error) => db_error(error, "reload updated word stock"),
            },
            Ok(_) => envelope_response(StatusCode::OK, 202, "未查询到该词库", ""),
            Err(error) => db_error(error, "update word stock"),
        }
    } else {
        match sqlx::query("INSERT INTO t_post_entering_exercise_word_stock (type,name,content,create_user_id,word_size,create_time) VALUES (?,?,?,?,?,NOW(6))")
            .bind(kind).bind(name.as_deref()).bind(content).bind(&user.id).bind(word_size).execute(pool).await
        {
            Ok(result) => {
                let id = result.last_insert_id() as i32;
                match sqlx::query(&format!("SELECT {STOCK_COLS} FROM t_post_entering_exercise_word_stock WHERE id=?"))
                    .bind(id).fetch_one(pool).await
                {
                    Ok(row) => envelope_json(StatusCode::OK, 200, Some(stock_json(&row, None)), "ok", ""),
                    Err(error) => db_error(error, "reload added word stock"),
                }
            }
            Err(error) => db_error(error, "add word stock"),
        }
    }
}

pub(super) async fn word_stock_view(mut multipart: Multipart) -> Response {
    loop {
        match multipart.next_field().await {
            Ok(Some(field)) if field.name() == Some("file") => {
                let file_name = field.file_name().unwrap_or("").to_owned();
                let lower_name = file_name.to_ascii_lowercase();
                if ![".txt", ".md", ".csv"]
                    .iter()
                    .any(|suffix| lower_name.ends_with(suffix))
                {
                    return envelope_response(
                        StatusCode::OK,
                        202,
                        "仅支持 UTF-8 纯文本文件（txt/md/csv）",
                        "",
                    );
                }
                let bytes = match field.bytes().await {
                    Ok(bytes) => bytes,
                    Err(_) => {
                        return envelope_response(StatusCode::OK, 202, "上传文件读取失败", "")
                    }
                };
                if bytes.is_empty() {
                    return envelope_response(StatusCode::OK, 202, "文档内容为空", "");
                }
                if bytes.len() > MAX_PREVIEW_BYTES {
                    return envelope_response(StatusCode::OK, 202, "上传文件不能超过10MiB", "");
                }
                let content = match std::str::from_utf8(&bytes) {
                    Ok(content) => content,
                    Err(_) => {
                        return envelope_response(
                            StatusCode::OK,
                            202,
                            "文档不是 UTF-8 纯文本，无法读取",
                            "",
                        )
                    }
                };
                return envelope_json(
                    StatusCode::OK,
                    200,
                    Some(json!({
                        "name": file_name,
                        "content": content,
                        "wordSize": stock_word_size(None, &content)
                    })),
                    "ok",
                    "",
                );
            }
            Ok(Some(_)) => continue,
            Ok(None) => return envelope_response(StatusCode::OK, 202, "未收到上传文件", ""),
            Err(_) => return envelope_response(StatusCode::OK, 202, "上传请求格式无效", ""),
        }
    }
}

async fn backfilled_stock(pool: &MySqlPool, id: i32) -> Result<Option<Value>, Response> {
    let mut transaction = pool
        .begin()
        .await
        .map_err(|error| db_error(error, "begin stock size backfill"))?;
    let result = async {
        // Catalog edits and deletes also mutate this PRIMARY row. Derive the lazy value only
        // after a current locking read, never from the earlier list snapshot.
        let row = sqlx::query(&format!(
            "SELECT {STOCK_COLS} FROM t_post_entering_exercise_word_stock WHERE id=? FOR UPDATE"
        ))
        .bind(id)
        .fetch_optional(&mut *transaction)
        .await
        .map_err(|error| db_error(error, "lock stock for size backfill"))?;
        let Some(row) = row else {
            return Ok(None);
        };
        let size = match row.get::<Option<i32>, _>("word_size") {
            Some(size) => size,
            None => {
                let raw = row
                    .get::<Option<&str>, _>("content")
                    .ok_or_else(|| envelope_response(StatusCode::OK, 500, "服务器错误", ""))?;
                let parsed: Vec<String> = serde_json::from_str(raw)
                    .map_err(|_| envelope_response(StatusCode::OK, 500, "服务器错误", ""))?;
                let joined: String = parsed.into_iter().collect();
                let size = stock_word_size(row.get::<Option<i32>, _>("type"), &joined);
                sqlx::query(
                    "UPDATE t_post_entering_exercise_word_stock SET word_size=? WHERE id=?",
                )
                .bind(size)
                .bind(id)
                .execute(&mut *transaction)
                .await
                .map_err(|error| db_error(error, "backfill word stock size"))?;
                size
            }
        };
        Ok(Some(stock_json(&row, Some(size))))
    }
    .await;
    match result {
        Ok(value) => {
            transaction
                .commit()
                .await
                .map_err(|error| db_error(error, "commit stock size backfill"))?;
            Ok(value)
        }
        Err(response) => {
            transaction
                .rollback()
                .await
                .map_err(|error| db_error(error, "rollback stock size backfill"))?;
            Err(response)
        }
    }
}

pub(super) async fn word_stock_list_page(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    request_field!(request_body(&body));
    let kind = request_field!(crate::platform::extract::request_integer(body.get("type")));
    let (sql, bind_kind) = if kind.is_some() {
        (format!("SELECT {STOCK_COLS} FROM t_post_entering_exercise_word_stock WHERE type=? ORDER BY create_time DESC"), true)
    } else {
        (format!("SELECT {STOCK_COLS} FROM t_post_entering_exercise_word_stock WHERE type IN (0,1,2) ORDER BY create_time DESC"), false)
    };
    let result = if bind_kind {
        sqlx::query(&sql).bind(kind).fetch_all(pool).await
    } else {
        sqlx::query(&sql).fetch_all(pool).await
    };
    let rows = match result {
        Ok(rows) => rows,
        Err(error) => return db_error(error, "list word stocks"),
    };
    let mut result = Vec::with_capacity(rows.len());
    for row in rows {
        if row.get::<Option<i32>, _>("word_size").is_none() {
            if let Some(value) = request_field!(backfilled_stock(pool, row.get("id")).await) {
                result.push(value);
            }
        } else {
            result.push(stock_json(&row, None));
        }
    }
    envelope_json(StatusCode::OK, 200, Some(Value::Array(result)), "ok", "")
}

pub(super) async fn word_stock_delete(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    request_field!(request_body(&body));
    let Some(id) = request_field!(crate::platform::extract::request_integer(body.get("id"))) else {
        return envelope_response(StatusCode::OK, 202, "词库ID不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("DELETE FROM t_post_entering_exercise_word_stock WHERE id=?")
        .bind(id)
        .execute(pool)
        .await
    {
        Ok(_) => envelope_response(StatusCode::OK, 200, "ok", ""),
        Err(error) => db_error(error, "delete word stock"),
    }
}
