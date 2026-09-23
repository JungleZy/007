use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::{MySql, Row, Transaction};

async fn lock_user(tx: &mut Transaction<'_, MySql>, user_id: &str) -> Result<bool, sqlx::Error> {
    sqlx::query("SELECT id FROM t_user WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(user_id)
        .fetch_optional(&mut **tx)
        .await
        .map(|row| row.is_some())
}
fn train(body: &Value) -> &Value {
    body.get("train").unwrap_or(body)
}
fn error(error: sqlx::Error, op: &'static str) -> Response {
    tracing::warn!(error=%error,operation=op,"telegram database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

pub(crate) async fn save(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let train = train(&body);
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return error(e, "start telegram save"),
    };
    match lock_user(&mut tx, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(e) => return error(e, "lock telegram user"),
    };
    let id = train
        .get("id")
        .and_then(Value::as_str)
        .filter(|id| !id.is_empty())
        .map(str::to_owned)
        .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    let exists = match sqlx::query(
        "SELECT create_user_id FROM t_telegram_train WHERE id=? LIMIT 1 FOR UPDATE",
    )
    .bind(&id)
    .fetch_optional(&mut *tx)
    .await
    {
        Ok(row) => row,
        Err(e) => return error(e, "load telegram train"),
    };
    if let Some(row) = &exists {
        if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
            return envelope_response(StatusCode::OK, 207, "无权限", "");
        }
    }
    let values = [
        train.get("title").and_then(Value::as_str),
        train.get("startTime").and_then(Value::as_str),
        train.get("pauseTime").and_then(Value::as_str),
        train.get("endTime").and_then(Value::as_str),
        train.get("sustainTime").and_then(Value::as_str),
        train.get("createTime").and_then(Value::as_str),
        train.get("nowFloorId").and_then(Value::as_str),
    ];
    let nums = [
        train.get("type").and_then(Value::as_i64).unwrap_or(0) as i32,
        train.get("status").and_then(Value::as_i64).unwrap_or(0) as i32,
        train
            .get("totalKnockNumber")
            .and_then(Value::as_i64)
            .unwrap_or(0) as i32,
        train
            .get("totalNumber")
            .and_then(Value::as_i64)
            .unwrap_or(0) as i32,
        train
            .get("errorNumber")
            .and_then(Value::as_i64)
            .unwrap_or(0) as i32,
    ];
    let result = if exists.is_some() {
        sqlx::query("UPDATE t_telegram_train SET title=?,pause_time=?,end_time=?,sustain_time=?,type=?,status=?,total_knock_number=?,total_number=?,error_number=?,now_floor_id=? WHERE id=? AND create_user_id=?").bind(values[0]).bind(values[2]).bind(values[3]).bind(values[4]).bind(nums[0]).bind(nums[1]).bind(nums[2]).bind(nums[3]).bind(nums[4]).bind(values[6]).bind(&id).bind(&user.id).execute(&mut *tx).await
    } else {
        sqlx::query("INSERT INTO t_telegram_train (id,title,sustain_time,type,status,total_knock_number,total_number,error_number,create_user_id,create_time,now_floor_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)").bind(&id).bind(values[0]).bind(values[4]).bind(nums[0]).bind(nums[1]).bind(nums[2]).bind(nums[3]).bind(nums[4]).bind(&user.id).bind(values[5]).bind(values[6]).execute(&mut *tx).await
    };
    if let Err(e) = result {
        return error(e, "save telegram train");
    };
    if let Err(e) = tx.commit().await {
        return error(e, "commit telegram save");
    };
    envelope_json(StatusCode::OK, 200, Some(json!({"id":id})), "ok", "")
}

pub(crate) async fn control(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let train = train(&body);
    let Some(id) = train.get("id").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "训练信息不能为空", "");
    };
    let kind = body
        .get("controlType")
        .and_then(Value::as_i64)
        .or_else(|| train.get("controlType").and_then(Value::as_i64));
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return error(e, "start telegram control"),
    };
    match lock_user(&mut tx, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(e) => return error(e, "lock telegram control user"),
    };
    let row=match sqlx::query("SELECT status,accumulated_active_millis FROM t_telegram_train WHERE id=? AND create_user_id=? LIMIT 1 FOR UPDATE").bind(id).bind(&user.id).fetch_optional(&mut *tx).await{Ok(Some(r))=>r,Ok(None)=>return envelope_response(StatusCode::OK,207,"无权限",""),Err(e)=>return error(e,"load telegram control")};
    let status = kind.unwrap_or(0);
    let new_status = match status {
        0 => 1,
        1 => 2,
        2 => 3,
        _ => return envelope_response(StatusCode::OK, 202, "请求参数错误", ""),
    };
    let now = Value::Null;
    let result = if new_status == 1 {
        sqlx::query("UPDATE t_telegram_train SET status=1,start_time=COALESCE(start_time,CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))*1000 AS CHAR)),active_since=CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))*1000 AS UNSIGNED) WHERE id=?").bind(id).execute(&mut *tx).await
    } else if new_status == 2 {
        sqlx::query("UPDATE t_telegram_train SET status=2,pause_time=CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))*1000 AS CHAR),active_since=NULL WHERE id=?").bind(id).execute(&mut *tx).await
    } else {
        sqlx::query("UPDATE t_telegram_train SET status=3,end_time=CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))*1000 AS CHAR),active_since=NULL WHERE id=?").bind(id).execute(&mut *tx).await
    };
    let _ = row;
    let _ = now;
    if let Err(e) = result {
        return error(e, "update telegram control");
    };
    if let Err(e) = tx.commit().await {
        return error(e, "commit telegram control");
    };
    envelope_response(StatusCode::OK, 200, "ok", "")
}
pub(crate) async fn start(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(mut body): Json<Value>,
) -> Response {
    if let Some(object) = body.as_object_mut() {
        object.insert("controlType".to_owned(), json!(0));
    }
    control(State(state), Extension(user), Json(body)).await
}
pub(crate) async fn pause(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(mut body): Json<Value>,
) -> Response {
    if let Some(object) = body.as_object_mut() {
        object.insert("controlType".to_owned(), json!(1));
    }
    control(State(state), Extension(user), Json(body)).await
}
pub(crate) async fn end(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(mut body): Json<Value>,
) -> Response {
    if let Some(object) = body.as_object_mut() {
        object.insert("controlType".to_owned(), json!(2));
    }
    control(State(state), Extension(user), Json(body)).await
}
