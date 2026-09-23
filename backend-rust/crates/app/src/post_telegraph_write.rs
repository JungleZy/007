use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Path, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::json;
#[derive(serde::Deserialize)]
pub(crate) struct AddRequest {
    title: Option<String>,
    #[serde(rename = "isCable")]
    is_cable: Option<i32>,
    #[serde(rename = "messageType")]
    message_type: Option<i32>,
    content: Option<String>,
}
#[derive(serde::Deserialize)]
pub(crate) struct ActionRequest {
    id: Option<String>,
}
fn err(e: sqlx::Error, op: &'static str) -> Response {
    tracing::warn!(error=%e,operation=op,"post telegraph write failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}
pub(crate) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<AddRequest>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = uuid::Uuid::new_v4().to_string();
    match sqlx::query("INSERT INTO t_post_telegraph_key_pat_train (id,title,is_cable,status,duration,total_number,error_number,accuracy,speed,create_user_id,attempt,protocol_version,content,message_type) VALUES (?,?,?,0,'0',0,0,0,'0',?,0,1,?,?)").bind(&id).bind(body.title).bind(body.is_cable.unwrap_or(0)).bind(&user.id).bind(body.content.unwrap_or_else(||"[]".into())).bind(body.message_type.unwrap_or(0)).execute(pool).await{Ok(_)=>envelope_json(StatusCode::OK,200,Some(json!({"id":id,"status":0,"protocolVersion":1,"attempt":0})),"ok",""),Err(e)=>err(e,"add")}
}
async fn action(state: AppState, user: AuthUser, body: ActionRequest, status: i32) -> Response {
    let Some(id) = body.id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = if status == 1 {
        "UPDATE t_post_telegraph_key_pat_train SET status=1,begin_time=COALESCE(begin_time,NOW(6)) WHERE id=? AND create_user_id=? AND status IN (0,2)"
    } else {
        "UPDATE t_post_telegraph_key_pat_train SET status=3,end_time=NOW(6) WHERE id=? AND create_user_id=? AND status IN (1,2)"
    };
    match sqlx::query(sql).bind(id).bind(&user.id).execute(pool).await {
        Ok(r) if r.rows_affected() == 1 => envelope_response(StatusCode::OK, 200, "ok", ""),
        Ok(_) => envelope_response(StatusCode::OK, 208, "训练状态不可执行", ""),
        Err(e) => err(e, "action"),
    }
}
pub(crate) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    action(state, user, body, 1).await
}
pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    action(state, user, body, 3).await
}
pub(crate) async fn reset(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    let Some(id) = body.id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE t_post_telegraph_key_pat_train SET status=0,attempt=attempt+1,duration='0' WHERE id=? AND create_user_id=? AND status<>3").bind(id).bind(&user.id).execute(pool).await{Ok(_)=>envelope_response(StatusCode::OK,200,"ok",""),Err(e)=>err(e,"reset")}
}
pub(crate) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Path(id): Path<String>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("DELETE FROM t_post_telegraph_key_pat_train WHERE id=? AND create_user_id=?")
        .bind(id)
        .bind(&user.id)
        .execute(pool)
        .await
    {
        Ok(_) => envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", ""),
        Err(e) => err(e, "delete"),
    }
}
