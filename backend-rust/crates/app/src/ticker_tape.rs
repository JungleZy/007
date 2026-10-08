use axum::{
    extract::{rejection::JsonRejection, Extension, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use rust_decimal::Decimal;
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, Row};

use super::{envelope_json, envelope_response, AppState, AuthUser};

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
        Some(value) => serde_json::from_str(&value),
        None => Ok(Value::Null),
    }
}

fn train_json(row: &MySqlRow) -> Result<Value, serde_json::Error> {
    Ok(json!({
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
        "codeMessageBody": parse_message_body(row.get::<Option<String>, _>("code_message_body"))?,
        "mark": row.get::<Option<String>, _>("mark"),
        "schedule": row.get::<Option<i32>, _>("schedule"),
        "isLowRate": row.get::<Option<i32>, _>("is_low_rate")
    }))
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
        Ok(None) => return envelope_response(StatusCode::OK, 202, "请求参数错误", ""),
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
        Ok(Some(row)) => match train_json(&row) {
            Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
            Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
        },
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
    let rows = match sqlx::query("SELECT id,type,total_count,total_time,avg_speed,user_id FROM t_ticker_tape_train_statistical WHERE user_id=? ORDER BY type")
        .bind(&user.id)
        .fetch_all(pool)
        .await
    {
        Ok(rows) => rows,
        Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    };
    let mut by_type = std::collections::HashMap::new();
    for row in rows {
        let train_type = row.get::<Option<i32>, _>("type").unwrap_or(0);
        by_type.insert(
            train_type,
            json!({
                "id": row.get::<String, _>("id"),
                "type": train_type,
                "totalCount": row.get::<Option<i32>, _>("total_count").unwrap_or(0),
                "totalTime": row.get::<Option<String>, _>("total_time").unwrap_or_else(|| "0".to_owned()),
                "avgSpeed": row.get::<Option<Decimal>, _>("avg_speed").unwrap_or(Decimal::ZERO),
                "userId": row.get::<Option<String>, _>("user_id")
            }),
        );
    }
    let data = (0..3)
        .map(|train_type| {
            by_type.remove(&train_type).unwrap_or_else(|| {
                json!({
                    "id": null,
                    "type": train_type,
                    "totalCount": 0,
                    "totalTime": "0",
                    "avgSpeed": Decimal::ZERO,
                    "userId": user.id
                })
            })
        })
        .collect::<Vec<_>>();
    envelope_json(StatusCode::OK, 200, Some(Value::Array(data)), "ok", "")
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

#[derive(Debug, Deserialize, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SettingRequest {
    param_list: Option<Vec<SettingRow>>,
}

#[derive(Debug, Deserialize, serde::Serialize)]
#[serde(rename_all = "camelCase")]
struct SettingRow {
    id: Option<String>,
    #[serde(rename = "type")]
    setting_type: Option<String>,
    rate: Option<i32>,
    text: Option<String>,
    create_time: Option<chrono::NaiveDateTime>,
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
    // Java's generated-ID entities cannot be merged after the bulk deletion.
    // Reject supplied IDs before deleting anything, preserving the same rollback result.
    if body
        .param_list
        .as_deref()
        .unwrap_or_default()
        .iter()
        .any(|row| row.id.is_some())
    {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    let result: Result<(), sqlx::Error> = async {
        let mut tx = pool.begin().await?;
        sqlx::query("DELETE FROM t_ticker_tape_train_setting")
            .execute(&mut *tx).await?;
        for row in body.param_list.as_deref().unwrap_or_default() {
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
    Json(mut body): Json<StageSettingRequest>,
) -> Response {
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
fn parse_seconds(value: Option<String>) -> i64 {
    value
        .and_then(|v| v.parse::<i64>().ok())
        .unwrap_or(0)
        .max(0)
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
    let id = uuid::Uuid::new_v4().to_string();
    let content = body
        .code_message_body
        .map(|v| v.to_string())
        .unwrap_or_else(|| "[]".into());
    match sqlx::query("INSERT INTO t_ticker_tape_train (id,rate,type,code_message_body,code_short,status,name,user_id,mark,schedule,is_low_rate,protocol_version,accumulated_active_millis,valid_time) VALUES (?,?,?,?,?,0,?,?,?,0,?,1,0,'0')").bind(&id).bind(body.rate).bind(kind).bind(content).bind(body.code_short).bind(body.name).bind(&user.id).bind("0,0").bind(body.is_low_rate).execute(pool).await{Ok(_)=>envelope_json(StatusCode::OK,200,Some(json!({"id":id})),"ok",""),Err(e)=>ticker_error(e,"add")}
}

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
    match sqlx::query("UPDATE t_ticker_tape_train SET status=1,start_time=COALESCE(start_time,NOW(6)),active_since=COALESCE(active_since,NOW(6)) WHERE id=? AND user_id=? AND status<>3").bind(&id).bind(&user.id).execute(pool).await{Ok(_)=>envelope_response(StatusCode::OK,200,"ok",""),Err(e)=>ticker_error(e,"begin")}
}

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
    let row = match sqlx::query("SELECT status,IF(active_since IS NULL,0,1) AS running,accumulated_active_millis,valid_time FROM t_ticker_tape_train WHERE id=? AND user_id=? LIMIT 1 FOR UPDATE")
        .bind(&id)
        .bind(&user.id)
        .fetch_optional(&mut *tx)
        .await
    {
        Ok(Some(r)) => r,
        Ok(None) => {
            // Java: a missing train is IllegalArgumentException -> 202; an existing foreign train -> 207.
            return match sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM t_ticker_tape_train WHERE id=?")
                .bind(&id)
                .fetch_one(&mut *tx)
                .await
            {
                Ok(0) => envelope_response(StatusCode::OK, 202, "未查询到训练", "未查询到训练"),
                Ok(_) => envelope_response(StatusCode::OK, 207, "无权限", ""),
                Err(e) => ticker_error(e, "lifecycle ownership"),
            };
        }
        Err(e) => return ticker_error(e, "load lifecycle"),
    };
    let status = row.get::<Option<i32>, _>("status").unwrap_or(0);
    if status == 3 {
        return envelope_response(StatusCode::OK, 208, "训练已结束", "");
    }
    let running = row.get::<i64, _>("running") != 0;
    let elapsed = if running { 1 } else { 0 };
    let next_status = if terminal {
        3
    } else if body.mark.is_some() || body.schedule.is_some() {
        2
    } else {
        1
    };
    let sql = if next_status == 3 {
        "UPDATE t_ticker_tape_train SET status=3,end_time=NOW(6),active_since=NULL,valid_time=? ,mark=COALESCE(?,mark),schedule=COALESCE(?,schedule) WHERE id=?"
    } else if next_status == 2 {
        "UPDATE t_ticker_tape_train SET status=2,active_since=NULL,valid_time=?,mark=COALESCE(?,mark),schedule=COALESCE(?,schedule) WHERE id=?"
    } else {
        "UPDATE t_ticker_tape_train SET status=1,active_since=COALESCE(active_since,NOW(6)),mark=COALESCE(?,mark),schedule=COALESCE(?,schedule) WHERE id=?"
    };
    let result = if next_status == 1 {
        sqlx::query(sql)
            .bind(body.mark)
            .bind(body.schedule)
            .bind(&id)
            .execute(&mut *tx)
            .await
    } else {
        sqlx::query(sql)
            .bind(parse_seconds(row.get("valid_time")).saturating_add(elapsed))
            .bind(body.mark)
            .bind(body.schedule)
            .bind(&id)
            .execute(&mut *tx)
            .await
    };
    if let Err(e) = result {
        return ticker_error(e, "update lifecycle");
    };
    if let Err(e) = tx.commit().await {
        return ticker_error(e, "commit lifecycle");
    };
    envelope_response(StatusCode::OK, 200, "ok", "")
}
pub(crate) async fn pause(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<UpdateRequest>,
) -> Response {
    update_clock(state, user, body, false).await
}
pub(crate) async fn resume(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<IdRequest>,
) -> Response {
    let update = UpdateRequest {
        id: body.id,
        mark: None,
        schedule: None,
    };
    update_clock(state, user, update, false).await
}
pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<UpdateRequest>,
) -> Response {
    update_clock(state, user, body, true).await
}
pub(crate) async fn base_session(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TypeRequest>,
) -> Response {
    let Some(kind) = body.train_type.filter(|v| *v == 21 || *v == 22) else {
        return envelope_response(StatusCode::OK, 202, "基础训练类型无效", "");
    };
    add(
        State(state),
        Extension(user),
        Json(AddRequest {
            rate: None,
            train_type: Some(kind),
            code_short: None,
            code_message_body: Some(Value::Array(Vec::new())),
            name: None,
            is_low_rate: None,
        }),
    )
    .await
}
pub(crate) async fn save_base(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<BaseRequest>,
) -> Response {
    let update = UpdateRequest {
        id: body.id,
        mark: body.mark,
        schedule: body.schedule,
    };
    finish(State(state), Extension(user), Json(update)).await
}
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
    match sqlx::query("DELETE FROM t_ticker_tape_train WHERE id=? AND user_id=? AND status=0")
        .bind(id)
        .bind(&user.id)
        .execute(pool)
        .await
    {
        Ok(_) => envelope_response(StatusCode::OK, 200, "ok", ""),
        Err(e) => ticker_error(e, "discard base"),
    }
}
