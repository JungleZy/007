use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;

use super::{envelope_json, envelope_response, AppState, AuthUser};

fn train_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id": row.get::<String,_>("id"), "userId": row.get::<Option<String>,_>("user_id"), "content": row.get::<Option<String>,_>("content"), "trainTime": null, "deviceId": row.get::<Option<String>,_>("device_id"), "trainName": row.get::<Option<String>,_>("train_name"), "trainStatus": row.get::<Option<i32>,_>("train_status"), "trainType": row.get::<Option<i32>,_>("train_type"), "deviceName": row.get::<Option<String>,_>("device_name")})
}

pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,user_id,content,device_id,train_name,train_status,train_type,device_name FROM t_equipment_train WHERE user_id=? ORDER BY train_time")
        .bind(&user.id).fetch_all(pool).await {
        Ok(rows) => envelope_json(StatusCode::OK, 200, Some(json!(rows.iter().map(train_json).collect::<Vec<_>>())), "ok", ""),
        Err(error) => { tracing::warn!(error = %error, "equipment train list query failed"); envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") }
    }
}

pub(crate) async fn detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,user_id,content,device_id,train_name,train_status,train_type,device_name FROM t_equipment_train WHERE id=? LIMIT 1").bind(id).fetch_optional(pool).await {
        Ok(Some(row)) if row.get::<Option<String>,_>("user_id").as_deref() == Some(user.id.as_str()) => envelope_json(StatusCode::OK, 200, Some(train_json(&row)), "ok", ""),
        Ok(Some(_)) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Ok(None) => envelope_response(StatusCode::OK, 500, "未查询到训练计划", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
    }
}
