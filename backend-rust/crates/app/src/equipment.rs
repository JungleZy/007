use axum::{extract::State, http::StatusCode, response::Response, Json};
use serde_json::{json, Value};
use sqlx::Row;
use uuid::Uuid;

use super::{envelope_json, envelope_response, AppState};

pub(crate) async fn list_devices(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id, device_id, name, image, `option`, is_enable, DATE_FORMAT(create_time,'%Y-%m-%dT%H:%i:%s') AS create_time_text FROM t_equipment_device ORDER BY create_time")
        .fetch_all(pool).await {
        Ok(rows) => envelope_json(StatusCode::OK, 200, Some(json!(rows.iter().map(|row| json!({
            "id": row.get::<String, _>("id"),
            "deviceId": row.get::<Option<String>, _>("device_id"),
            "name": row.get::<Option<String>, _>("name"),
            "image": row.get::<Option<String>, _>("image"),
            "option": row.get::<Option<String>, _>("option"),
            "isEnable": row.get::<Option<i32>, _>("is_enable"),
            "createTime": row.get::<Option<String>, _>("create_time_text")
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

pub(crate) async fn add(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = Uuid::new_v4().to_string();
    match sqlx::query("INSERT INTO t_equipment_device (id,name,image,`option`,is_enable,key_points,create_time) VALUES (?,?,?,?,?,?,NOW())")
        .bind(&id)
        .bind(body.get("name").and_then(Value::as_str))
        .bind(body.get("image").and_then(Value::as_str))
        .bind(body.get("option").and_then(Value::as_str))
        // The Java entity defaults isEnable to 0.
        .bind(body.get("isEnable").and_then(Value::as_i64).unwrap_or(0))
        .bind(body.get("keyPoints").and_then(Value::as_str))
        .execute(pool)
        .await
    {
        Ok(_) => envelope_json(StatusCode::OK, 200, None, "ok", ""),
        Err(error) => { tracing::warn!(error = %error, "equipment device insert failed"); envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") }
    }
}

pub(crate) async fn update(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
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
    match sqlx::query(
        "UPDATE t_equipment_device SET image=?, is_enable=?, name=?, `option`=? WHERE id=?",
    )
    .bind(body.get("image").and_then(Value::as_str))
    .bind(body.get("isEnable").and_then(Value::as_i64))
    .bind(body.get("name").and_then(Value::as_str))
    .bind(body.get("option").and_then(Value::as_str))
    .bind(id)
    .execute(pool)
    .await
    {
        Ok(result) if result.rows_affected() == 1 => {
            envelope_json(StatusCode::OK, 200, None, "ok", "")
        }
        Ok(_) => envelope_response(StatusCode::OK, 202, "未查询到该设备", "未查询到该设备"),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn delete(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
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
    match sqlx::query("DELETE FROM t_equipment_device WHERE id=?")
        .bind(id)
        .execute(pool)
        .await
    {
        Ok(result) if result.rows_affected() == 1 => {
            envelope_json(StatusCode::OK, 200, None, "ok", "")
        }
        Ok(_) => envelope_response(StatusCode::OK, 200, "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn add_key_points(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("deviceId")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE t_equipment_device SET key_points=? WHERE id=?")
        .bind(body.get("content").and_then(Value::as_str))
        .bind(id)
        .execute(pool)
        .await
    {
        Ok(result) if result.rows_affected() == 1 => {
            envelope_json(StatusCode::OK, 200, None, "ok", "")
        }
        Ok(_) => envelope_response(StatusCode::OK, 202, "未查询到该设备", "未查询到该设备"),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
