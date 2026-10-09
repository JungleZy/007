use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rust_decimal::{prelude::ToPrimitive, Decimal, RoundingStrategy};
use serde::{Serialize, Serializer};
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, MySql, MySqlPool, Row, Transaction};
use std::{fmt, str::FromStr};
use uuid::Uuid;

const TRAIN_SELECT: &str = "SELECT id,title,status,duration,total_number,error_number,accuracy,speed,create_user_id,message_type,content,protocol_version,accumulated_active_millis,source_content,IF(started_at IS NULL,0,1) AS clock_running,CAST(TIMESTAMPDIFF(MICROSECOND,started_at,NOW(6)) DIV 1000 AS SIGNED) AS elapsed_ms FROM t_telegraph_key_pat_synthetical_train WHERE id=? LIMIT 1";
const TRAIN_SELECT_LOCKED: &str = "SELECT id,title,status,duration,total_number,error_number,accuracy,speed,create_user_id,message_type,content,protocol_version,accumulated_active_millis,source_content,IF(started_at IS NULL,0,1) AS clock_running,CAST(TIMESTAMPDIFF(MICROSECOND,started_at,NOW(6)) DIV 1000 AS SIGNED) AS elapsed_ms FROM t_telegraph_key_pat_synthetical_train WHERE id=? LIMIT 1 FOR UPDATE";
const TRAIN_NOT_FOUND: &str = "未查询到该场训练";

#[derive(Debug)]
enum Error {
    Parameter(String),
    Terminal(String),
    Forbidden(String),
    Database(sqlx::Error),
    Internal(String),
}

impl fmt::Display for Error {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Parameter(message)
            | Self::Terminal(message)
            | Self::Forbidden(message)
            | Self::Internal(message) => formatter.write_str(message),
            Self::Database(error) => write!(formatter, "{error}"),
        }
    }
}

impl From<sqlx::Error> for Error {
    fn from(error: sqlx::Error) -> Self {
        Self::Database(error)
    }
}

fn parameter(message: impl Into<String>) -> Error {
    Error::Parameter(message.into())
}

fn terminal(message: impl Into<String>) -> Error {
    Error::Terminal(message.into())
}

fn database_error(error: Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "synthetical training operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

fn respond(result: Result<Value, Error>) -> Response {
    match result {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(Error::Parameter(message)) => {
            envelope_response(StatusCode::OK, 202, &message, &message)
        }
        Err(Error::Terminal(message)) => envelope_response(StatusCode::OK, 208, &message, &message),
        Err(Error::Forbidden(message)) => {
            tracing::warn!(reason = %message, "synthetical training access denied");
            envelope_response(StatusCode::OK, 207, "无权限", "")
        }
        Err(error) => database_error(error, "synthetical training request"),
    }
}

fn pool(state: &AppState) -> Result<&MySqlPool, Error> {
    state
        .pool
        .as_ref()
        .ok_or_else(|| Error::Internal("数据库未配置".into()))
}

fn field_string<'a>(body: &'a Value, name: &str) -> Result<Option<&'a str>, Error> {
    match body.get(name) {
        None | Some(Value::Null) => Ok(None),
        Some(Value::String(value)) => Ok(Some(value)),
        Some(_) => Err(parameter(format!("{name}必须为文本"))),
    }
}

fn required_id(body: &Value) -> Result<&str, Error> {
    field_string(body, "id")?
        .filter(|value| !value.trim().is_empty())
        .ok_or_else(|| parameter(TRAIN_NOT_FOUND))
}

fn required_content(body: &Value) -> Result<&str, Error> {
    field_string(body, "content")?.ok_or_else(|| parameter("报文内容不能为空"))
}

fn required_message_type(body: &Value) -> Result<i32, Error> {
    let value = body
        .get("messageType")
        .and_then(Value::as_i64)
        .ok_or_else(|| parameter("训练报文类型无效"))?;
    i32::try_from(value)
        .ok()
        .filter(|value| (0..=2).contains(value))
        .ok_or_else(|| parameter("训练报文类型无效"))
}

async fn lock_user(tx: &mut Transaction<'_, MySql>, user_id: &str) -> Result<(), Error> {
    if !crate::statistical::lock_user(tx, user_id).await? {
        return Err(parameter("训练属主不存在"));
    }
    Ok(())
}

async fn find_train(pool: &MySqlPool, id: &str) -> Result<Option<MySqlRow>, Error> {
    Ok(sqlx::query(TRAIN_SELECT)
        .bind(id)
        .fetch_optional(pool)
        .await?)
}

async fn find_train_locked(
    tx: &mut Transaction<'_, MySql>,
    id: &str,
) -> Result<Option<MySqlRow>, Error> {
    Ok(sqlx::query(TRAIN_SELECT_LOCKED)
        .bind(id)
        .fetch_optional(&mut **tx)
        .await?)
}

fn owner_of(row: &MySqlRow) -> Result<String, Error> {
    row.get::<Option<String>, _>("create_user_id")
        .ok_or_else(|| parameter("训练属主不存在"))
}

async fn authorized_locked(
    tx: &mut Transaction<'_, MySql>,
    user: &AuthUser,
    id: &str,
) -> Result<MySqlRow, Error> {
    let initial = sqlx::query(TRAIN_SELECT)
        .bind(id)
        .fetch_optional(&mut **tx)
        .await?;
    let initial = initial.ok_or_else(|| parameter(TRAIN_NOT_FOUND))?;
    let owner = initial.try_get::<Option<String>, _>("create_user_id")?;
    if owner.as_deref() != Some(user.id.as_str()) && !user.is_admin {
        return Err(Error::Forbidden(format!("非创建者访问电子键综合训练 {id}")));
    }
    let owner = owner.ok_or_else(|| parameter("id to load is required for loading"))?;

    // Every train mutation takes the owner row before taking the train row.
    lock_user(tx, &owner).await?;
    let refreshed = find_train_locked(tx, id).await?;
    refreshed.ok_or_else(|| parameter(TRAIN_NOT_FOUND))
}

fn protocol_guard(row: &MySqlRow) -> Result<(), Error> {
    if row.get::<Option<i32>, _>("protocol_version") != Some(1)
        || row.get::<Option<String>, _>("source_content").is_none()
        || row
            .get::<Option<i64>, _>("accumulated_active_millis")
            .is_none()
    {
        return Err(terminal(
            "历史训练缺少权威时间轴，请新建训练；历史成绩保持不变",
        ));
    }
    Ok(())
}

fn checked_elapsed(row: &MySqlRow) -> Result<i64, Error> {
    let accumulated = row
        .get::<Option<i64>, _>("accumulated_active_millis")
        .ok_or_else(|| terminal("历史训练缺少权威时间轴，请新建训练；历史成绩保持不变"))?;
    if accumulated < 0 {
        return Err(parameter("训练时间轴异常"));
    }
    let running = row.get::<i64, _>("clock_running") != 0;
    let wall_clock = if running {
        row.get::<Option<i64>, _>("elapsed_ms")
            .ok_or_else(|| parameter("训练时间轴异常"))?
    } else {
        0
    };
    if wall_clock < 0 {
        return Err(parameter("训练时间轴异常"));
    }
    accumulated
        .checked_add(wall_clock)
        .ok_or_else(|| parameter("训练时间轴异常"))
}

fn view(row: &MySqlRow) -> Result<Value, Error> {
    let protocol = row.get::<Option<i32>, _>("protocol_version");
    let accumulated = row.get::<Option<i64>, _>("accumulated_active_millis");
    let duration = if protocol == Some(1) && accumulated.is_some() {
        Some((checked_elapsed(row)? / 1_000).to_string())
    } else {
        row.get::<Option<String>, _>("duration")
    };
    Ok(json!({
        "id": row.get::<String, _>("id"),
        "title": row.get::<Option<String>, _>("title"),
        "status": row.get::<Option<i32>, _>("status"),
        "duration": duration,
        "totalNumber": row.get::<Option<i32>, _>("total_number"),
        "messageType": row.get::<Option<i32>, _>("message_type"),
        "errorNumber": row.get::<Option<i32>, _>("error_number"),
        "accuracy": row.get::<Option<f64>, _>("accuracy"),
        "speed": row.get::<Option<String>, _>("speed"),
        "content": row.get::<Option<String>, _>("content")
    }))
}

fn parse_array(json_text: Option<&str>) -> Result<Vec<Value>, Error> {
    let json_text = json_text.ok_or_else(|| parameter("报文内容不能为空"))?;
    serde_json::from_str(json_text).map_err(|_| parameter("报文必须为有效的数组"))
}

fn text<'a>(value: Option<&'a Value>) -> Result<&'a str, Error> {
    value
        .and_then(Value::as_str)
        .ok_or_else(|| parameter("报文和答案必须为文本"))
}

// Content is a JSON string on the wire; retain Java's row field order within it.
struct Content<'a>(&'a [Value]);

impl Serialize for Content<'_> {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        #[derive(Serialize)]
        struct Row<'a> {
            text: &'a Value,
            value: &'a Value,
            #[serde(rename = "type")]
            correct: &'a Value,
            #[serde(rename = "isFocus")]
            focused: &'a Value,
        }
        serializer.collect_seq(self.0.iter().map(|row| Row {
            text: &row["text"],
            value: &row["value"],
            correct: &row["type"],
            focused: &row["isFocus"],
        }))
    }
}

fn initial_source(body: &Value) -> Result<String, Error> {
    let source = parse_array(field_string(body, "content")?)?;
    if source.is_empty() {
        return Err(parameter("训练报文不能为空"));
    }
    let mut initial = Vec::with_capacity(source.len());
    for item in source {
        let object = item
            .as_object()
            .ok_or_else(|| parameter("报文行必须为对象"))?;
        let expected = text(object.get("text"))?;
        if expected.is_empty() {
            return Err(parameter("训练报文不能为空"));
        }
        initial.push(json!({
            "text": expected,
            "value": "",
            "type": true,
            "isFocus": false
        }));
    }
    serde_json::to_string(&Content(&initial)).map_err(|error| Error::Internal(error.to_string()))
}

fn answers(row: &MySqlRow, content: &str) -> Result<Value, Error> {
    let source = parse_array(row.get::<Option<String>, _>("source_content").as_deref())?;
    let submitted = parse_array(Some(content))?;
    if source.len() != submitted.len() {
        return Err(parameter("答案数量与报文不一致"));
    }

    let mut normalized = Vec::with_capacity(source.len());
    for (source_item, submitted_item) in source.iter().zip(submitted.iter()) {
        let source_object = source_item
            .as_object()
            .ok_or_else(|| parameter("报文行必须为对象"))?;
        let expected = text(source_object.get("text"))?;
        let answer_object = submitted_item
            .as_object()
            .ok_or_else(|| parameter("报文行必须为对象"))?;
        if expected != text(answer_object.get("text"))? {
            return Err(parameter("报文源文本不匹配"));
        }
        let value = text(answer_object.get("value"))?;
        let focus = match answer_object.get("isFocus") {
            None => false,
            Some(Value::Bool(value)) => *value,
            Some(_) => return Err(parameter("报文完成标记无效")),
        };
        let focused = !value.is_empty() || focus;
        normalized.push(json!({
            "text": expected,
            "value": value,
            "type": !focused || expected == value,
            "isFocus": focused
        }));
    }
    Ok(Value::Array(normalized))
}

fn require_same_answers(row: &MySqlRow, normalized: &Value) -> Result<(), Error> {
    let saved = parse_array(row.get::<Option<String>, _>("content").as_deref())?;
    if Value::Array(saved) != *normalized {
        return Err(terminal("当前结果已保存，不能修改"));
    }
    Ok(())
}

fn utf16_length(value: &str) -> usize {
    value.encode_utf16().count()
}

fn metrics(normalized: &Value, elapsed_millis: i64) -> Result<(i32, i32, f64, String), Error> {
    if elapsed_millis < 0 {
        return Err(parameter("训练时间轴异常"));
    }
    let rows = normalized
        .as_array()
        .ok_or_else(|| parameter("报文必须为有效的数组"))?;
    let mut errors: i32 = 0;
    let mut answered: i64 = 0;
    let mut characters: i64 = 0;
    for item in rows {
        let object = item
            .as_object()
            .ok_or_else(|| parameter("报文行必须为对象"))?;
        let expected = text(object.get("text"))?;
        let value = text(object.get("value"))?;
        let entered = utf16_length(value).min(utf16_length(expected));
        characters = characters
            .checked_add(i64::try_from(entered).map_err(|_| parameter("成绩超出有效范围"))?)
            .ok_or_else(|| parameter("成绩超出有效范围"))?;
        if object.get("isFocus") == Some(&Value::Bool(true)) {
            answered = answered
                .checked_add(1)
                .ok_or_else(|| parameter("成绩超出有效范围"))?;
            if expected != value {
                errors = errors
                    .checked_add(1)
                    .ok_or_else(|| parameter("成绩超出有效范围"))?;
            }
        }
    }
    let accuracy = if answered == 0 {
        0.0
    } else {
        (Decimal::from(answered - i64::from(errors)) * Decimal::from(100_i64)
            / Decimal::from(answered))
        .round_dp_with_strategy(2, RoundingStrategy::MidpointAwayFromZero)
        .to_f64()
        .ok_or_else(|| parameter("成绩超出有效范围"))?
    };
    let speed = if elapsed_millis == 0 {
        "0".to_owned()
    } else {
        let speed = (Decimal::from(characters) * Decimal::from(60_000_i64)
            / Decimal::from(elapsed_millis))
        .round_dp_with_strategy(2, RoundingStrategy::MidpointAwayFromZero);
        format!("{speed:.2}")
    };
    let total = i32::try_from(rows.len()).map_err(|_| parameter("报文数量超出有效范围"))?;
    Ok((total, errors, accuracy, speed))
}

async fn update_capture(
    tx: &mut Transaction<'_, MySql>,
    id: &str,
    status: i32,
    row: &MySqlRow,
    normalized: &Value,
) -> Result<(), Error> {
    let elapsed = checked_elapsed(row)?;
    let (total, errors, accuracy, speed) = metrics(normalized, elapsed)?;
    let duration = (elapsed / 1_000).to_string();
    let rows = normalized
        .as_array()
        .ok_or_else(|| parameter("报文必须为有效的数组"))?;
    let content = serde_json::to_string(&Content(rows))
        .map_err(|error| Error::Internal(error.to_string()))?;
    sqlx::query("UPDATE t_telegraph_key_pat_synthetical_train SET accumulated_active_millis=?,started_at=NULL,duration=?,content=?,total_number=?,error_number=?,accuracy=?,speed=?,status=? WHERE id=?")
        .bind(elapsed)
        .bind(duration)
        .bind(content)
        .bind(total)
        .bind(errors)
        .bind(accuracy)
        .bind(speed)
        .bind(status)
        .bind(id)
        .execute(&mut **tx)
        .await?;
    Ok(())
}

async fn recompute_statistics(
    tx: &mut Transaction<'_, MySql>,
    owner_id: &str,
) -> Result<(), Error> {
    // The owner lock serializes every settlement; historical rows need no locking scan.
    let rows = sqlx::query("SELECT CAST(CASE WHEN protocol_version=1 THEN accumulated_active_millis ELSE duration END AS CHAR) AS total_time_value,speed FROM t_telegraph_key_pat_synthetical_train WHERE create_user_id=? AND status=3")
        .bind(owner_id)
        .fetch_all(&mut **tx)
        .await?;
    let mut total_time = Decimal::ZERO;
    let mut total_speed = Decimal::ZERO;
    for row in &rows {
        let time = row
            .get::<Option<String>, _>("total_time_value")
            .unwrap_or_else(|| "0".to_owned());
        let speed = row
            .get::<Option<String>, _>("speed")
            .unwrap_or_else(|| "0".to_owned());
        total_time = total_time
            .checked_add(
                Decimal::from_str(&time)
                    .map_err(|_| Error::Internal("训练时长不是有效数字".into()))?,
            )
            .ok_or_else(|| Error::Internal("训练时长超出有效范围".into()))?;
        total_speed = total_speed
            .checked_add(
                Decimal::from_str(&speed)
                    .map_err(|_| Error::Internal("训练速率不是有效数字".into()))?,
            )
            .ok_or_else(|| Error::Internal("训练速率超出有效范围".into()))?;
    }
    let average = if rows.is_empty() {
        Decimal::ZERO
    } else {
        (total_speed / Decimal::from(rows.len() as i64))
            .round_dp_with_strategy(2, RoundingStrategy::MidpointAwayFromZero)
    };
    let total_count =
        i32::try_from(rows.len()).map_err(|_| Error::Internal("训练次数超出有效范围".into()))?;
    let total_time = total_time.to_string();

    let existing = sqlx::query(
        "SELECT id FROM t_telegraph_key_train_statistical WHERE user_id=? AND type=2 LIMIT 1",
    )
    .bind(owner_id)
    .fetch_optional(&mut **tx)
    .await?;
    if let Some(row) = existing {
        let id = row.get::<String, _>("id");
        sqlx::query("UPDATE t_telegraph_key_train_statistical SET total_count=?,total_time=?,avg_speed=? WHERE id=?")
            .bind(total_count)
            .bind(total_time)
            .bind(average)
            .bind(id)
            .execute(&mut **tx)
            .await?;
    } else {
        sqlx::query("INSERT INTO t_telegraph_key_train_statistical (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,2,?,?,?,?)")
            .bind(Uuid::new_v4().to_string())
            .bind(total_count)
            .bind(total_time)
            .bind(average)
            .bind(owner_id)
            .execute(&mut **tx)
            .await?;
    }
    Ok(())
}

fn decimal_value(value: Option<Decimal>) -> Result<Value, Error> {
    match value {
        None => Ok(Value::Null),
        Some(value) => value
            .to_string()
            .parse::<serde_json::Number>()
            .map(Value::Number)
            .map_err(|_| Error::Internal("统计速率不是有效数字".into())),
    }
}

fn statistic_view(row: &MySqlRow) -> Result<Value, Error> {
    Ok(json!({
        "id": row.get::<String, _>("id"),
        "type": row.get::<Option<i32>, _>("type"),
        "totalCount": row.get::<Option<i32>, _>("total_count"),
        "totalTime": row.get::<Option<String>, _>("total_time"),
        "avgSpeed": decimal_value(row.get::<Option<Decimal>, _>("avg_speed"))?,
        "userId": row.get::<Option<String>, _>("user_id")
    }))
}

pub(crate) async fn find_all(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let result = async {
        let pool = pool(&state)?;
        let rows = sqlx::query("SELECT id,title,status,duration,total_number,error_number,accuracy,speed,create_user_id,message_type,content,protocol_version,accumulated_active_millis,source_content,IF(started_at IS NULL,0,1) AS clock_running,CAST(TIMESTAMPDIFF(MICROSECOND,started_at,NOW(6)) DIV 1000 AS SIGNED) AS elapsed_ms FROM t_telegraph_key_pat_synthetical_train WHERE create_user_id=? ORDER BY create_time DESC")
            .bind(&user.id)
            .fetch_all(pool)
            .await?;
        rows.iter().map(view).collect::<Result<Vec<_>, Error>>().map(Value::Array)
    }
    .await;
    respond(result)
}

pub(crate) async fn find_by_id(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let result = async {
        let id = required_id(&body)?;
        let pool = pool(&state)?;
        let row = find_train(pool, id)
            .await?
            .ok_or_else(|| parameter(TRAIN_NOT_FOUND))?;
        let owner = row.try_get::<Option<String>, _>("create_user_id")?;
        if owner.as_deref() != Some(user.id.as_str()) && !user.is_admin {
            return Err(Error::Forbidden(format!("非创建者访问电子键综合训练 {id}")));
        }
        view(&row)
    }
    .await;
    respond(result)
}

pub(crate) async fn last_train(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let result = async {
        let pool = pool(&state)?;
        let row = sqlx::query("SELECT id,title,status,duration,total_number,error_number,accuracy,speed,create_user_id,message_type,content,protocol_version,accumulated_active_millis,source_content,IF(started_at IS NULL,0,1) AS clock_running,CAST(TIMESTAMPDIFF(MICROSECOND,started_at,NOW(6)) DIV 1000 AS SIGNED) AS elapsed_ms FROM t_telegraph_key_pat_synthetical_train WHERE create_user_id=? ORDER BY create_time DESC LIMIT 1")
            .bind(&user.id)
            .fetch_optional(pool)
            .await?;
        Ok(row.as_ref().map(view).transpose()?.unwrap_or(Value::Null))
    }
    .await;
    respond(result)
}

pub(crate) async fn save(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let result = async {
        if !body.is_object() {
            return Err(parameter("训练参数不能为空"));
        }
        let pool = pool(&state)?;
        let id = field_string(&body, "id")?;
        let mut tx = pool.begin().await?;
        let outcome = async {
            if let Some(id) = id.filter(|value| !value.trim().is_empty()) {
                let row = authorized_locked(&mut tx, &user, id).await?;
                protocol_guard(&row)?;
                if row.get::<Option<i32>, _>("status") != Some(0) {
                    return Err(terminal("训练已开始，不能修改配置"));
                }
                if let Some(title) = field_string(&body, "title")? {
                    sqlx::query("UPDATE t_telegraph_key_pat_synthetical_train SET title=? WHERE id=?")
                        .bind(title)
                        .bind(id)
                        .execute(&mut *tx)
                        .await?;
                }
                let row = find_train_locked(&mut tx, id)
                    .await?
                    .ok_or_else(|| parameter(TRAIN_NOT_FOUND))?;
                view(&row)
            } else {
                let message_type = required_message_type(&body)?;
                let source = initial_source(&body)?;
                lock_user(&mut tx, &user.id).await?;
                let last_id: Option<String> = sqlx::query_scalar("SELECT id FROM t_telegraph_key_pat_synthetical_train WHERE create_user_id=? ORDER BY create_time DESC,id DESC LIMIT 1")
                    .bind(&user.id)
                    .fetch_optional(&mut *tx)
                    .await?;
                let last = match last_id {
                    Some(id) => find_train_locked(&mut tx, &id).await?,
                    None => None,
                };
                if let Some(last) = last {
                    let last_id = last.get::<String, _>("id");
                    match last.get::<Option<i32>, _>("status") {
                        Some(2) => {
                            if last.get::<Option<i32>, _>("protocol_version") == Some(1)
                                && last.get::<Option<String>, _>("source_content").is_some()
                                && last.get::<Option<i64>, _>("accumulated_active_millis").is_some()
                            {
                                sqlx::query("UPDATE t_telegraph_key_pat_synthetical_train SET status=3 WHERE id=?")
                                    .bind(&last_id)
                                    .execute(&mut *tx)
                                    .await?;
                                recompute_statistics(&mut tx, &user.id).await?;
                            } else {
                                sqlx::query("UPDATE t_telegraph_key_pat_synthetical_train SET status=3 WHERE id=?")
                                    .bind(&last_id)
                                    .execute(&mut *tx)
                                    .await?;
                            }
                        }
                        Some(0) => {
                            sqlx::query("DELETE FROM t_telegraph_key_pat_synthetical_train WHERE id=?")
                                .bind(&last_id)
                                .execute(&mut *tx)
                                .await?;
                        }
                        _ => {}
                    }
                }
                let id = Uuid::new_v4().to_string();
                sqlx::query("INSERT INTO t_telegraph_key_pat_synthetical_train (id,title,status,duration,total_number,error_number,accuracy,speed,create_user_id,message_type,content,protocol_version,accumulated_active_millis,source_content,create_time) VALUES (?, ?, 0, '0', ?, 0, 0, '0', ?, ?, ?, 1, 0, ?, NOW(6))")
                    .bind(&id)
                    .bind(field_string(&body, "title")?)
                    .bind(i32::try_from(parse_array(Some(&source))?.len()).map_err(|_| parameter("报文数量超出有效范围"))?)
                    .bind(&user.id)
                    .bind(message_type)
                    .bind(&source)
                    .bind(&source)
                    .execute(&mut *tx)
                    .await?;
                let row = find_train_locked(&mut tx, &id)
                    .await?
                    .ok_or_else(|| parameter(TRAIN_NOT_FOUND))?;
                view(&row)
            }
        }
        .await;
        match outcome {
            Ok(value) => {
                tx.commit().await?;
                Ok(value)
            }
            Err(error) => {
                let _ = tx.rollback().await;
                Err(error)
            }
        }
    }
    .await;
    respond(result)
}

pub(crate) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let result = async {
        let id = required_id(&body)?;
        let pool = pool(&state)?;
        let mut tx = pool.begin().await?;
        let outcome = async {
            let row = authorized_locked(&mut tx, &user, id).await?;
            protocol_guard(&row)?;
            match row.get::<Option<i32>, _>("status") {
                Some(1) => view(&row),
                Some(0) => {
                    sqlx::query("UPDATE t_telegraph_key_pat_synthetical_train SET status=1,started_at=NOW(6) WHERE id=?")
                        .bind(id)
                        .execute(&mut *tx)
                        .await?;
                    let row = find_train_locked(&mut tx, id)
                        .await?
                        .ok_or_else(|| parameter(TRAIN_NOT_FOUND))?;
                    view(&row)
                }
                _ => Err(terminal("训练已开始或已完成，请读取当前状态")),
            }
        }
        .await;
        match outcome {
            Ok(value) => {
                tx.commit().await?;
                Ok(value)
            }
            Err(error) => {
                let _ = tx.rollback().await;
                Err(error)
            }
        }
    }
    .await;
    respond(result)
}

pub(crate) async fn stop(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let result = async {
        let id = required_id(&body)?;
        let pool = pool(&state)?;
        let mut tx = pool.begin().await?;
        let outcome = async {
            let row = authorized_locked(&mut tx, &user, id).await?;
            protocol_guard(&row)?;
            let content = required_content(&body)?;
            let normalized = answers(&row, content)?;
            if row.get::<Option<i32>, _>("status") == Some(2) {
                require_same_answers(&row, &normalized)?;
                return view(&row);
            }
            if row.get::<Option<i32>, _>("status") == Some(3) {
                return Err(terminal("训练已完成"));
            }
            if row.get::<Option<i32>, _>("status") != Some(1)
                || row.get::<i64, _>("clock_running") == 0
            {
                return Err(parameter("训练尚未开始或已暂停"));
            }
            update_capture(&mut tx, id, 2, &row, &normalized).await?;
            let row = find_train_locked(&mut tx, id)
                .await?
                .ok_or_else(|| parameter(TRAIN_NOT_FOUND))?;
            view(&row)
        }
        .await;
        match outcome {
            Ok(value) => {
                tx.commit().await?;
                Ok(value)
            }
            Err(error) => {
                let _ = tx.rollback().await;
                Err(error)
            }
        }
    }
    .await;
    respond(result)
}

pub(crate) async fn go_to(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let result = async {
        let id = required_id(&body)?;
        let pool = pool(&state)?;
        let mut tx = pool.begin().await?;
        let outcome = async {
            let row = authorized_locked(&mut tx, &user, id).await?;
            protocol_guard(&row)?;
            match row.get::<Option<i32>, _>("status") {
                Some(1) => view(&row),
                Some(2) => {
                    sqlx::query("UPDATE t_telegraph_key_pat_synthetical_train SET status=1,started_at=NOW(6) WHERE id=?")
                        .bind(id)
                        .execute(&mut *tx)
                        .await?;
                    let row = find_train_locked(&mut tx, id)
                        .await?
                        .ok_or_else(|| parameter(TRAIN_NOT_FOUND))?;
                    view(&row)
                }
                _ => Err(terminal("训练未暂停或已完成，不能继续")),
            }
        }
        .await;
        match outcome {
            Ok(value) => {
                tx.commit().await?;
                Ok(value)
            }
            Err(error) => {
                let _ = tx.rollback().await;
                Err(error)
            }
        }
    }
    .await;
    respond(result)
}

pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let result = async {
        let id = required_id(&body)?;
        let pool = pool(&state)?;
        let mut tx = pool.begin().await?;
        let outcome = async {
            let row = authorized_locked(&mut tx, &user, id).await?;
            protocol_guard(&row)?;
            let content = required_content(&body)?;
            let normalized = answers(&row, content)?;
            match row.get::<Option<i32>, _>("status") {
                Some(3) => {
                    require_same_answers(&row, &normalized)?;
                    view(&row)
                }
                Some(2) => {
                    require_same_answers(&row, &normalized)?;
                    sqlx::query(
                        "UPDATE t_telegraph_key_pat_synthetical_train SET status=3 WHERE id=?",
                    )
                    .bind(id)
                    .execute(&mut *tx)
                    .await?;
                    recompute_statistics(&mut tx, &owner_of(&row)?).await?;
                    let row = find_train_locked(&mut tx, id)
                        .await?
                        .ok_or_else(|| parameter(TRAIN_NOT_FOUND))?;
                    view(&row)
                }
                Some(1) if row.get::<i64, _>("clock_running") != 0 => {
                    update_capture(&mut tx, id, 3, &row, &normalized).await?;
                    recompute_statistics(&mut tx, &owner_of(&row)?).await?;
                    let row = find_train_locked(&mut tx, id)
                        .await?
                        .ok_or_else(|| parameter(TRAIN_NOT_FOUND))?;
                    view(&row)
                }
                _ => Err(parameter("训练尚未开始或已暂停")),
            }
        }
        .await;
        match outcome {
            Ok(value) => {
                tx.commit().await?;
                Ok(value)
            }
            Err(error) => {
                let _ = tx.rollback().await;
                Err(error)
            }
        }
    }
    .await;
    respond(result)
}

pub(crate) async fn statistical_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let result = async {
        let pool = pool(&state)?;
        let mut tx = pool.begin().await?;
        let outcome = async {
            lock_user(&mut tx, &user.id).await?;
            let mut rows = sqlx::query("SELECT id,type,total_count,total_time,avg_speed,user_id FROM t_telegraph_key_train_statistical WHERE user_id=? ORDER BY type")
                .bind(&user.id)
                .fetch_all(&mut *tx)
                .await?;
            let present: std::collections::HashSet<i32> = rows
                .iter()
                .filter_map(|row| row.get::<Option<i32>, _>("type"))
                .collect();
            for kind in 0..3 {
                if !present.contains(&kind) {
                    let id = Uuid::new_v4().to_string();
                    sqlx::query("INSERT INTO t_telegraph_key_train_statistical (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,?,0,'0',0,?)")
                        .bind(&id)
                        .bind(kind)
                        .bind(&user.id)
                        .execute(&mut *tx)
                        .await?;
                    let row = sqlx::query("SELECT id,type,total_count,total_time,avg_speed,user_id FROM t_telegraph_key_train_statistical WHERE id=?")
                        .bind(&id)
                        .fetch_one(&mut *tx)
                        .await?;
                    rows.push(row);
                }
            }
            rows.sort_by_key(|row| row.get::<Option<i32>, _>("type").unwrap_or(i32::MAX));
            rows.iter().map(statistic_view).collect::<Result<Vec<_>, Error>>().map(Value::Array)
        }
        .await;
        match outcome {
            Ok(value) => {
                tx.commit().await?;
                Ok(value)
            }
            Err(error) => {
                let _ = tx.rollback().await;
                Err(error)
            }
        }
    }
    .await;
    respond(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn score_excludes_unanswered_rows_and_caps_overlong_input() {
        let rows = json!([
            {"text":"1234","value":"12345678","isFocus":true},
            {"text":"5678","value":"5678","isFocus":true},
            {"text":"9999","value":"","isFocus":false}
        ]);
        assert_eq!(metrics(&rows, 60_000).unwrap(), (3, 1, 50.0, "8.00".into()));
    }

    #[test]
    fn score_counts_utf16_units_and_handles_zero_elapsed_time() {
        let rows = json!([{"text":"\u{1F600}","value":"\u{1F600}","isFocus":true}]);
        assert_eq!(
            metrics(&rows, 60_000).unwrap(),
            (1, 0, 100.0, "2.00".into())
        );
        assert_eq!(metrics(&rows, 0).unwrap(), (1, 0, 100.0, "0".into()));
        assert!(metrics(&rows, -1).is_err());
    }
}
