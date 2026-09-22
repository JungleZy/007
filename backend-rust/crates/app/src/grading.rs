use axum::{extract::State, http::StatusCode, response::Response, Json};
use serde_json::{json, Value};
use sqlx::Row;

use super::{envelope_json, envelope_response, AppState};

fn rule_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "type": row.get::<Option<i32>, _>("type"),
        "title": row.get::<Option<String>, _>("title"),
        "score": row.get::<Option<i32>, _>("score"),
        "status": row.get::<Option<i32>, _>("status"),
        "isDefault": row.get::<Option<i32>, _>("is_default"),
        "content": row.get::<Option<String>, _>("content")
    })
}

pub(crate) async fn list_by_type(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(rule_type) = body.get("type").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    match sqlx::query("SELECT id, type, title, score, status, is_default, content FROM t_grading_rule WHERE type = ? ORDER BY id")
        .bind(rule_type).fetch_all(pool).await {
        Ok(rows) => envelope_json(StatusCode::OK, 200, Some(json!(rows.iter().map(rule_json).collect::<Vec<_>>())), "ok", ""),
        Err(error) => { tracing::warn!(error = %error, "grading rule list query failed"); envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") }
    }
}

pub(crate) async fn by_id(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    match sqlx::query("SELECT id, type, title, score, status, is_default, content FROM t_grading_rule WHERE id = ? LIMIT 1").bind(id).fetch_optional(pool).await {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(rule_json(&row)), "ok", ""),
        Ok(None) => envelope_response(StatusCode::OK, 500, "未查询到评分规则", ""),
        Err(error) => { tracing::warn!(error = %error, "grading rule detail query failed"); envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") }
    }
}
