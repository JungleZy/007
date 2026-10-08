use super::{envelope_json, envelope_response, scoring, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rust_decimal::{Decimal, RoundingStrategy};
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::{MySql, Row, Transaction};
use std::str::FromStr;

#[derive(Debug, Deserialize)]
pub(crate) struct TypeRequest {
    #[serde(rename = "type")]
    train_type: Option<i32>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct BaseSaveRequest {
    count: Option<i32>,
    mistake: Option<i32>,
    #[serde(rename = "type")]
    train_type: Option<i32>,
    duration: Option<Value>,
}

fn database_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "telex database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

fn duration(value: Option<&Value>) -> Option<i64> {
    match value? {
        Value::Number(number) => number.as_i64(),
        Value::String(value) => value.parse().ok(),
        _ => None,
    }
}

fn base_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "userId": row.get::<Option<String>, _>("user_id"),
        "count": row.get::<Option<i32>, _>("count"),
        "mistake": row.get::<Option<i32>, _>("mistake"),
        "duration": row.get::<Option<i64>, _>("duration"),
        "type": row.get::<Option<i32>, _>("type")
    })
}

async fn lock_user(
    transaction: &mut Transaction<'_, MySql>,
    user_id: &str,
) -> Result<bool, sqlx::Error> {
    sqlx::query("SELECT id FROM t_user WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(user_id)
        .fetch_optional(&mut **transaction)
        .await
        .map(|row| row.is_some())
}

pub(crate) async fn find_base(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TypeRequest>,
) -> Response {
    let Some(train_type) = body.train_type else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,user_id,count,mistake,duration,type FROM t_telex_pat WHERE user_id=? AND type=? LIMIT 1")
        .bind(&user.id)
        .bind(train_type)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(base_json(&row)), "ok", ""),
        Ok(None) => envelope_json(StatusCode::OK, 200, None, "ok", ""),
        Err(error) => database_error(error, "find base training"),
    }
}

pub(crate) async fn save_base(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<BaseSaveRequest>,
) -> Response {
    let (Some(count), Some(mistake), Some(train_type), Some(duration)) = (
        body.count,
        body.mistake,
        body.train_type,
        duration(body.duration.as_ref()),
    ) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start base save transaction"),
    };
    match lock_user(&mut transaction, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(error) => return database_error(error, "lock user for base save"),
    }
    let existing = match sqlx::query(
        "SELECT id FROM t_telex_pat WHERE user_id=? AND type=? LIMIT 1 FOR UPDATE",
    )
    .bind(&user.id)
    .bind(train_type)
    .fetch_optional(&mut *transaction)
    .await
    {
        Ok(row) => row,
        Err(error) => return database_error(error, "load base training for save"),
    };
    let id = if let Some(row) = existing {
        let id = row.get::<String, _>("id");
        if let Err(error) =
            sqlx::query("UPDATE t_telex_pat SET count=?,mistake=?,duration=? WHERE id=?")
                .bind(count)
                .bind(mistake)
                .bind(duration)
                .bind(&id)
                .execute(&mut *transaction)
                .await
        {
            return database_error(error, "update base training");
        }
        id
    } else {
        let id = uuid::Uuid::new_v4().to_string();
        if let Err(error) = sqlx::query(
            "INSERT INTO t_telex_pat (id,user_id,count,mistake,duration,type) VALUES (?,?,?,?,?,?)",
        )
        .bind(&id)
        .bind(&user.id)
        .bind(count)
        .bind(mistake)
        .bind(duration)
        .bind(train_type)
        .execute(&mut *transaction)
        .await
        {
            return database_error(error, "insert base training");
        }
        id
    };

    let total_time = match sqlx::query("SELECT CAST(COALESCE(SUM(duration),0) AS SIGNED) AS total_time FROM t_telex_pat WHERE user_id=? AND type=0")
        .bind(&user.id)
        .fetch_one(&mut *transaction)
        .await
    {
        Ok(row) => row.get::<i64, _>("total_time"),
        Err(error) => return database_error(error, "sum base duration"),
    };
    let statistical = match sqlx::query("SELECT id,total_count FROM t_telex_pat_train_statistical WHERE user_id=? AND type=0 LIMIT 1 FOR UPDATE")
        .bind(&user.id)
        .fetch_optional(&mut *transaction)
        .await
    {
        Ok(row) => row,
        Err(error) => return database_error(error, "load base statistics"),
    };
    let statistical_result = if let Some(row) = statistical {
        sqlx::query(
            "UPDATE t_telex_pat_train_statistical SET total_time=?,total_count=? WHERE id=?",
        )
        .bind(total_time.to_string())
        .bind(row.get::<Option<i32>, _>("total_count").unwrap_or(0) + 1)
        .bind(row.get::<String, _>("id"))
        .execute(&mut *transaction)
        .await
    } else {
        sqlx::query("INSERT INTO t_telex_pat_train_statistical (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,0,1,?,NULL,?)")
            .bind(uuid::Uuid::new_v4().to_string())
            .bind(total_time.to_string())
            .bind(&user.id)
            .execute(&mut *transaction)
            .await
    };
    if let Err(error) = statistical_result {
        return database_error(error, "save base statistics");
    }
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit base save transaction");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({
            "id": id,
            "userId": user.id,
            "count": count,
            "mistake": mistake,
            "duration": duration,
            "type": train_type
        })),
        "ok",
        "",
    )
}

pub(crate) async fn delete_base(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TypeRequest>,
) -> Response {
    let Some(train_type) = body.train_type else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start base delete transaction"),
    };
    match lock_user(&mut transaction, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(error) => return database_error(error, "lock user for base delete"),
    }
    if let Err(error) = sqlx::query("DELETE FROM t_telex_pat WHERE user_id=? AND type=?")
        .bind(&user.id)
        .bind(train_type)
        .execute(&mut *transaction)
        .await
    {
        return database_error(error, "delete base training");
    }
    if let Err(error) = sqlx::query("UPDATE t_telex_pat_train_statistical SET total_time='0',total_count=0,avg_speed=0 WHERE user_id=? AND type=?")
        .bind(&user.id)
        .bind(train_type)
        .execute(&mut *transaction)
        .await
    {
        return database_error(error, "clear base statistics");
    }
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit base delete transaction");
    }
    envelope_response(StatusCode::OK, 200, "ok", "")
}

pub(crate) async fn statistical_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match crate::statistical::load_or_create(pool, "t_telex_pat_train_statistical", &user.id, 4)
        .await
    {
        Ok(rows) => envelope_json(StatusCode::OK, 200, Some(Value::Array(rows)), "ok", ""),
        Err(error) => database_error(error, "list statistics"),
    }
}

pub(crate) async fn last_train(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TypeRequest>,
) -> Response {
    let Some(kind) = body.train_type else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,numbs,title,status,duration,total_number,error_number,accuracy,speed,content FROM t_telex_pat_train WHERE create_user_id=? AND type=? ORDER BY create_time DESC LIMIT 1")
        .bind(&user.id)
        .bind(kind)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(
            StatusCode::OK,
            200,
            Some(json!({
                "id": row.get::<String, _>("id"),
                "type": row.get::<Option<i32>, _>("type"),
                "numbs": row.get::<Option<i32>, _>("numbs"),
                "title": row.get::<Option<String>, _>("title"),
                "status": row.get::<Option<i32>, _>("status"),
                "duration": row.get::<Option<String>, _>("duration"),
                "totalNumber": row.get::<Option<i32>, _>("total_number"),
                "errorNumber": row.get::<Option<i32>, _>("error_number"),
                "accuracy": row.get::<Option<i32>, _>("accuracy"),
                "speed": row.get::<Option<String>, _>("speed"),
                "content": row.get::<Option<String>, _>("content")
            })),
            "ok",
            "",
        ),
        Ok(None) => envelope_json(StatusCode::OK, 200, None, "ok", ""),
        Err(error) => database_error(error, "find last train"),
    }
}
/// A JSON property that keeps "absent" apart from an explicit `null`: Jackson leaves a missing
/// DTO property at its default (`status = 0`, `totalNumber = 0`, `createTime = now`) but lets an
/// explicit `null` overwrite it.
#[derive(Debug, Default)]
enum Field {
    #[default]
    Absent,
    Null,
    Value(Value),
}

impl<'de> Deserialize<'de> for Field {
    fn deserialize<D: serde::Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        Value::deserialize(deserializer).map(|value| match value {
            Value::Null => Field::Null,
            other => Field::Value(other),
        })
    }
}

impl Field {
    /// Jackson coerces JSON numbers and numeric strings into `Integer` properties.
    fn int(&self) -> Option<i64> {
        match self {
            Field::Value(Value::Number(number)) => number
                .as_i64()
                .or_else(|| number.as_f64().map(|float| float as i64)),
            Field::Value(Value::String(text)) => {
                let text = text.trim();
                text.parse::<i64>()
                    .ok()
                    .or_else(|| text.parse::<f64>().ok().map(|float| float as i64))
            }
            _ => None,
        }
    }
    /// The DTO default applies to a missing property only.
    fn int_or(&self, default: i64) -> Option<i64> {
        if matches!(self, Field::Absent) {
            Some(default)
        } else {
            self.int()
        }
    }
    fn int32(&self) -> Option<i32> {
        self.int().and_then(|value| i32::try_from(value).ok())
    }
    fn int32_or(&self, default: i32) -> Option<i32> {
        if matches!(self, Field::Absent) {
            Some(default)
        } else {
            self.int32()
        }
    }
    /// Jackson coerces scalars into `String` properties.
    fn text(&self) -> Option<String> {
        match self {
            Field::Value(Value::String(text)) => Some(text.clone()),
            Field::Value(Value::Number(number)) => Some(number.to_string()),
            Field::Value(Value::Bool(flag)) => Some(flag.to_string()),
            _ => None,
        }
    }
}

#[derive(Debug, Deserialize)]
pub(crate) struct TrainRequest {
    id: Option<String>,
    #[serde(rename = "type", default)]
    train_type: Field,
    #[serde(default)]
    status: Field,
    #[serde(default)]
    numbs: Field,
    title: Option<String>,
    #[serde(default)]
    duration: Field,
    #[serde(rename = "totalNumber", default)]
    total_number: Field,
    #[serde(rename = "errorNumber", default)]
    error_number: Field,
    #[serde(default)]
    accuracy: Field,
    #[serde(default)]
    speed: Field,
    #[serde(rename = "createTime", default)]
    create_time: Field,
    content: Option<String>,
    #[serde(rename = "extendBy", default)]
    extend_by: Field,
}
fn train_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error=%error,operation,"telex training write failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}
/// Java maps IllegalArgumentException and IllegalStateException to 202 with description = message.
fn invalid(message: &'static str) -> Response {
    envelope_response(StatusCode::OK, 202, message, message)
}
/// Java `String.valueOf(map.get(key))`.
fn java_text(value: Option<&Value>) -> String {
    match value {
        None | Some(Value::Null) => "null".to_owned(),
        Some(Value::String(text)) => text.clone(),
        Some(other) => other.to_string(),
    }
}

const TRAIN_FIELDS: &str = "id,type,numbs,title,status,duration,total_number,error_number,accuracy,speed,create_user_id,create_time,content,protocol_version,active_since,accumulated_active_millis";

/// The whole TelexPatTrainEntity, as Java serializes it.
fn train_value(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "type": row.get::<Option<i32>, _>("type"),
        "numbs": row.get::<Option<i32>, _>("numbs"),
        "title": row.get::<Option<String>, _>("title"),
        "status": row.get::<Option<i32>, _>("status"),
        "duration": row.get::<Option<String>, _>("duration"),
        "totalNumber": row.get::<Option<i32>, _>("total_number"),
        "errorNumber": row.get::<Option<i32>, _>("error_number"),
        "accuracy": row.get::<Option<i32>, _>("accuracy"),
        "speed": row.get::<Option<String>, _>("speed"),
        "createUserId": row.get::<Option<String>, _>("create_user_id"),
        "createTime": row.get::<Option<String>, _>("create_time"),
        "content": row.get::<Option<String>, _>("content"),
        "protocolVersion": row.get::<Option<i32>, _>("protocol_version"),
        "activeSince": row.get::<Option<i64>, _>("active_since"),
        "accumulatedActiveMillis": row.get::<Option<i64>, _>("accumulated_active_millis")
    })
}
pub(crate) async fn train_list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = format!("SELECT {TRAIN_FIELDS} FROM t_telex_pat_train WHERE create_user_id=? ORDER BY create_time DESC");
    match sqlx::query(&sql).bind(&user.id).fetch_all(pool).await {
        Ok(rows) => envelope_json(
            StatusCode::OK,
            200,
            Some(Value::Array(rows.iter().map(train_value).collect())),
            "ok",
            "",
        ),
        Err(e) => train_error(e, "list train"),
    }
}
pub(crate) async fn train_detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TrainRequest>,
) -> Response {
    let Some(id) = body.id.filter(|v| !v.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = format!("SELECT {TRAIN_FIELDS} FROM t_telex_pat_train WHERE id=? LIMIT 1");
    match sqlx::query(&sql).bind(id).fetch_optional(pool).await {
        Ok(Some(row))
            if row.get::<Option<String>, _>("create_user_id").as_deref()
                == Some(user.id.as_str()) =>
        {
            envelope_json(StatusCode::OK, 200, Some(train_value(&row)), "ok", "")
        }
        Ok(Some(_)) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Ok(None) => invalid("未查询到训练"),
        Err(e) => train_error(e, "detail train"),
    }
}

/// Java `TelexPatTrainStatisticalService.statistical`: a finished training of `train_type`
/// feeds the statistics row of type `train_type + 1`; a missing row is rebuilt from every
/// finished training of that type. The caller holds the user row lock, so these reads are plain:
/// a locking read on the unindexed `user_id`/`type` filter would lock every row it scans.
async fn record_statistics(
    tx: &mut Transaction<'_, MySql>,
    user_id: &str,
    train_type: i32,
    duration: Option<&str>,
) -> Result<(), sqlx::Error> {
    let stat_type = train_type + 1;
    let existing = sqlx::query("SELECT id,total_count,total_time FROM t_telex_pat_train_statistical WHERE user_id=? AND type=? LIMIT 1")
        .bind(user_id)
        .bind(stat_type)
        .fetch_optional(&mut **tx)
        .await?;
    let seconds = |text: Option<&str>| {
        text.and_then(|value| value.trim().parse::<i64>().ok())
            .unwrap_or(0)
    };
    match existing {
        None => {
            let finished = sqlx::query("SELECT duration,speed FROM t_telex_pat_train WHERE create_user_id=? AND type=? AND status=3")
                .bind(user_id)
                .bind(train_type)
                .fetch_all(&mut **tx)
                .await?;
            let mut total_time = 0_i64;
            let mut total_speed = Decimal::ZERO;
            for row in &finished {
                total_time += seconds(row.get::<Option<String>, _>("duration").as_deref());
                total_speed += row
                    .get::<Option<String>, _>("speed")
                    .and_then(|value| Decimal::from_str(value.trim()).ok())
                    .unwrap_or(Decimal::ZERO);
            }
            let count = finished.len() as i64;
            let average = if count == 0 {
                Decimal::ZERO
            } else {
                (total_speed / Decimal::from(count))
                    .round_dp_with_strategy(0, RoundingStrategy::MidpointAwayFromZero)
            };
            sqlx::query("INSERT INTO t_telex_pat_train_statistical (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,?,?,?,?,?)")
                .bind(uuid::Uuid::new_v4().to_string())
                .bind(stat_type)
                .bind(count as i32)
                .bind(total_time.to_string())
                .bind(average)
                .bind(user_id)
                .execute(&mut **tx)
                .await?;
        }
        Some(row) => {
            let average: Decimal = sqlx::query_scalar("SELECT CAST(IFNULL(AVG(CAST(speed AS DECIMAL(20,6))),0) AS DECIMAL(20,6)) FROM t_telex_pat_train WHERE create_user_id=? AND type=? AND status=3")
                .bind(user_id)
                .bind(train_type)
                .fetch_one(&mut **tx)
                .await?;
            let total_time =
                seconds(row.get::<Option<String>, _>("total_time").as_deref()) + seconds(duration);
            sqlx::query("UPDATE t_telex_pat_train_statistical SET avg_speed=?,total_time=?,total_count=? WHERE id=?")
                .bind(average)
                .bind(total_time.to_string())
                .bind(row.get::<Option<i32>, _>("total_count").unwrap_or(0) + 1)
                .bind(row.get::<String, _>("id"))
                .execute(&mut **tx)
                .await?;
        }
    }
    Ok(())
}

/// Java `lastTration` cleanup on creating an unstarted training: the user's previous training of
/// this type is deleted when it never started, and finished (with statistics) when it was paused.
///
/// The caller holds the user row lock, so no other save of this user interleaves. The previous
/// training is found with a plain read because the filter has no index: a locking read there
/// would lock every row scanned and stall other users' saves until commit. Only the row that is
/// acted on is locked, by primary key.
async fn settle_previous(
    tx: &mut Transaction<'_, MySql>,
    user_id: &str,
    kind: i32,
) -> Result<(), sqlx::Error> {
    let latest: Option<String> = sqlx::query_scalar("SELECT id FROM t_telex_pat_train WHERE create_user_id=? AND type=? ORDER BY create_time DESC LIMIT 1")
        .bind(user_id)
        .bind(kind)
        .fetch_optional(&mut **tx)
        .await?;
    let Some(id) = latest else {
        return Ok(());
    };
    let previous =
        sqlx::query("SELECT status,type,duration FROM t_telex_pat_train WHERE id=? FOR UPDATE")
            .bind(&id)
            .fetch_optional(&mut **tx)
            .await?;
    let Some(previous) = previous else {
        // Deleted by another request between the read and the lock.
        return Ok(());
    };
    match previous.get::<Option<i32>, _>("status") {
        Some(0) => {
            sqlx::query("DELETE FROM t_telex_pat_train WHERE id=?")
                .bind(&id)
                .execute(&mut **tx)
                .await?;
        }
        Some(2) => {
            sqlx::query("UPDATE t_telex_pat_train SET status=3 WHERE id=?")
                .bind(&id)
                .execute(&mut **tx)
                .await?;
            record_statistics(
                tx,
                user_id,
                previous.get::<Option<i32>, _>("type").unwrap_or(kind),
                previous.get::<Option<String>, _>("duration").as_deref(),
            )
            .await?;
        }
        _ => {}
    }
    Ok(())
}

/// Java `validateAndStoreContent`: the submitted rows must keep the issued text; `extendBy`
/// appends freshly generated groups (the Vue page asks for 100 once every group is typed).
/// Errors are the exact Java `IllegalArgumentException` messages (answered as 202).
fn checked_content(
    submitted: &str,
    stored: Option<&str>,
    extend_by: Option<i64>,
    kind: i32,
) -> Result<String, &'static str> {
    let expected = match stored {
        None => Vec::new(),
        Some(text) => match serde_json::from_str::<Value>(text) {
            Ok(Value::Array(rows)) => rows,
            _ => return Err("训练报文内容无效"),
        },
    };
    let actual = match serde_json::from_str::<Value>(submitted) {
        Ok(Value::Array(rows)) => rows,
        _ => return Err("训练报文内容无效"),
    };
    let extension = extend_by.unwrap_or(0);
    if !(0..=100).contains(&extension) {
        return Err("扩展数量无效");
    }
    if actual.len() != expected.len() {
        return Err(if extension == 0 {
            "训练报文结构不一致"
        } else {
            "扩展请求必须基于完整原始报文"
        });
    }
    for (issued, typed) in expected.iter().zip(actual.iter()) {
        if java_text(issued.get("text")) != java_text(typed.get("text")) {
            return Err("训练报文源内容不可修改");
        }
    }
    if extension == 0 {
        return Ok(submitted.to_owned());
    }
    // Append to the submitted text so the original rows keep their submitted form.
    let appended =
        super::post_telegram_write::generated_groups(extension as usize, kind, true, true)
            .into_iter()
            .map(|text| json!({"text": text, "value": ""}).to_string())
            .collect::<Vec<_>>()
            .join(",");
    let body = submitted.trim_end();
    let body = body.strip_suffix(']').unwrap_or(body);
    Ok(if actual.is_empty() {
        format!("[{appended}]")
    } else {
        format!("{body},{appended}]")
    })
}

/// Java `applyMetrics`: the score is recomputed from the stored rows, never taken from the
/// client. A row counts once it holds a non-blank typed value and is wrong unless the typed
/// value equals the issued text. Returns `(total, errors, accuracy)`; accuracy is integer
/// division, as in Java.
fn measure(content: Option<&str>) -> Result<(i32, i32, i32), &'static str> {
    let rows = match content {
        None => Vec::new(),
        Some(text) => match serde_json::from_str::<Value>(text) {
            Ok(Value::Array(rows)) => rows,
            _ => return Err("训练报文内容无效"),
        },
    };
    let (mut total, mut errors) = (0_i32, 0_i32);
    for row in &rows {
        let typed = row.get("value");
        if matches!(typed, None | Some(Value::Null)) || java_text(typed).trim().is_empty() {
            continue;
        }
        total += 1;
        if java_text(row.get("text")) != java_text(typed) {
            errors += 1;
        }
    }
    let accuracy = if total == 0 {
        0
    } else {
        (total - errors) * 100 / total
    };
    Ok((total, errors, accuracy))
}

/// Java `BigDecimal.valueOf(total * 60000).divide(millis, 2, HALF_UP).toPlainString()`; a
/// training with no active time scores "0".
fn speed_text(total: i32, active_millis: i64) -> String {
    if active_millis == 0 {
        "0".to_owned()
    } else {
        format!(
            "{:.2}",
            scoring::rate_per_minute_precise(i64::from(total), active_millis)
        )
    }
}

pub(crate) async fn train_save(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TrainRequest>,
) -> Response {
    // The DTO defaults `status` to 0 when the property is missing (the creation form omits it).
    let (Some(kind), Some(requested)) = (body.train_type.int(), body.status.int_or(0)) else {
        return invalid("训练状态和类型不能为空");
    };
    if !(0..=3).contains(&requested) {
        return invalid("训练状态无效");
    }
    if !(0..=2).contains(&kind) {
        return invalid("训练类型无效");
    }
    let (kind, requested) = (kind as i32, requested as i32);
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_millis().min(i64::MAX as u128) as i64)
        .unwrap_or(0);
    let mut tx = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return train_error(error, "start train save transaction"),
    };
    // Saves of one user serialize on the user row (the same point the statistics page and the
    // base training use), so the cleanup and the lazily built statistics rows cannot race.
    match lock_user(&mut tx, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(error) => return train_error(error, "lock user for train save"),
    }
    let id = match body.id.as_deref().filter(|id| !id.trim().is_empty()) {
        Some(id) => match update_train(&mut tx, &user, id, requested, &body, now).await {
            Ok(()) => id.to_owned(),
            Err(response) => return response,
        },
        None => match create_train(&mut tx, &user, kind, requested, &body, now).await {
            Ok(id) => id,
            Err(response) => return response,
        },
    };
    let sql = format!("SELECT {TRAIN_FIELDS} FROM t_telex_pat_train WHERE id=?");
    let row = match sqlx::query(&sql).bind(&id).fetch_one(&mut *tx).await {
        Ok(row) => row,
        Err(error) => return train_error(error, "reload train"),
    };
    if let Err(error) = tx.commit().await {
        return train_error(error, "commit train save");
    }
    // Java answers with the saved entity; the Vue page spreads it into its state.
    envelope_json(StatusCode::OK, 200, Some(train_value(&row)), "ok", "")
}

async fn create_train(
    tx: &mut Transaction<'_, MySql>,
    user: &AuthUser,
    kind: i32,
    requested: i32,
    body: &TrainRequest,
    now: i64,
) -> Result<String, Response> {
    if requested == 3 {
        return Err(invalid("训练尚未开始，不能完成"));
    }
    if requested == 0 {
        settle_previous(tx, &user.id, kind)
            .await
            .map_err(|error| train_error(error, "settle previous"))?;
    }
    let id = uuid::Uuid::new_v4().to_string();
    let active_since = (requested == 1).then_some(now);
    sqlx::query("INSERT INTO t_telex_pat_train (id,type,numbs,title,status,duration,total_number,error_number,accuracy,speed,content,create_user_id,protocol_version,active_since,accumulated_active_millis,create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,1,?,0,?)")
        .bind(&id)
        .bind(kind)
        .bind(body.numbs.int32())
        .bind(body.title.as_deref())
        .bind(requested)
        .bind(body.duration.text())
        .bind(body.total_number.int32_or(0))
        .bind(body.error_number.int32_or(0))
        .bind(body.accuracy.int32_or(0))
        .bind(body.speed.text())
        .bind(body.content.as_deref())
        .bind(&user.id)
        .bind(active_since)
        .bind(match &body.create_time {
            Field::Absent => Some(crate::now_time_millis().to_string()),
            other => other.text(),
        })
        .execute(&mut **tx)
        .await
        .map_err(|error| train_error(error, "insert train"))?;
    Ok(id)
}

async fn update_train(
    tx: &mut Transaction<'_, MySql>,
    user: &AuthUser,
    id: &str,
    requested: i32,
    body: &TrainRequest,
    now: i64,
) -> Result<(), Response> {
    let row = sqlx::query("SELECT create_user_id,type,status,protocol_version,active_since,accumulated_active_millis,content FROM t_telex_pat_train WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(id)
        .fetch_optional(&mut **tx)
        .await
        .map_err(|error| train_error(error, "load train for update"))?;
    let Some(row) = row else {
        return Err(invalid("未查询到训练"));
    };
    if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return Err(envelope_response(StatusCode::OK, 207, "无权限", ""));
    }
    let current = row.get::<Option<i32>, _>("status").unwrap_or(0);
    if current == 3 {
        return Err(envelope_response(StatusCode::OK, 208, "训练已结束", ""));
    }
    if row.get::<Option<i32>, _>("protocol_version").unwrap_or(0) < 1 {
        return Err(invalid("旧版训练记录不可继续提交"));
    }
    let allowed = current == requested
        || matches!(
            (current, requested),
            (0, 1) | (1, 2) | (1, 3) | (2, 1) | (2, 3)
        );
    if !allowed {
        return Err(invalid("训练状态流转无效"));
    }
    let kind = row.get::<Option<i32>, _>("type").unwrap_or(0);
    let stored = row.get::<Option<String>, _>("content");
    let content = match body.content.as_deref() {
        Some(submitted) => Some(
            checked_content(submitted, stored.as_deref(), body.extend_by.int(), kind)
                .map_err(invalid)?,
        ),
        None => stored,
    };
    // Java updateActiveClock.
    let mut accumulated = row
        .get::<Option<i64>, _>("accumulated_active_millis")
        .unwrap_or(0);
    let mut active_since = row.get::<Option<i64>, _>("active_since");
    if current != 1 && requested == 1 {
        active_since = Some(now);
    } else if current == 1 && requested != 1 {
        accumulated =
            accumulated.saturating_add(now.saturating_sub(active_since.unwrap_or(now)).max(0));
        active_since = None;
    }
    if requested != 3 {
        sqlx::query("UPDATE t_telex_pat_train SET status=?,active_since=?,accumulated_active_millis=?,content=? WHERE id=?")
            .bind(requested)
            .bind(active_since)
            .bind(accumulated)
            .bind(content.as_deref())
            .bind(id)
            .execute(&mut **tx)
            .await
            .map_err(|error| train_error(error, "update train"))?;
        return Ok(());
    }
    // The server recomputes the score from the stored rows, never from the client.
    let (total, errors, accuracy) = measure(content.as_deref()).map_err(invalid)?;
    let duration = (accumulated / 1000).to_string();
    let speed = speed_text(total, accumulated);
    sqlx::query("UPDATE t_telex_pat_train SET status=3,active_since=NULL,accumulated_active_millis=?,content=?,duration=?,total_number=?,error_number=?,accuracy=?,speed=? WHERE id=?")
        .bind(accumulated)
        .bind(content.as_deref())
        .bind(&duration)
        .bind(total)
        .bind(errors)
        .bind(accuracy)
        .bind(&speed)
        .bind(id)
        .execute(&mut **tx)
        .await
        .map_err(|error| train_error(error, "finish train"))?;
    record_statistics(tx, &user.id, kind, Some(&duration))
        .await
        .map_err(|error| train_error(error, "record statistics"))?;
    Ok(())
}
pub(crate) async fn train_delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TrainRequest>,
) -> Response {
    let Some(id) = body.id else {
        return envelope_response(
            StatusCode::OK,
            202,
            "id to load is required for loading",
            "id to load is required for loading",
        );
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<Response, sqlx::Error> = async {
        let mut transaction = pool.begin().await?;
        let row = sqlx::query("SELECT create_user_id FROM t_telex_pat_train WHERE id=? FOR UPDATE")
            .bind(&id)
            .fetch_optional(&mut *transaction)
            .await?;
        let Some(row) = row else {
            return Ok(envelope_response(
                StatusCode::OK,
                202,
                "未查询到该训练",
                "未查询到该训练",
            ));
        };
        if row
            .try_get::<Option<String>, _>("create_user_id")?
            .as_deref()
            != Some(user.id.as_str())
        {
            return Ok(envelope_response(StatusCode::OK, 207, "无权限", ""));
        }
        sqlx::query("DELETE FROM t_telex_pat_train WHERE id=?")
            .bind(id)
            .execute(&mut *transaction)
            .await?;
        transaction.commit().await?;
        Ok(envelope_response(StatusCode::OK, 200, "ok", ""))
    }
    .await;
    match result {
        Ok(response) => response,
        Err(error) => train_error(error, "delete train"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rows(items: &[(&str, &str)]) -> String {
        Value::Array(
            items
                .iter()
                .map(|(text, value)| json!({"value": value, "text": text, "type": true, "isFocus": false}))
                .collect(),
        )
        .to_string()
    }

    fn request(body: &str) -> TrainRequest {
        serde_json::from_str(body).expect("request body")
    }

    #[test]
    fn missing_status_defaults_to_not_started_but_explicit_null_does_not() {
        // The creation form omits `status`; Jackson keeps the DTO default of 0.
        let created = request(r#"{"type":2,"content":"[]"}"#);
        assert_eq!(created.status.int_or(0), Some(0));
        assert_eq!(created.total_number.int32_or(0), Some(0));
        // An explicit null overwrites the default, which Java rejects as a missing status.
        let nulled = request(r#"{"type":2,"status":null,"totalNumber":null}"#);
        assert_eq!(nulled.status.int_or(0), None);
        assert_eq!(nulled.total_number.int32_or(0), None);
    }

    #[test]
    fn scalars_are_coerced_like_jackson() {
        let body = request(
            r#"{"type":"1","status":" 2 ","numbs":7.9,"duration":30,"speed":12.5,"accuracy":"abc","totalNumber":3000000000}"#,
        );
        assert_eq!(body.train_type.int(), Some(1));
        assert_eq!(body.status.int(), Some(2));
        assert_eq!(body.numbs.int32(), Some(7));
        assert_eq!(body.duration.text().as_deref(), Some("30"));
        assert_eq!(body.speed.text().as_deref(), Some("12.5"));
        assert_eq!(body.accuracy.int(), None);
        // Does not fit an Integer column: never wraps into a different number.
        assert_eq!(body.total_number.int32(), None);
    }

    #[test]
    fn content_must_keep_the_issued_text_and_row_count() {
        let stored = rows(&[("ab12", ""), ("cd34", "")]);
        let typed = rows(&[("ab12", "ab12"), ("cd34", "cx")]);
        // Typing into the rows is the whole point and is stored as submitted.
        assert_eq!(
            checked_content(&typed, Some(&stored), None, 1),
            Ok(typed.clone())
        );
        assert_eq!(
            checked_content(&rows(&[("ab13", ""), ("cd34", "")]), Some(&stored), None, 1),
            Err("训练报文源内容不可修改")
        );
        let short = rows(&[("ab12", "")]);
        assert_eq!(
            checked_content(&short, Some(&stored), None, 1),
            Err("训练报文结构不一致")
        );
        assert_eq!(
            checked_content(&short, Some(&stored), Some(2), 1),
            Err("扩展请求必须基于完整原始报文")
        );
        // A training issued without content only accepts an empty list.
        assert_eq!(checked_content("[]", None, None, 1), Ok("[]".to_owned()));
        assert_eq!(
            checked_content(&typed, None, None, 1),
            Err("训练报文结构不一致")
        );
    }

    #[test]
    fn unreadable_content_and_out_of_range_extension_are_rejected() {
        let stored = rows(&[("ab12", "")]);
        for bad in ["", "null", "{}", "[", "not json"] {
            assert_eq!(
                checked_content(bad, Some(&stored), None, 1),
                Err("训练报文内容无效"),
                "submitted {bad:?}"
            );
        }
        assert_eq!(
            checked_content(&stored, Some("garbage"), None, 1),
            Err("训练报文内容无效")
        );
        // The range is checked before the structure, so an oversize ask on a short list says so.
        for extension in [-1, 101] {
            assert_eq!(
                checked_content(&stored, Some(&stored), Some(extension), 1),
                Err("扩展数量无效")
            );
        }
    }

    #[test]
    fn extension_appends_generated_groups_after_untouched_rows() {
        let stored = rows(&[("ab12", ""), ("cd34", "")]);
        let typed = rows(&[("ab12", "ab12"), ("cd34", "")]);
        for (kind, alphabet) in [
            (0, "0123456789"),
            (1, "ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
            (2, "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
        ] {
            let extended =
                checked_content(&typed, Some(&stored), Some(100), kind).expect("extended");
            let parsed: Vec<Value> = serde_json::from_str(&extended).expect("valid json");
            assert_eq!(parsed.len(), 102);
            // The rows the user already typed keep exactly the submitted text.
            assert!(extended.starts_with(typed.trim_end_matches(']')));
            for row in &parsed[2..] {
                let keys: Vec<&str> = row
                    .as_object()
                    .unwrap()
                    .keys()
                    .map(String::as_str)
                    .collect();
                assert_eq!(keys.len(), 2, "kind {kind}: {row}");
                assert_eq!(row["value"], "");
                let text = row["text"].as_str().unwrap();
                assert_eq!(text.chars().count(), 4, "kind {kind}: {row}");
                assert!(
                    text.chars().all(|c| alphabet.contains(c)),
                    "kind {kind}: {row}"
                );
            }
        }
        // An issued-empty list is extended from nothing.
        let from_empty = checked_content("[]", None, Some(3), 0).expect("extended");
        assert_eq!(
            serde_json::from_str::<Vec<Value>>(&from_empty)
                .unwrap()
                .len(),
            3
        );
    }

    #[test]
    fn scoring_counts_only_typed_rows_with_integer_accuracy() {
        let content = rows(&[
            ("ab12", "ab12"),
            ("cd34", "cx34"),
            ("ef56", ""),
            ("gh78", "   "),
        ]);
        assert_eq!(measure(Some(&content)), Ok((2, 1, 50)));
        // Integer division, not rounding: 2 of 3 right is 66, never 67.
        let content = rows(&[("a", "a"), ("b", "b"), ("c", "x")]);
        assert_eq!(measure(Some(&content)), Ok((3, 1, 66)));
        // Nothing typed (or no content at all) scores zero instead of dividing by zero.
        assert_eq!(measure(Some(&rows(&[("ab12", "")]))), Ok((0, 0, 0)));
        assert_eq!(measure(None), Ok((0, 0, 0)));
        assert_eq!(measure(Some("{")), Err("训练报文内容无效"));
    }

    #[test]
    fn speed_is_rounded_half_up_to_two_decimals() {
        assert_eq!(speed_text(2, 250), "480.00");
        // 60000 / 480000 is exactly 0.125: half-up gives 0.13 where banker's rounding gives 0.12.
        assert_eq!(speed_text(1, 480_000), "0.13");
        assert_eq!(speed_text(0, 5_000), "0.00");
        // No active time at all is the bare "0", not "0.00".
        assert_eq!(speed_text(3, 0), "0");
    }

    /// Settling one user's paused predecessor (which also updates that user's statistics row)
    /// must leave every other user's training and statistics rows writable. Both tables are
    /// indexed by primary key only, so a locking read on `create_user_id` / `user_id` would lock
    /// every row it scans and stall unrelated users' saves until commit.
    ///
    /// Needs an isolated MySQL database with the project schema:
    /// `RUST_PHASE2_DATABASE_URL=mysql://… cargo test -p rust-backend -- --ignored`.
    #[tokio::test]
    #[ignore = "requires an isolated MySQL database with the project schema (RUST_PHASE2_DATABASE_URL)"]
    async fn settling_one_users_predecessor_does_not_lock_other_users_rows(
    ) -> Result<(), sqlx::Error> {
        let url = std::env::var("RUST_PHASE2_DATABASE_URL")
            .expect("RUST_PHASE2_DATABASE_URL must target an isolated rehearsal database");
        let pool = sqlx::mysql::MySqlPoolOptions::new()
            .max_connections(3)
            .connect(&url)
            .await?;
        let tag = uuid::Uuid::new_v4().simple().to_string();
        let owner = format!("lock-owner-{tag}");
        let other = format!("lock-other-{tag}");
        for user in [&owner, &other] {
            // A paused training of type 1 (statistics type 2) with a statistics row each.
            sqlx::query("INSERT INTO t_telex_pat_train (id,type,status,duration,speed,create_user_id,create_time) VALUES (?,1,2,'20','10.50',?,'1')")
                .bind(format!("train-{user}"))
                .bind(user)
                .execute(&pool)
                .await?;
            sqlx::query("INSERT INTO t_telex_pat_train_statistical (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,2,0,'0',0,?)")
                .bind(format!("stat-{user}"))
                .bind(user)
                .execute(&pool)
                .await?;
        }

        // Act: settle the owner's predecessor and keep the transaction open.
        let mut tx = pool.begin().await?;
        settle_previous(&mut tx, &owner, 1).await?;
        let mut bystander = pool.acquire().await?;
        sqlx::query("SET SESSION innodb_lock_wait_timeout=2")
            .execute(&mut *bystander)
            .await?;
        let training = sqlx::query("UPDATE t_telex_pat_train SET title='x' WHERE id=?")
            .bind(format!("train-{other}"))
            .execute(&mut *bystander)
            .await;
        let statistics =
            sqlx::query("UPDATE t_telex_pat_train_statistical SET total_count=7 WHERE id=?")
                .bind(format!("stat-{other}"))
                .execute(&mut *bystander)
                .await;
        // The owner's own rows were really settled (finished + counted) inside the transaction.
        let settled: (Option<i32>, Option<i32>) = (
            sqlx::query_scalar("SELECT status FROM t_telex_pat_train WHERE id=?")
                .bind(format!("train-{owner}"))
                .fetch_one(&mut *tx)
                .await?,
            sqlx::query_scalar("SELECT total_count FROM t_telex_pat_train_statistical WHERE id=?")
                .bind(format!("stat-{owner}"))
                .fetch_one(&mut *tx)
                .await?,
        );
        tx.rollback().await?;
        drop(bystander);
        for user in [&owner, &other] {
            sqlx::query("DELETE FROM t_telex_pat_train WHERE create_user_id=?")
                .bind(user)
                .execute(&pool)
                .await?;
            sqlx::query("DELETE FROM t_telex_pat_train_statistical WHERE user_id=?")
                .bind(user)
                .execute(&pool)
                .await?;
        }

        assert_eq!(settled, (Some(3), Some(1)));
        assert!(
            training.is_ok(),
            "another user's training row is locked: {training:?}"
        );
        assert!(
            statistics.is_ok(),
            "another user's statistics row is locked: {statistics:?}"
        );
        Ok(())
    }
}
