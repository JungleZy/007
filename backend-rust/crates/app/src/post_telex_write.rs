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
    name: Option<String>,
    #[serde(rename = "isCable")]
    is_cable: Option<i32>,
    #[serde(rename = "type")]
    train_type: Option<i32>,
    #[serde(rename = "trainType")]
    train_kind: Option<i32>,
}
#[derive(serde::Deserialize)]
pub(crate) struct ActionRequest {
    id: Option<String>,
}
fn err(e: sqlx::Error, op: &'static str) -> Response {
    tracing::warn!(error=%e,operation=op,"post telex write failed");
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
    match sqlx::query("INSERT INTO t_post_telex_pat_train (id,name,is_cable,type,train_type,create_user,status,protocol_version,attempt) VALUES (?,?,?,?,?,?,0,1,0)").bind(&id).bind(body.name).bind(body.is_cable.unwrap_or(0)).bind(body.train_type.unwrap_or(0)).bind(body.train_kind.unwrap_or(0)).bind(&user.id).execute(pool).await{Ok(_)=>envelope_json(StatusCode::OK,200,Some(json!({"id":id,"status":0,"protocolVersion":1,"attempt":0})),"ok",""),Err(e)=>err(e,"add")}
}
async fn action(state: AppState, user: AuthUser, body: ActionRequest, status: i32) -> Response {
    let Some(id) = body.id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = if status == 1 {
        "UPDATE t_post_telex_pat_train SET status=1,start_time=COALESCE(start_time,NOW(6)) WHERE id=? AND create_user=? AND status IN (0,2)"
    } else if status == 2 {
        "UPDATE t_post_telex_pat_train SET status=2,paused_at=NOW(6) WHERE id=? AND create_user=? AND status=1"
    } else {
        "UPDATE t_post_telex_pat_train SET status=3,end_time=NOW(6) WHERE id=? AND create_user=? AND status IN (1,2)"
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
pub(crate) async fn pause(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    action(state, user, body, 2).await
}
pub(crate) async fn resume(
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
    match sqlx::query("UPDATE t_post_telex_pat_train SET status=0,attempt=attempt+1,valid_time=0 WHERE id=? AND create_user=? AND status<>3").bind(id).bind(&user.id).execute(pool).await{Ok(_)=>envelope_response(StatusCode::OK,200,"ok",""),Err(e)=>err(e,"reset")}
}
pub(crate) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Path(id): Path<String>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("DELETE FROM t_post_telex_pat_train WHERE id=? AND create_user=?")
        .bind(id)
        .bind(&user.id)
        .execute(pool)
        .await
    {
        Ok(_) => envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", ""),
        Err(e) => err(e, "delete"),
    }
}
