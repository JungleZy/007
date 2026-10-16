use axum::{
    extract::{rejection::JsonRejection, Extension, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use rust_decimal::Decimal;
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, pool::PoolConnection, Acquire, MySql, Row, Transaction};

use crate::platform::{
    auth::AuthUser,
    envelope::{envelope_json, envelope_response},
    state::AppState,
};
use crate::statistical::lock_user;

#[derive(Debug, Deserialize)]
pub(crate) struct PageRequest {
    page: Option<i64>,
    rows: Option<i64>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct IdRequest {
    id: Option<String>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct TypeRequest {
    #[serde(rename = "type")]
    train_type: Option<i32>,
}

const TRAIN_COLUMNS: &str = "id,name,rate,type,code_message_body,code_short,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_time_text,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') AS start_time_text,DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') AS end_time_text,valid_time,status,user_id,mark,schedule,is_low_rate";

fn parse_message_body(raw: Option<String>) -> Result<Value, serde_json::Error> {
    match raw {
        Some(value)
            if value
                .bytes()
                .all(|byte| matches!(byte, b' ' | b'\t' | b'\r' | b'\n')) =>
        {
            Ok(Value::Null)
        }
        Some(value) => serde_json::from_str(&value),
        None => Ok(Value::Null),
    }
}

fn train_json(row: &MySqlRow) -> Result<Value, serde_json::Error> {
    let body = parse_message_body(row.get::<Option<String>, _>("code_message_body"))?;
    Ok(train_json_with_body(row, body))
}

fn train_json_with_body(row: &MySqlRow, body: Value) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "name": row.get::<Option<String>, _>("name"),
        "trainId": null,
        "type": row.get::<Option<i32>, _>("type"),
        "codeShort": row.get::<Option<i32>, _>("code_short"),
        "createTime": row.get::<Option<String>, _>("create_time_text"),
        "startTime": row.get::<Option<String>, _>("start_time_text"),
        "endTime": row.get::<Option<String>, _>("end_time_text"),
        "validTime": row.get::<Option<String>, _>("valid_time"),
        "status": row.get::<Option<i32>, _>("status"),
        "rate": row.get::<Option<i32>, _>("rate"),
        "codeMessageBody": body,
        "mark": row.get::<Option<String>, _>("mark"),
        "schedule": row.get::<Option<i32>, _>("schedule"),
        "isLowRate": row.get::<Option<i32>, _>("is_low_rate")
    })
}

fn empty_train() -> Value {
    json!({
        "id": null,
        "name": null,
        "trainId": null,
        "type": null,
        "codeShort": null,
        "createTime": null,
        "startTime": null,
        "endTime": null,
        "validTime": null,
        "status": null,
        "rate": null,
        "codeMessageBody": null,
        "mark": null,
        "schedule": null,
        "isLowRate": null
    })
}

pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<PageRequest>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let page = body.page.unwrap_or(1).max(1);
    let rows = body.rows.unwrap_or(20).clamp(1, 200);
    let total =
        match sqlx::query("SELECT COUNT(*) AS total FROM t_ticker_tape_train WHERE user_id=?")
            .bind(&user.id)
            .fetch_one(pool)
            .await
        {
            Ok(row) => row.get::<i64, _>("total"),
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        };
    let sql = format!(
        "SELECT {TRAIN_COLUMNS} FROM t_ticker_tape_train WHERE user_id=? ORDER BY create_time DESC LIMIT ? OFFSET ?"
    );
    let train_rows = match sqlx::query(&sql)
        .bind(&user.id)
        .bind(rows)
        .bind((page - 1) * rows)
        .fetch_all(pool)
        .await
    {
        Ok(rows) => rows,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let data = match train_rows
        .iter()
        .map(train_json)
        .collect::<Result<Vec<_>, _>>()
    {
        Ok(data) => data,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let total_page = if total == 0 {
        0
    } else {
        (total + rows - 1) / rows
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({
            "totalPage": total_page,
            "totalNumber": total,
            "currentPage": page,
            "pageSize": rows,
            "data": data
        })),
        "ok",
        "",
    )
}

pub(crate) async fn by_id(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<IdRequest>,
) -> Response {
    let Some(id) = body.id.filter(|id| !id.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = format!("SELECT {TRAIN_COLUMNS} FROM t_ticker_tape_train WHERE id=? LIMIT 1");
    let row = match sqlx::query(&sql).bind(&id).fetch_optional(pool).await {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到训练", "未查询到训练"),
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if row.get::<Option<String>, _>("user_id").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    match train_json(&row) {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn last_train(
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
    let sql = format!(
        "SELECT {TRAIN_COLUMNS} FROM t_ticker_tape_train WHERE user_id=? AND type=? ORDER BY create_time DESC LIMIT 1"
    );
    match sqlx::query(&sql)
        .bind(&user.id)
        .bind(train_type)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => {
            let body = super::bean_conversion::string_to_map_list(
                row.get::<Option<&str>, _>("code_message_body"),
            )
            .unwrap_or(Value::Null);
            envelope_json(
                StatusCode::OK,
                200,
                Some(train_json_with_body(&row, body)),
                "ok",
                "",
            )
        }
        Ok(None) => envelope_json(StatusCode::OK, 200, Some(empty_train()), "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn statistics(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match crate::statistical::load_or_create(pool, "t_ticker_tape_train_statistical", &user.id, 3)
        .await
    {
        Ok(rows) => envelope_json(StatusCode::OK, 200, Some(Value::Array(rows)), "ok", ""),
        Err(error) => ticker_error(error, "list statistics"),
    }
}

pub(crate) async fn settings(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<Vec<Value>, sqlx::Error> = async {
        sqlx::query("SELECT id,type,rate,text,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_time_text FROM t_ticker_tape_train_setting")
            .fetch_all(pool).await?.iter().map(|row| {
                Ok(json!({
                    "id": row.try_get::<String, _>("id")?,
                    "type": row.try_get::<Option<String>, _>("type")?,
                    "rate": row.try_get::<Option<i32>, _>("rate")?,
                    "text": row.try_get::<Option<String>, _>("text")?,
                    "createTime": row.try_get::<Option<String>, _>("create_time_text")?
                }))
            }).collect()
    }.await;
    match result {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", ""),
        Err(error) => {
            tracing::warn!(%error, "ticker tape setting query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

// These global setting tables have no indexes, including on id. Keep the actual
// whole-set DELETE and serialize replacements even when the current set is empty.
// The dedicated session retains its pool permit and closes on cancellation/error.
pub(super) async fn settings_connection(
    pool: &sqlx::MySqlPool,
    domain: &str,
) -> Result<PoolConnection<MySql>, sqlx::Error> {
    let mut connection = pool.acquire().await?;
    connection.close_on_drop();
    let acquired: Option<i64> = sqlx::query_scalar(
        "SELECT GET_LOCK(CONCAT('receive-settings:', LEFT(SHA2(CONCAT(DATABASE(), ':', ?),256),47)),10)",
    )
    .bind(domain)
    .fetch_one(&mut *connection)
    .await?;
    if acquired != Some(1) {
        return Err(sqlx::Error::Protocol(
            "receive settings lock unavailable".into(),
        ));
    }
    Ok(connection)
}

#[derive(Debug, Deserialize, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SettingRequest {
    pub(super) param_list: Option<Vec<SettingRow>>,
}

impl SettingRequest {
    pub(super) fn replacement_rows(&self) -> Result<&[SettingRow], &'static str> {
        self.param_list.as_deref().ok_or("配置不能为空")
    }
}

#[derive(Debug, Deserialize, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct SettingRow {
    pub(super) id: Option<String>,
    #[serde(rename = "type")]
    pub(super) setting_type: Option<String>,
    #[serde(default, deserialize_with = "deserialize_setting_rate")]
    pub(super) rate: Option<i32>,
    pub(super) text: Option<String>,
    pub(super) create_time: Option<chrono::NaiveDateTime>,
}

fn deserialize_setting_rate<'de, D>(deserializer: D) -> Result<Option<i32>, D::Error>
where
    D: serde::Deserializer<'de>,
{
    let value = Option::<Value>::deserialize(deserializer)?;
    super::entering::request_integer(value.as_ref())
        .map_err(|_| serde::de::Error::custom("invalid setting rate"))
}

#[derive(Debug, Deserialize, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct StageSettingRequest {
    id: Option<i32>,
    stage_array: Option<String>,
}

pub(crate) async fn setting_add_or_update(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    body: Result<Json<SettingRequest>, JsonRejection>,
) -> Response {
    let Json(body) = match body {
        Ok(body) => body,
        Err(error) => {
            return if error.status() == StatusCode::UNSUPPORTED_MEDIA_TYPE {
                StatusCode::UNSUPPORTED_MEDIA_TYPE.into_response()
            } else {
                StatusCode::BAD_REQUEST.into_response()
            }
        }
    };
    if !user.is_admin {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    // Missing/null is not permission to erase the global set. An explicit [] is.
    let rows = match body.replacement_rows() {
        Ok(rows) => rows,
        Err(message) => return envelope_response(StatusCode::OK, 202, message, message),
    };
    // Java's generated-ID entities cannot be merged after the bulk deletion.
    // Reject supplied IDs before deleting anything, preserving the same rollback result.
    if rows.iter().any(|row| row.id.is_some()) {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    let result: Result<(), sqlx::Error> = async {
        let mut connection = settings_connection(pool, "personal").await?;
        let mut tx = connection.begin().await?;
        sqlx::query("DELETE FROM t_ticker_tape_train_setting")
            .execute(&mut *tx).await?;
        for row in rows {
            let id = uuid::Uuid::new_v4().to_string();
            sqlx::query("INSERT INTO t_ticker_tape_train_setting (id,type,rate,text,create_time,dot) VALUES (?,?,?,?,?,NULL)")
                .bind(id).bind(&row.setting_type).bind(row.rate).bind(&row.text)
                .bind(row.create_time).execute(&mut *tx).await?;
        }
        tx.commit().await
    }.await;
    match result {
        Ok(()) => envelope_json(StatusCode::OK, 200, Some(json!(body)), "ok", ""),
        Err(error) => {
            tracing::warn!(%error, "ticker tape setting replacement failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(crate) async fn stage_settings(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<StageSettingRequest, sqlx::Error> = async {
        match sqlx::query("SELECT id,stage_array FROM t_ticker_tape_train_stage_setting LIMIT 1")
            .fetch_optional(pool)
            .await?
        {
            Some(row) => Ok(StageSettingRequest {
                id: row.try_get("id")?,
                stage_array: row.try_get("stage_array")?,
            }),
            None => Ok(StageSettingRequest {
                id: None,
                stage_array: None,
            }),
        }
    }
    .await;
    match result {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", ""),
        Err(error) => {
            tracing::warn!(%error, "ticker tape stage setting query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(crate) async fn stage_setting_add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    body: Result<Json<StageSettingRequest>, JsonRejection>,
) -> Response {
    let Json(mut body) = match body {
        Ok(body) => body,
        Err(error) => {
            return if error.status() == StatusCode::UNSUPPORTED_MEDIA_TYPE {
                StatusCode::UNSUPPORTED_MEDIA_TYPE.into_response()
            } else {
                StatusCode::BAD_REQUEST.into_response()
            }
        }
    };
    if !user.is_admin {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<bool, sqlx::Error> = async {
        let mut tx = pool.begin().await?;
        if let Some(id) = body.id {
            if sqlx::query("SELECT id FROM t_ticker_tape_train_stage_setting WHERE id=? FOR UPDATE")
                .bind(id)
                .fetch_optional(&mut *tx)
                .await?
                .is_none()
            {
                tx.rollback().await?;
                return Ok(false);
            }
            sqlx::query("UPDATE t_ticker_tape_train_stage_setting SET stage_array=? WHERE id=?")
                .bind(&body.stage_array)
                .bind(id)
                .execute(&mut *tx)
                .await?;
        } else {
            let inserted = sqlx::query(
                "INSERT INTO t_ticker_tape_train_stage_setting (stage_array) VALUES (?)",
            )
            .bind(&body.stage_array)
            .execute(&mut *tx)
            .await?;
            body.id = Some(
                i32::try_from(inserted.last_insert_id())
                    .map_err(|error| sqlx::Error::Decode(Box::new(error)))?,
            );
        }
        tx.commit().await?;
        Ok(true)
    }
    .await;
    match result {
        Ok(true) => envelope_json(StatusCode::OK, 200, Some(json!(body)), "ok", ""),
        Ok(false) => envelope_response(
            StatusCode::OK,
            202,
            "未查询到该阶段设置",
            "未查询到该阶段设置",
        ),
        Err(error) => {
            tracing::warn!(%error, "ticker tape stage setting save failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

#[derive(Debug, Deserialize)]
pub(crate) struct AddRequest {
    rate: Option<i32>,
    #[serde(rename = "type")]
    train_type: Option<i32>,
    #[serde(rename = "codeShort")]
    code_short: Option<i32>,
    #[serde(rename = "codeMessageBody")]
    code_message_body: Option<Value>,
    name: Option<String>,
    #[serde(rename = "isLowRate")]
    is_low_rate: Option<i32>,
}
#[derive(Debug, Deserialize)]
pub(crate) struct UpdateRequest {
    id: Option<String>,
    mark: Option<String>,
    schedule: Option<i32>,
}
#[derive(Debug, Deserialize)]
pub(crate) struct BaseRequest {
    id: Option<String>,
    mark: Option<String>,
    schedule: Option<i32>,
}

fn ticker_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error=%error, operation, "ticker write failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}
/// Lifecycle state of one training, read under `FOR UPDATE`.
struct Locked {
    train_type: Option<i32>,
    status: i32,
    accumulated: i64,
    active_ms: Option<i64>,
}

impl Locked {
    /// Java `accrue`: accumulated millis plus the time since the clock was last started.
    fn accrued(&self) -> i64 {
        self.accumulated.saturating_add(self.active_ms.unwrap_or(0))
    }
}

/// Java `ownedForUpdate`: a missing training is IllegalArgumentException (202), someone
/// else's training is ForbiddenException (207).
async fn lock_owned(
    tx: &mut Transaction<'_, MySql>,
    id: &str,
    user_id: &str,
) -> Result<Locked, Response> {
    let row = sqlx::query("SELECT user_id,type,status,CAST(COALESCE(accumulated_active_millis,0) AS SIGNED) AS accumulated,CAST(CASE WHEN active_since IS NULL THEN NULL ELSE GREATEST(0,TIMESTAMPDIFF(MICROSECOND,active_since,NOW(6)) DIV 1000) END AS SIGNED) AS active_ms FROM t_ticker_tape_train WHERE id=? FOR UPDATE")
        .bind(id)
        .fetch_optional(&mut **tx)
        .await
        .map_err(|e| ticker_error(e, "lock train"))?;
    let Some(row) = row else {
        return Err(envelope_response(
            StatusCode::OK,
            202,
            "未查询到训练",
            "未查询到训练",
        ));
    };
    if row.get::<Option<String>, _>("user_id").as_deref() != Some(user_id) {
        return Err(envelope_response(StatusCode::OK, 207, "无权限", ""));
    }
    Ok(Locked {
        train_type: row.get::<Option<i32>, _>("type"),
        status: row.get::<Option<i32>, _>("status").unwrap_or(0),
        accumulated: row.get::<i64, _>("accumulated"),
        active_ms: row.get::<Option<i64>, _>("active_ms"),
    })
}

/// Writes the aggregate into the user's statistics row of `stat_type`, creating it when absent.
async fn upsert_statistics(
    tx: &mut Transaction<'_, MySql>,
    user_id: &str,
    stat_type: i32,
    aggregate: &MySqlRow,
) -> Result<(), sqlx::Error> {
    let total_time = aggregate.get::<i64, _>("total_time").to_string();
    let total_count = aggregate.get::<i64, _>("total_count") as i32;
    let avg_speed = aggregate.get::<Decimal, _>("avg_speed");
    let existing: Option<String> = sqlx::query_scalar(
        "SELECT id FROM t_ticker_tape_train_statistical WHERE user_id=? AND type=? LIMIT 1",
    )
    .bind(user_id)
    .bind(stat_type)
    .fetch_optional(&mut **tx)
    .await?;
    match existing {
        Some(id) => {
            sqlx::query("UPDATE t_ticker_tape_train_statistical SET total_count=?,total_time=?,avg_speed=? WHERE id=?")
                .bind(total_count)
                .bind(&total_time)
                .bind(avg_speed)
                .bind(id)
                .execute(&mut **tx)
                .await?;
        }
        None => {
            sqlx::query("INSERT INTO t_ticker_tape_train_statistical (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,?,?,?,?,?)")
                .bind(uuid::Uuid::new_v4().to_string())
                .bind(stat_type)
                .bind(total_count)
                .bind(&total_time)
                .bind(avg_speed)
                .bind(user_id)
                .execute(&mut **tx)
                .await?;
        }
    }
    Ok(())
}

/// Java `finishStatistical`: finished letter/word/mixed trainings (type < 3) feed statistics type 2.
async fn finish_statistical(
    tx: &mut Transaction<'_, MySql>,
    user_id: &str,
) -> Result<(), sqlx::Error> {
    let aggregate = sqlx::query("SELECT CAST(COALESCE(SUM(valid_time),0) AS SIGNED) AS total_time,COUNT(id) AS total_count,CAST(IFNULL(AVG(rate),0) AS DECIMAL(14,4)) AS avg_speed FROM t_ticker_tape_train WHERE user_id=? AND type<3 AND status=3")
        .bind(user_id)
        .fetch_one(&mut **tx)
        .await?;
    upsert_statistics(tx, user_id, 2, &aggregate).await
}

/// Java `saveStatistical`: base (21) and department (22) sessions aggregate every session of
/// that type into statistics type 0 (base) or 1 (department).
async fn save_statistical(
    tx: &mut Transaction<'_, MySql>,
    user_id: &str,
    train_type: Option<i32>,
) -> Result<(), sqlx::Error> {
    let aggregate = sqlx::query("SELECT CAST(COALESCE(SUM(valid_time),0) AS SIGNED) AS total_time,COUNT(id) AS total_count,CAST(IFNULL(AVG(rate),0) AS DECIMAL(14,4)) AS avg_speed FROM t_ticker_tape_train WHERE user_id=? AND type<=>?")
        .bind(user_id)
        .bind(train_type)
        .fetch_one(&mut **tx)
        .await?;
    let stat_type = if train_type == Some(22) { 1 } else { 0 };
    upsert_statistics(tx, user_id, stat_type, &aggregate).await
}

/// Java: the user's previous training of this type is dropped when it never started, and
/// finished (with statistics) when it was paused.
///
/// The caller holds the user row lock (`statistical::lock_user`). The previous training is found
/// with a plain read and then locked by primary key, so only that row is held: the table is
/// indexed by primary key only, and a locking read on `user_id` would lock every row it scans.
async fn settle_previous(
    tx: &mut Transaction<'_, MySql>,
    user_id: &str,
    kind: i32,
) -> Result<(), sqlx::Error> {
    let latest: Option<String> = sqlx::query_scalar("SELECT id FROM t_ticker_tape_train WHERE user_id=? AND type=? ORDER BY create_time DESC LIMIT 1")
        .bind(user_id)
        .bind(kind)
        .fetch_optional(&mut **tx)
        .await?;
    let Some(previous) = latest else {
        return Ok(());
    };
    let Some(last) = sqlx::query("SELECT status FROM t_ticker_tape_train WHERE id=? FOR UPDATE")
        .bind(&previous)
        .fetch_optional(&mut **tx)
        .await?
    else {
        // Deleted by another request between the read and the lock.
        return Ok(());
    };
    match last.get::<Option<i32>, _>("status") {
        Some(0) => {
            sqlx::query("DELETE FROM t_ticker_tape_train WHERE id=?")
                .bind(&previous)
                .execute(&mut **tx)
                .await?;
        }
        Some(2) => {
            sqlx::query("UPDATE t_ticker_tape_train SET status=3 WHERE id=?")
                .bind(&previous)
                .execute(&mut **tx)
                .await?;
            finish_statistical(tx, user_id).await?;
        }
        _ => {}
    }
    Ok(())
}

pub(crate) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<AddRequest>,
) -> Response {
    let Some(kind) = body.train_type else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return ticker_error(e, "start add"),
    };
    // User row first (see statistical::lock_user): cleanup and the lazily built statistics rows
    // of one user cannot race, and no locking read has to scan the unindexed tables.
    match lock_user(&mut tx, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(e) => return ticker_error(e, "lock user for add"),
    }
    if let Err(e) = settle_previous(&mut tx, &user.id, kind).await {
        return ticker_error(e, "settle previous");
    }
    let id = uuid::Uuid::new_v4().to_string();
    let content = body
        .code_message_body
        .as_ref()
        .map(|v| v.to_string())
        .unwrap_or_else(|| "[]".into());
    if let Err(e) = sqlx::query("INSERT INTO t_ticker_tape_train (id,rate,type,code_message_body,code_short,status,name,user_id,mark,schedule,is_low_rate,protocol_version,accumulated_active_millis,valid_time,create_time) VALUES (?,?,?,?,?,0,?,?,?,0,?,1,0,'0',NOW())")
        .bind(&id)
        .bind(body.rate)
        .bind(kind)
        .bind(content)
        .bind(body.code_short)
        .bind(body.name.as_deref())
        .bind(&user.id)
        .bind("0,0")
        .bind(body.is_low_rate)
        .execute(&mut *tx)
        .await
    {
        return ticker_error(e, "add");
    }
    if let Err(e) = tx.commit().await {
        return ticker_error(e, "commit add");
    }
    // Java echoes the request (TickerTapeTrainAddParam) with the generated id.
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({
            "id": id,
            "rate": body.rate,
            "type": kind,
            "codeShort": body.code_short,
            "codeMessageBody": body.code_message_body,
            "name": body.name,
            "isLowRate": body.is_low_rate
        })),
        "ok",
        "",
    )
}

/// Java `begin`/`goOn`: a finished training stays finished; otherwise the training starts (or
/// resumes) and its clock is armed unless it is already running.
pub(crate) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<IdRequest>,
) -> Response {
    let Some(id) = body.id.filter(|v| !v.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return ticker_error(e, "start begin"),
    };
    let locked = match lock_owned(&mut tx, &id, &user.id).await {
        Ok(locked) => locked,
        Err(response) => return response,
    };
    if locked.status == 3 {
        return envelope_response(StatusCode::OK, 200, "ok", "");
    }
    // Assignments run left to right: active_since must read the old status.
    if let Err(e) = sqlx::query("UPDATE t_ticker_tape_train SET start_time=COALESCE(start_time,NOW(6)),active_since=IF(NOT (status<=>1),NOW(6),active_since),status=1 WHERE id=?")
        .bind(&id)
        .execute(&mut *tx)
        .await
    {
        return ticker_error(e, "begin");
    }
    if let Err(e) = tx.commit().await {
        return ticker_error(e, "commit begin");
    }
    envelope_response(StatusCode::OK, 200, "ok", "")
}

/// Java `pause` (terminal = false) and `finish` (terminal = true).
async fn update_clock(
    state: AppState,
    user: AuthUser,
    body: UpdateRequest,
    terminal: bool,
) -> Response {
    let Some(id) = body.id.filter(|v| !v.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return ticker_error(e, "start lifecycle"),
    };
    // Finishing feeds the statistics rows, so it takes the user row first like `add`.
    if terminal {
        match lock_user(&mut tx, &user.id).await {
            Ok(true) => {}
            Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
            Err(e) => return ticker_error(e, "lock user for finish"),
        }
    }
    let locked = match lock_owned(&mut tx, &id, &user.id).await {
        Ok(locked) => locked,
        Err(response) => return response,
    };
    if locked.status == 3 {
        return envelope_response(StatusCode::OK, 208, "训练已结束", "");
    }
    if !terminal && locked.status == 2 {
        return envelope_response(StatusCode::OK, 200, "ok", "");
    }
    let accumulated = locked.accrued();
    let valid_time = (accumulated / 1000).to_string();
    let sql = if terminal {
        "UPDATE t_ticker_tape_train SET accumulated_active_millis=?,valid_time=?,mark=?,schedule=?,status=3,end_time=NOW(6),active_since=NULL WHERE id=?"
    } else {
        "UPDATE t_ticker_tape_train SET accumulated_active_millis=?,valid_time=?,mark=?,schedule=?,status=2,active_since=NULL WHERE id=?"
    };
    if let Err(e) = sqlx::query(sql)
        .bind(accumulated)
        .bind(&valid_time)
        .bind(body.mark.as_deref())
        .bind(body.schedule)
        .bind(&id)
        .execute(&mut *tx)
        .await
    {
        return ticker_error(e, "update lifecycle");
    }
    if terminal {
        let statistics = match locked.train_type {
            Some(21) | Some(22) => save_statistical(&mut tx, &user.id, locked.train_type).await,
            _ => finish_statistical(&mut tx, &user.id).await,
        };
        if let Err(e) = statistics {
            return ticker_error(e, "update statistics");
        }
    }
    if let Err(e) = tx.commit().await {
        return ticker_error(e, "commit lifecycle");
    }
    envelope_response(StatusCode::OK, 200, "ok", "")
}
pub(crate) async fn pause(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<UpdateRequest>,
) -> Response {
    update_clock(state, user, body, false).await
}
/// Java `goOn` is `begin`.
pub(crate) async fn resume(
    state: State<AppState>,
    user: Extension<AuthUser>,
    body: Json<IdRequest>,
) -> Response {
    begin(state, user, body).await
}
pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<UpdateRequest>,
) -> Response {
    update_clock(state, user, body, true).await
}
/// Java `createBaseSession`: an empty base (21) or department (22) session.
pub(crate) async fn base_session(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TypeRequest>,
) -> Response {
    let Some(kind) = body.train_type.filter(|v| *v == 21 || *v == 22) else {
        return envelope_response(StatusCode::OK, 202, "基础训练类型无效", "基础训练类型无效");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = uuid::Uuid::new_v4().to_string();
    if let Err(e) = sqlx::query("INSERT INTO t_ticker_tape_train (id,type,status,user_id,protocol_version,accumulated_active_millis,valid_time,mark,schedule,create_time) VALUES (?,?,0,?,1,0,'0','0',0,NOW())")
        .bind(&id)
        .bind(kind)
        .bind(&user.id)
        .execute(pool)
        .await
    {
        return ticker_error(e, "create base session");
    }
    let sql = format!("SELECT {TRAIN_COLUMNS} FROM t_ticker_tape_train WHERE id=?");
    match sqlx::query(&sql).bind(&id).fetch_one(pool).await {
        Ok(row) => match train_json(&row) {
            Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
            Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
        },
        Err(e) => ticker_error(e, "reload base session"),
    }
}
/// Java `saveBaseTrain`: finishes the session (once) and refreshes the base/department statistics.
pub(crate) async fn save_base(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<BaseRequest>,
) -> Response {
    let Some(id) = body.id.filter(|v| !v.trim().is_empty()) else {
        return envelope_response(
            StatusCode::OK,
            202,
            "收报会话编号不能为空",
            "收报会话编号不能为空",
        );
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return ticker_error(e, "start save base"),
    };
    match lock_user(&mut tx, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(e) => return ticker_error(e, "lock user for save base"),
    }
    let locked = match lock_owned(&mut tx, &id, &user.id).await {
        Ok(locked) => locked,
        Err(response) => return response,
    };
    if locked.status != 3 {
        let accumulated = locked.accrued();
        if let Err(e) = sqlx::query("UPDATE t_ticker_tape_train SET accumulated_active_millis=?,valid_time=?,mark=?,schedule=?,status=3,end_time=NOW(6),active_since=NULL WHERE id=?")
            .bind(accumulated)
            .bind((accumulated / 1000).to_string())
            .bind(body.mark.as_deref())
            .bind(body.schedule)
            .bind(&id)
            .execute(&mut *tx)
            .await
        {
            return ticker_error(e, "save base");
        }
        if let Err(e) = save_statistical(&mut tx, &user.id, locked.train_type).await {
            return ticker_error(e, "save base statistics");
        }
    }
    if let Err(e) = tx.commit().await {
        return ticker_error(e, "commit save base");
    }
    envelope_response(StatusCode::OK, 200, "ok", "")
}
/// Java `discardBaseSession`: only a session that never started is deleted.
pub(crate) async fn discard_base(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<IdRequest>,
) -> Response {
    let Some(id) = body.id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return ticker_error(e, "start discard base"),
    };
    let locked = match lock_owned(&mut tx, &id, &user.id).await {
        Ok(locked) => locked,
        Err(response) => return response,
    };
    if locked.status == 0 {
        if let Err(e) = sqlx::query("DELETE FROM t_ticker_tape_train WHERE id=?")
            .bind(&id)
            .execute(&mut *tx)
            .await
        {
            return ticker_error(e, "discard base");
        }
    }
    if let Err(e) = tx.commit().await {
        return ticker_error(e, "commit discard base");
    }
    envelope_response(StatusCode::OK, 200, "ok", "")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn json_body_reader_keeps_java_empty_text_guard() {
        assert_eq!(parse_message_body(None).unwrap(), Value::Null);
        assert_eq!(
            parse_message_body(Some(String::new())).unwrap(),
            Value::Null
        );
        for whitespace in [" ", "\t\r\n", " \t\r\n "] {
            assert_eq!(
                parse_message_body(Some(whitespace.into())).unwrap(),
                Value::Null
            );
        }
        assert!(parse_message_body(Some("\u{a0}".into())).is_err());
        assert!(parse_message_body(Some(" \tinvalid".into())).is_err());
        assert_eq!(parse_message_body(Some("[]".into())).unwrap(), json!([]));
        assert!(parse_message_body(Some("not json".into())).is_err());
    }

    #[tokio::test]
    #[ignore = "requires an isolated MySQL database with the project schema (RUST_PHASE2_DATABASE_URL)"]
    async fn last_train_applies_bean_conversion_without_json_parsing() -> Result<(), sqlx::Error> {
        use http_body_util::BodyExt;
        use std::sync::{atomic::AtomicBool, Arc};

        let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("isolated database URL");
        let pool = sqlx::mysql::MySqlPoolOptions::new().connect(&url).await?;
        let user = AuthUser {
            id: uuid::Uuid::new_v4().to_string(),
            is_admin: false,
        };
        let id = uuid::Uuid::new_v4().to_string();
        sqlx::query("INSERT INTO t_ticker_tape_train (id,type,status,user_id,code_message_body,create_time) VALUES (?,1,0,?,'[]',NOW(6))")
            .bind(&id).bind(&user.id).execute(&pool).await?;
        let state = AppState {
            config: Arc::new(crate::platform::config::AppConfig::from_env().unwrap()),
            pool: Some(pool.clone()),
            database_ready: Arc::new(AtomicBool::new(true)),
        };
        for (stored, expected) in [
            ("", json!([])),
            ("   ", json!([])),
            (",", json!([])),
            ("not json", Value::Null),
            ("[]", json!([])),
            ("[{\"codeMessage\":\"1234\",\"sort\":1}]", Value::Null),
        ] {
            sqlx::query("UPDATE t_ticker_tape_train SET code_message_body=? WHERE id=?")
                .bind(stored)
                .bind(&id)
                .execute(&pool)
                .await?;
            let response = last_train(
                State(state.clone()),
                Extension(user.clone()),
                Json(TypeRequest {
                    train_type: Some(1),
                }),
            )
            .await;
            assert_eq!(response.status(), StatusCode::OK, "{stored:?}");
            let bytes = response.into_body().collect().await.unwrap().to_bytes();
            let body: Value = serde_json::from_slice(&bytes).unwrap();
            assert_eq!(body["code"], 200, "{stored:?}");
            assert_eq!(body["data"]["codeMessageBody"], expected, "{stored:?}");
        }
        for stored in ["", "   ", "\t\r\n", " \t\r\n "] {
            sqlx::query("UPDATE t_ticker_tape_train SET code_message_body=? WHERE id=?")
                .bind(stored)
                .bind(&id)
                .execute(&pool)
                .await?;
            let detail = by_id(
                State(state.clone()),
                Extension(user.clone()),
                Json(IdRequest {
                    id: Some(id.clone()),
                }),
            )
            .await;
            let history = list(
                State(state.clone()),
                Extension(user.clone()),
                Json(PageRequest {
                    page: Some(1),
                    rows: Some(10),
                }),
            )
            .await;
            for (response, is_page) in [(detail, false), (history, true)] {
                assert_eq!(response.status(), StatusCode::OK);
                let bytes = response.into_body().collect().await.unwrap().to_bytes();
                let body: Value = serde_json::from_slice(&bytes).unwrap();
                assert_eq!(body["code"], 200);
                let train = if is_page {
                    &body["data"]["data"][0]
                } else {
                    &body["data"]
                };
                assert_eq!(train["id"], id);
                assert_eq!(train["type"], 1);
                assert_eq!(train["status"], 0);
                assert_eq!(train["codeMessageBody"], Value::Null);
            }
        }
        // Invalid non-empty JSON must still fail both JSON-reading callers.
        sqlx::query("UPDATE t_ticker_tape_train SET code_message_body=' not json ' WHERE id=?")
            .bind(&id)
            .execute(&pool)
            .await?;
        let detail = by_id(
            State(state.clone()),
            Extension(user.clone()),
            Json(IdRequest {
                id: Some(id.clone()),
            }),
        )
        .await;
        let history = list(
            State(state),
            Extension(user),
            Json(PageRequest {
                page: Some(1),
                rows: Some(10),
            }),
        )
        .await;
        for response in [detail, history] {
            assert_eq!(response.status(), StatusCode::INTERNAL_SERVER_ERROR);
        }
        sqlx::query("DELETE FROM t_ticker_tape_train WHERE id=?")
            .bind(&id)
            .execute(&pool)
            .await?;
        Ok(())
    }

    /// Settling one user's paused predecessor (which also refreshes that user's statistics row)
    /// must leave every other user's training and statistics rows writable. Both tables are
    /// indexed by primary key only, so a locking read on `user_id` would lock every row it scans
    /// and stall unrelated users' saves until commit.
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
            sqlx::query("INSERT INTO t_ticker_tape_train (id,type,status,user_id,rate,valid_time,create_time) VALUES (?,1,2,?,10,'20',NOW(6))")
                .bind(format!("train-{user}"))
                .bind(user)
                .execute(&pool)
                .await?;
            sqlx::query("INSERT INTO t_ticker_tape_train_statistical (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,2,0,'0',0,?)")
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
        let training = sqlx::query("UPDATE t_ticker_tape_train SET name='x' WHERE id=?")
            .bind(format!("train-{other}"))
            .execute(&mut *bystander)
            .await;
        let statistics =
            sqlx::query("UPDATE t_ticker_tape_train_statistical SET total_count=7 WHERE id=?")
                .bind(format!("stat-{other}"))
                .execute(&mut *bystander)
                .await;
        // The owner's own rows were really settled (finished + counted) inside the transaction.
        let settled: (Option<i32>, Option<i32>) = (
            sqlx::query_scalar("SELECT status FROM t_ticker_tape_train WHERE id=?")
                .bind(format!("train-{owner}"))
                .fetch_one(&mut *tx)
                .await?,
            sqlx::query_scalar(
                "SELECT total_count FROM t_ticker_tape_train_statistical WHERE id=?",
            )
            .bind(format!("stat-{owner}"))
            .fetch_one(&mut *tx)
            .await?,
        );
        tx.rollback().await?;
        drop(bystander);
        for user in [&owner, &other] {
            sqlx::query("DELETE FROM t_ticker_tape_train WHERE user_id=?")
                .bind(user)
                .execute(&pool)
                .await?;
            sqlx::query("DELETE FROM t_ticker_tape_train_statistical WHERE user_id=?")
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

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::platform::auth::{authenticate, require_admin};
    use axum::{middleware, routing::post, Router};

    let mut router = Router::new();
    let train = Router::new()
        .route("/listPage", post(list))
        .route("/getById", post(by_id))
        .route("/add", post(add))
        .route("/begin", post(begin))
        .route("/pause", post(pause))
        .route("/goOn", post(resume))
        .route("/finish", post(finish))
        .route("/baseSession", post(base_session))
        .route("/saveBaseTrain", post(save_base))
        .route("/discardBaseSession", post(discard_base))
        .route("/statisticalPage", post(statistics))
        .route("/lastTrain", post(last_train))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let setting = Router::new()
        .route("/findAll", post(settings))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_setting = Router::new()
        .route("/addOrUpdate", post(setting_add_or_update))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let stage_setting = Router::new()
        .route("/findAll", post(stage_settings))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_stage_setting = Router::new()
        .route("/add", post(stage_setting_add))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router
        .nest("/api/tickerTapeTrain", train)
        .nest("/api/tickerTapeTrainSetting", setting.merge(admin_setting))
        .nest(
            "/api/tickerTapeTrainStageSetting",
            stage_setting.merge(admin_stage_setting),
        );
    router
}
