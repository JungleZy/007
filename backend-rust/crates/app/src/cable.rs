use std::collections::HashMap;

use axum::{
    extract::{Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::{MySqlPool, Row};

use crate::platform::{
    envelope::{envelope_json, envelope_response},
    state::AppState,
};

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
        "createTime": row.get::<Option<String>, _>("create_time_text")
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
    let query = "SELECT id, title, type_id, type_title, scope, floor_count, group_count, code_count, code_type, code_sort, remark, DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_time_text FROM t_cable";
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
    match sqlx::query("SELECT id, title, type_id, type_title, scope, floor_count, group_count, code_count, code_type, code_sort, remark, DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_time_text FROM t_cable WHERE id = ? LIMIT 1")
        .bind(id)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(cable_json(&row)), "ok", ""),
        Ok(None) => envelope_response(StatusCode::OK, 202, "未查询到该电缆", "未查询到该电缆"),
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

pub(crate) async fn save(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(type_id) = body
        .get("typeId")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let type_row = match sqlx::query("SELECT title FROM t_cable_type WHERE id = ? LIMIT 1")
        .bind(type_id)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => {
            return envelope_response(
                StatusCode::OK,
                202,
                "未查询到该电缆类型",
                "未查询到该电缆类型",
            )
        }
        Err(error) => {
            tracing::warn!(error = %error, "cable type lookup failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let id = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    let title = body.get("title").and_then(Value::as_str);
    let scope = body.get("scope").and_then(Value::as_i64);
    let code_type = body.get("codeType").and_then(Value::as_i64);
    let code_sort = body.get("codeSort").and_then(Value::as_i64);
    let remark = body.get("remark").and_then(Value::as_str);
    let floors = body
        .get("floors")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default();
    let mut group_count = 0i32;
    let mut code_count = 0i32;
    for page in &floors {
        let Some(groups) = page.as_array() else {
            return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
        };
        for group in groups {
            let Some(codes) = group.as_array() else {
                return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
            };
            group_count += 1;
            code_count += codes.len() as i32;
        }
    }
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => {
            tracing::warn!(error = %error, "cable save transaction could not start");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    if let Err(error) = sqlx::query("INSERT INTO t_cable (id,title,type_id,type_title,scope,floor_count,group_count,code_count,code_type,code_sort,remark,create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,NOW()) ON DUPLICATE KEY UPDATE title=VALUES(title),type_id=VALUES(type_id),type_title=VALUES(type_title),scope=VALUES(scope),floor_count=VALUES(floor_count),group_count=VALUES(group_count),code_count=VALUES(code_count),code_type=VALUES(code_type),code_sort=VALUES(code_sort),remark=VALUES(remark)")
        .bind(&id).bind(title).bind(type_id).bind(type_row.get::<Option<String>, _>("title"))
        .bind(scope).bind(floors.len() as i32).bind(group_count).bind(code_count).bind(code_type).bind(code_sort).bind(remark)
        .execute(&mut *tx).await {
        let _ = tx.rollback().await; tracing::warn!(error = %error, "cable head write failed"); return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if let Err(error) = sqlx::query("DELETE FROM t_cable_floor WHERE cable_id = ?")
        .bind(&id)
        .execute(&mut *tx)
        .await
    {
        let _ = tx.rollback().await;
        tracing::warn!(error = %error, "cable floor replacement delete failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    for (floor_number, page) in floors.iter().enumerate() {
        let Some(groups) = page.as_array() else {
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
        };
        for (sort, group) in groups.iter().enumerate() {
            let Some(codes) = group.as_array() else {
                let _ = tx.rollback().await;
                return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
            };
            let content = codes.iter().filter_map(Value::as_str).collect::<Vec<_>>();
            if let Err(error) = sqlx::query("INSERT INTO t_cable_floor (id,cable_id,type_id,floor_number,sort,mores_key) VALUES (?,?,?,?,?,?)")
                .bind(uuid::Uuid::new_v4().to_string())
                .bind(&id)
                .bind(type_id)
                .bind(floor_number as i32)
                .bind(sort as i32)
                .bind(serde_json::to_string(&content).unwrap_or_else(|_| "[]".into()))
                .execute(&mut *tx)
                .await
            {
                let _ = tx.rollback().await;
                tracing::warn!(error = %error, "cable floor write failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        }
    }
    if let Err(error) = tx.commit().await {
        tracing::warn!(error = %error, "cable save commit failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    find_by_id(State(state), Json(json!({"id": id}))).await
}

pub(crate) async fn delete(
    State(state): State<AppState>,
    Query(query): Query<HashMap<String, String>>,
) -> Response {
    let Some(id) = query.get("id").filter(|value| !value.is_empty()) else {
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
    if sqlx::query("DELETE FROM t_cable_floor WHERE cable_id = ?")
        .bind(id)
        .execute(&mut *tx)
        .await
        .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    let result = match sqlx::query("DELETE FROM t_cable WHERE id = ?")
        .bind(id)
        .execute(&mut *tx)
        .await
    {
        Ok(result) => result.rows_affected() == 1,
        Err(_) => {
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(result)), "ok", "")
}

pub(crate) async fn save_type(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(title) = body
        .get("title")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if sqlx::query("INSERT INTO t_cable_type (id,title) VALUES (?,?) ON DUPLICATE KEY UPDATE title=VALUES(title)").bind(&id).bind(title).execute(&mut *tx).await.is_err() { let _ = tx.rollback().await; return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""); }
    if sqlx::query("UPDATE t_cable SET type_title = ? WHERE type_id = ?")
        .bind(title)
        .bind(&id)
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
    find_types(State(state)).await
}

pub(crate) async fn delete_type(
    State(state): State<AppState>,
    Query(query): Query<HashMap<String, String>>,
) -> Response {
    let Some(id) = query.get("id").filter(|value| !value.is_empty()) else {
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
    if sqlx::query("DELETE FROM t_cable_floor WHERE type_id = ?")
        .bind(id)
        .execute(&mut *tx)
        .await
        .is_err()
        || sqlx::query("DELETE FROM t_cable WHERE type_id = ?")
            .bind(id)
            .execute(&mut *tx)
            .await
            .is_err()
        || sqlx::query("DELETE FROM t_cable_type WHERE id = ?")
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
    envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", "")
}
