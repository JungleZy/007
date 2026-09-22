use std::collections::HashMap;

use axum::{extract::State, http::StatusCode, response::Response, Json};
use serde_json::{json, Value};
use sqlx::Row;

use super::{envelope_json, envelope_response, AppState};

pub(crate) async fn list_devices(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(type_id) = body.get("id").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let devices = match sqlx::query("SELECT id, device_name, device_number, device_img, device_type_id FROM t_device WHERE device_type_id = ? ORDER BY create_time")
        .bind(type_id).fetch_all(pool).await {
        Ok(rows) => rows,
        Err(error) => { tracing::warn!(error = %error, "device list query failed"); return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""); }
    };
    let descriptions = match sqlx::query(
        "SELECT id, device_id, title, content FROM t_device_description ORDER BY create_time",
    )
    .fetch_all(pool)
    .await
    {
        Ok(rows) => rows,
        Err(error) => {
            tracing::warn!(error = %error, "device description query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let mut by_device: HashMap<i32, Vec<Value>> = HashMap::new();
    for row in descriptions {
        let device_id = row.get::<Option<i32>, _>("device_id").unwrap_or_default();
        by_device.entry(device_id).or_default().push(json!({
            "id": row.get::<i32, _>("id"),
            "title": row.get::<Option<String>, _>("title"),
            "content": row.get::<Option<String>, _>("content")
        }));
    }
    let data = devices
        .into_iter()
        .map(|row| {
            let id = row.get::<i32, _>("id");
            json!({
                "id": id,
                "deviceName": row.get::<Option<String>, _>("device_name"),
                "deviceNumber": row.get::<Option<String>, _>("device_number"),
                "deviceImg": row.get::<Option<String>, _>("device_img"),
                "deviceTypeId": row.get::<Option<i32>, _>("device_type_id"),
                "createTime": null,
                "descriptions": by_device.remove(&id).unwrap_or_default()
            })
        })
        .collect::<Vec<_>>();
    envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", "")
}

pub(crate) async fn list_types(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT dt.id, dt.type_name, COUNT(d.id) AS exist_device FROM t_device_type dt LEFT JOIN t_device d ON d.device_type_id = dt.id GROUP BY dt.id, dt.type_name ORDER BY dt.create_time")
        .fetch_all(pool).await {
        Ok(rows) => envelope_json(StatusCode::OK, 200, Some(json!(rows.iter().map(|row| json!({
            "id": row.get::<i32, _>("id"),
            "typeName": row.get::<Option<String>, _>("type_name"),
            "existDevice": row.get::<i64, _>("exist_device")
        })).collect::<Vec<_>>())), "ok", ""),
        Err(error) => { tracing::warn!(error = %error, "device type list query failed"); envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") }
    }
}
