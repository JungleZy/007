use axum::{extract::State, http::StatusCode, response::Response, Json};
use serde_json::{json, Value};
use sqlx::{MySqlPool, Row};

use super::{envelope_json, envelope_response, AppState};

fn cable_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "title": row.get::<Option<String>, _>("title"),
        "typeId": row.get::<Option<String>, _>("type_id"),
        "typeTitle": row.get::<Option<String>, _>("type_title"),
        "scope": row.get::<Option<i32>, _>("scope"),
        "floorCount": row.get::<Option<i32>, _>("floor_count"),
        "groupCount": row.get::<Option<i32>, _>("group_count"),
        "codeCount": row.get::<Option<i32>, _>("code_count"),
        "codeType": row.get::<Option<i32>, _>("code_type"),
        "codeSort": row.get::<Option<i32>, _>("code_sort"),
        "remark": row.get::<Option<String>, _>("remark"),
        "createTime": null
    })
}

async fn cable_pool(state: &AppState) -> Result<&MySqlPool, Response> {
    state
        .pool
        .as_ref()
        .ok_or_else(|| envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""))
}

pub(crate) async fn find(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Ok(pool) = cable_pool(&state).await else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let type_id = body.get("typeId").and_then(Value::as_str);
    let scopes: Vec<i32> = body
        .get("scope")
        .and_then(Value::as_array)
        .map(|items| {
            items
                .iter()
                .filter_map(Value::as_i64)
                .filter_map(|value| value.try_into().ok())
                .collect()
        })
        .unwrap_or_default();
    let query = "SELECT id, title, type_id, type_title, scope, floor_count, group_count, code_count, code_type, code_sort, remark FROM t_cable";
    let rows = match type_id {
        Some(type_id) => {
            sqlx::query(&format!("{query} WHERE type_id = ?"))
                .bind(type_id)
                .fetch_all(pool)
                .await
        }
        None => sqlx::query(query).fetch_all(pool).await,
    };
    match rows {
        Ok(rows) => {
            let data = rows
                .iter()
                .filter(|row| {
                    scopes.is_empty()
                        || row
                            .get::<Option<i32>, _>("scope")
                            .is_some_and(|scope| scopes.contains(&scope))
                })
                .map(cable_json)
                .collect::<Vec<_>>();
            envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", "")
        }
        Err(error) => {
            tracing::warn!(error = %error, "cable list query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(crate) async fn find_by_id(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Ok(pool) = cable_pool(&state).await else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|id| !id.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    match sqlx::query("SELECT id, title, type_id, type_title, scope, floor_count, group_count, code_count, code_type, code_sort, remark FROM t_cable WHERE id = ? LIMIT 1")
        .bind(id)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(cable_json(&row)), "ok", ""),
        Ok(None) => envelope_response(StatusCode::OK, 500, "服务器错误", ""),
        Err(error) => {
            tracing::warn!(error = %error, "cable detail query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(crate) async fn find_types(State(state): State<AppState>) -> Response {
    let Ok(pool) = cable_pool(&state).await else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id, title FROM t_cable_type ORDER BY id")
        .fetch_all(pool)
        .await
    {
        Ok(rows) => envelope_json(
            StatusCode::OK,
            200,
            Some(json!(rows
                .iter()
                .map(|row| json!({"id": row.get::<String, _>("id"), "title": row.get::<Option<String>, _>("title")}))
                .collect::<Vec<_>>())),
            "ok",
            "",
        ),
        Err(error) => {
            tracing::warn!(error = %error, "cable type query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(crate) async fn find_floor(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Ok(pool) = cable_pool(&state).await else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|id| !id.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let floor_number = body.get("floorNumber").and_then(Value::as_i64);
    let rows = match floor_number {
        Some(floor_number) => sqlx::query("SELECT mores_key FROM t_cable_floor WHERE cable_id = ? AND floor_number = ? ORDER BY sort")
            .bind(id).bind(floor_number).fetch_all(pool).await,
        None => sqlx::query("SELECT floor_number, mores_key FROM t_cable_floor WHERE cable_id = ? ORDER BY floor_number, sort")
            .bind(id).fetch_all(pool).await,
    };
    match rows {
        Ok(rows) => {
            let mut pages: Vec<Vec<Vec<String>>> = Vec::new();
            let mut current_floor: Option<i32> = None;
            for row in rows {
                if floor_number.is_none() {
                    let floor: Option<i32> = row.get("floor_number");
                    if current_floor != floor {
                        current_floor = floor;
                        pages.push(Vec::new());
                    }
                }
                let groups = serde_json::from_str::<Vec<String>>(
                    &row.get::<Option<String>, _>("mores_key")
                        .unwrap_or_else(|| "[]".into()),
                )
                .unwrap_or_default();
                if floor_number.is_some() {
                    if pages.is_empty() {
                        pages.push(Vec::new());
                    }
                    pages[0].push(groups);
                } else if let Some(page) = pages.last_mut() {
                    page.push(groups);
                }
            }
            envelope_json(StatusCode::OK, 200, Some(json!(pages)), "ok", "")
        }
        Err(error) => {
            tracing::warn!(error = %error, "cable floor query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}
