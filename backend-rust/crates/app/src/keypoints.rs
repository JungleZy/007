use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::json;
use sqlx::Row;
async fn get(state: AppState, body: serde_json::Value, table: &str) -> Response {
    let Some(kind) = body.get("type").and_then(|v| v.as_i64()) else {
        return envelope_response(StatusCode::OK, 202, "要点讲解类型不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = format!("SELECT id,type,content FROM {table} WHERE type=? LIMIT 1");
    match sqlx::query(&sql).bind(kind).fetch_optional(pool).await {
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
pub(crate) async fn entering(
    State(s): State<AppState>,
    Extension(_u): Extension<AuthUser>,
    Json(b): Json<serde_json::Value>,
) -> Response {
    get(s, b, "t_entering_key_points").await
}
pub(crate) async fn key(
    State(s): State<AppState>,
    Extension(_u): Extension<AuthUser>,
    Json(b): Json<serde_json::Value>,
) -> Response {
    get(s, b, "t_key_points").await
}
pub(crate) async fn receive(
    State(s): State<AppState>,
    Extension(_u): Extension<AuthUser>,
    Json(b): Json<serde_json::Value>,
) -> Response {
    get(s, b, "t_receive_key_points").await
}
