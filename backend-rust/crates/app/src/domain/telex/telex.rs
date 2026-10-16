use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use crate::shared::scoring;
use crate::shared::statistical::lock_user;
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
    pub(crate) train_type: Option<i32>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct BaseSaveRequest {
    pub(crate) count: Option<i32>,
    mistake: Option<i32>,
    #[serde(rename = "type")]
    pub(crate) train_type: Option<i32>,
    pub(crate) duration: Option<Value>,
}

fn database_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "telex database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

pub(super) fn duration(value: Option<&Value>) -> Option<i64> {
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

pub(super) async fn find_base(
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
    let existing =
        match sqlx::query("SELECT id FROM t_telex_pat WHERE user_id=? AND type=? LIMIT 1")
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
    let statistical = match sqlx::query("SELECT id,total_count FROM t_telex_pat_train_statistical WHERE user_id=? AND type=0 LIMIT 1")
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
    let ids: Vec<String> = match sqlx::query_scalar(
        "SELECT id FROM t_telex_pat WHERE user_id=? AND type=? ORDER BY id",
    )
    .bind(&user.id)
    .bind(train_type)
    .fetch_all(&mut *transaction)
    .await
    {
        Ok(ids) => ids,
        Err(error) => return database_error(error, "discover base training for delete"),
    };
    for id in ids {
        if let Err(error) = sqlx::query("DELETE FROM t_telex_pat WHERE id=?")
            .bind(id)
            .execute(&mut *transaction)
            .await
        {
            return database_error(error, "delete base training");
        }
    }
    let ids: Vec<String> = match sqlx::query_scalar(
        "SELECT id FROM t_telex_pat_train_statistical WHERE user_id=? AND type=? ORDER BY id",
    )
    .bind(&user.id)
    .bind(train_type)
    .fetch_all(&mut *transaction)
    .await
    {
        Ok(ids) => ids,
        Err(error) => return database_error(error, "discover base statistics for clear"),
    };
    for id in ids {
        if let Err(error) = sqlx::query("UPDATE t_telex_pat_train_statistical SET total_time='0',total_count=0,avg_speed=0 WHERE id=?")
            .bind(id)
            .execute(&mut *transaction)
            .await
        {
            return database_error(error, "clear base statistics");
        }
    }
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit base delete transaction");
    }
    envelope_response(StatusCode::OK, 200, "ok", "")
}

pub(super) async fn statistical_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match crate::shared::statistical::load_or_create(
        pool,
        "t_telex_pat_train_statistical",
        &user.id,
        4,
    )
    .await
    {
        Ok(rows) => envelope_json(StatusCode::OK, 200, Some(Value::Array(rows)), "ok", ""),
        Err(error) => database_error(error, "list statistics"),
    }
}

pub(super) async fn last_train(
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
pub(super) enum Field {
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
    pub(super) fn int(&self) -> Option<i64> {
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
    pub(super) fn int_or(&self, default: i64) -> Option<i64> {
        if matches!(self, Field::Absent) {
            Some(default)
        } else {
            self.int()
        }
    }
    pub(super) fn int32(&self) -> Option<i32> {
        self.int().and_then(|value| i32::try_from(value).ok())
    }
    pub(super) fn int32_or(&self, default: i32) -> Option<i32> {
        if matches!(self, Field::Absent) {
            Some(default)
        } else {
            self.int32()
        }
    }
    /// Jackson coerces scalars into `String` properties.
    pub(super) fn text(&self) -> Option<String> {
        match self {
            Field::Value(Value::String(text)) => Some(text.clone()),
            Field::Value(Value::Number(number)) => Some(number.to_string()),
            Field::Value(Value::Bool(flag)) => Some(flag.to_string()),
            _ => None,
        }
    }
}

#[derive(Debug, Deserialize)]
pub(super) struct TrainRequest {
    id: Option<String>,
    #[serde(rename = "type", default)]
    pub(super) train_type: Field,
    #[serde(default)]
    pub(super) status: Field,
    #[serde(default)]
    pub(super) numbs: Field,
    title: Option<String>,
    #[serde(default)]
    pub(super) duration: Field,
    #[serde(rename = "totalNumber", default)]
    pub(super) total_number: Field,
    #[serde(rename = "errorNumber", default)]
    error_number: Field,
    #[serde(default)]
    pub(super) accuracy: Field,
    #[serde(default)]
    pub(super) speed: Field,
    #[serde(rename = "createTime", default)]
    create_time: Field,
    pub(super) content: Option<String>,
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
pub(super) async fn train_list(
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
pub(super) async fn train_detail(
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
pub(super) async fn settle_previous(
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
pub(super) fn checked_content(
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
    let appended = crate::domain::post_telegram::post_telegram_write::generated_groups(
        extension as usize,
        kind,
        true,
        true,
    )
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
pub(super) fn measure(content: Option<&str>) -> Result<(i32, i32, i32), &'static str> {
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
pub(super) fn speed_text(total: i32, active_millis: i64) -> String {
    if active_millis == 0 {
        "0".to_owned()
    } else {
        format!(
            "{:.2}",
            scoring::rate_per_minute_precise(i64::from(total), active_millis)
        )
    }
}

pub(super) async fn train_save(
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
            Field::Absent => Some(crate::platform::clock::now_time_millis().to_string()),
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
pub(super) async fn train_delete(
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
