use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
};
use serde_json::json;
use sqlx::Row;
pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT user_id,type,total_time,total_count,active_session_id FROM t_radiotelephone_train WHERE user_id=? ORDER BY type").bind(&user.id).fetch_all(pool).await{Ok(rows)=>envelope_json(StatusCode::OK,200,Some(json!(rows.iter().map(|row|json!({"userId":row.get::<Option<String>,_>("user_id"),"type":row.get::<Option<i32>,_>("type"),"totalTime":row.get::<Option<String>,_>("total_time"),"totalCount":row.get::<Option<i32>,_>("total_count"),"sessionId":row.get::<Option<String>,_>("active_session_id")})).collect::<Vec<_>>())),"ok",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
