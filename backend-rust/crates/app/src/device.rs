use std::collections::HashMap;

use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;

use super::{envelope_json, envelope_response, AppState, AuthUser};

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

pub(crate) async fn save_device(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let type_id = body.get("deviceTypeId").and_then(Value::as_i64);
    let name = body.get("deviceName").and_then(Value::as_str);
    let number = body.get("deviceNumber").and_then(Value::as_str);
    let image = body.get("deviceImg").and_then(Value::as_str);
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let device_id = if let Some(id) = body.get("id").and_then(Value::as_i64) {
        if sqlx::query("UPDATE t_device SET device_name=?, device_number=?, device_img=?, device_type_id=? WHERE id=?").bind(name).bind(number).bind(image).bind(type_id).bind(id).execute(&mut *tx).await.is_err() { let _ = tx.rollback().await; return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""); }
        id
    } else {
        match sqlx::query("INSERT INTO t_device (device_name,device_number,device_img,device_type_id,user_id) VALUES (?,?,?,?,?)").bind(name).bind(number).bind(image).bind(type_id).bind(&user.id).execute(&mut *tx).await { Ok(result) => result.last_insert_id() as i64, Err(_) => { let _ = tx.rollback().await; return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""); } }
    };
    if sqlx::query("DELETE FROM t_device_description WHERE device_id=?")
        .bind(device_id)
        .execute(&mut *tx)
        .await
        .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if let Some(descriptions) = body.get("descriptions").and_then(Value::as_array) {
        for description in descriptions {
            if sqlx::query("INSERT INTO t_device_description (title,content,user_id,device_id) VALUES (?,?,?,?)").bind(description.get("title").and_then(Value::as_str)).bind(description.get("content").and_then(Value::as_str)).bind(&user.id).bind(device_id).execute(&mut *tx).await.is_err() { let _ = tx.rollback().await; return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""); }
        }
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"id": device_id, "deviceName": name, "deviceNumber": number, "deviceImg": image, "deviceTypeId": type_id}),
        ),
        "ok",
        "",
    )
}

pub(crate) async fn delete_device(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body.get("id").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if sqlx::query("DELETE FROM t_device_description WHERE device_id=?")
        .bind(id)
        .execute(&mut *tx)
        .await
        .is_err()
        || sqlx::query("DELETE FROM t_device WHERE id=?")
            .bind(id)
            .execute(&mut *tx)
            .await
            .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, None, "ok", "")
}

pub(crate) async fn save_type(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(name) = body
        .get("typeName")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result = if let Some(id) = body.get("id").and_then(Value::as_i64) {
        sqlx::query("UPDATE t_device_type SET type_name=? WHERE id=?")
            .bind(name)
            .bind(id)
            .execute(pool)
            .await
    } else {
        sqlx::query("INSERT INTO t_device_type (type_name) VALUES (?)")
            .bind(name)
            .execute(pool)
            .await
    };
    match result {
        Ok(_) => list_types(State(state)).await,
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn delete_type(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body.get("id").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if sqlx::query("DELETE d FROM t_device_description d JOIN t_device e ON e.id=d.device_id WHERE e.device_type_id=?").bind(id).execute(&mut *tx).await.is_err() || sqlx::query("DELETE FROM t_device WHERE device_type_id=?").bind(id).execute(&mut *tx).await.is_err() || sqlx::query("DELETE FROM t_device_type WHERE id=?").bind(id).execute(&mut *tx).await.is_err() { let _ = tx.rollback().await; return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""); }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, None, "ok", "")
}

pub(crate) async fn add_description(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(device_id) = body.get("deviceId").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query(
        "INSERT INTO t_device_description (title,content,user_id,device_id) VALUES (?,?,?,?)",
    )
    .bind(body.get("title").and_then(Value::as_str))
    .bind(body.get("content").and_then(Value::as_str))
    .bind(&user.id)
    .bind(device_id)
    .execute(pool)
    .await
    {
        Ok(result) => envelope_json(
            StatusCode::OK,
            200,
            Some(
                json!({"id": result.last_insert_id(), "title": body.get("title"), "content": body.get("content")}),
            ),
            "ok",
            "",
        ),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
