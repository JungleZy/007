use axum::{extract::State, http::StatusCode, response::Response};
use serde_json::json;
use sqlx::Row;

use super::{envelope_json, envelope_response, AppState};

pub(crate) async fn list_devices(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id, device_id, name, image, `option`, is_enable, create_time FROM t_equipment_device ORDER BY create_time")
        .fetch_all(pool).await {
        Ok(rows) => envelope_json(StatusCode::OK, 200, Some(json!(rows.iter().map(|row| json!({
            "id": row.get::<String, _>("id"),
            "deviceId": row.get::<Option<String>, _>("device_id"),
            "name": row.get::<Option<String>, _>("name"),
            "image": row.get::<Option<String>, _>("image"),
            "option": row.get::<Option<String>, _>("option"),
            "isEnable": row.get::<Option<i32>, _>("is_enable"),
            "createTime": null
        })).collect::<Vec<_>>())), "ok", ""),
        Err(error) => { tracing::warn!(error = %error, "equipment device list query failed"); envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") }
    }
}

pub(crate) async fn list_key_points(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT name, key_points FROM t_equipment_device ORDER BY create_time").fetch_all(pool).await {
        Ok(rows) => envelope_json(StatusCode::OK, 200, Some(json!(rows.iter().map(|row| json!({"deviceName": row.get::<Option<String>, _>("name"), "connect": row.get::<Option<String>, _>("key_points")})).collect::<Vec<_>>())), "ok", ""),
        Err(error) => { tracing::warn!(error = %error, "equipment key points query failed"); envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") }
    }
}
