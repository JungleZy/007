use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::json;
#[derive(serde::Deserialize)]
pub(crate) struct AddRequest {
    #[serde(rename = "deviceType")]
    device_type: Option<i32>,
    #[serde(rename = "deviceId")]
    device_id: Option<i32>,
    topic: Option<String>,
}
#[derive(serde::Deserialize)]
pub(crate) struct AnswerRequest {
    id: Option<i32>,
    answer: Option<String>,
}
fn err(e: sqlx::Error, op: &'static str) -> Response {
    tracing::warn!(error=%e,operation=op,"group net write failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}
pub(crate) async fn save(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<AddRequest>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("INSERT INTO group_net_train (device_type,device_id,topic,create_user,create_time) VALUES (?,?,?,?,NOW())").bind(body.device_type).bind(body.device_id).bind(body.topic).bind(&user.id).execute(pool).await{Ok(r)=>envelope_json(StatusCode::OK,200,Some(json!({"id":r.last_insert_id()})),"ok",""),Err(e)=>err(e,"save")}
}
pub(crate) async fn submit(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<AnswerRequest>,
) -> Response {
    let Some(id) = body.id else {
        return envelope_response(StatusCode::OK, 202, "训练id不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE group_net_train SET answer=? WHERE id=? AND create_user=?")
        .bind(body.answer)
        .bind(id)
        .bind(&user.id)
        .execute(pool)
        .await
    {
        Ok(r) if r.rows_affected() == 1 => envelope_response(StatusCode::OK, 200, "ok", ""),
        Ok(_) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Err(e) => err(e, "submit"),
    }
}
