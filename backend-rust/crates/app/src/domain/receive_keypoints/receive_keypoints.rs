use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::json;
use sqlx::Row;
pub(super) async fn get_by_type(
    State(state): State<AppState>,
    Extension(_user): Extension<AuthUser>,
    Json(body): Json<serde_json::Value>,
) -> Response {
    let Some(kind) = body.get("type").and_then(|v| v.as_i64()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,content FROM t_receive_key_points WHERE type=? LIMIT 1")
        .bind(kind)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(
            StatusCode::OK,
            200,
            Some(
                json!({"id":row.get::<String,_>("id"),"type":row.get::<Option<i32>,_>("type"),"content":row.get::<Option<String>,_>("content")}),
            ),
            "ok",
            "",
        ),
        Ok(None) => envelope_json(StatusCode::OK, 200, None, "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
pub(super) async fn save(
    State(state): State<AppState>,
    Json(body): Json<serde_json::Value>,
) -> Response {
    let Some(kind) = body.get("type").and_then(|value| value.as_i64()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(content) = body.get("content").and_then(|value| value.as_str()) else {
        return envelope_response(StatusCode::OK, 202, "内容不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = body
        .get("id")
        .and_then(|value| value.as_str())
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    match sqlx::query("INSERT INTO t_receive_key_points (id,type,content) VALUES (?,?,?) ON DUPLICATE KEY UPDATE type=VALUES(type),content=VALUES(content)")
        .bind(&id)
        .bind(kind)
        .bind(content)
        .execute(pool)
        .await
    {
        Ok(_) => envelope_json(StatusCode::OK, 200, Some(json!({"id":id,"type":kind,"content":content})), "ok", ""),
        Err(error) => {
            tracing::warn!(%error, "save receive key points failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}
