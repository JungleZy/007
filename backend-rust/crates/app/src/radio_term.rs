use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;
pub(crate) async fn find_by_type(
    State(state): State<AppState>,
    Extension(_user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(kind) = body.get("type").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,`key`,value,sort FROM t_post_radiotelephone_term_data WHERE type=? ORDER BY sort,id").bind(kind).fetch_all(pool).await{Ok(rows)=>envelope_json(StatusCode::OK,200,Some(json!(rows.iter().map(|r|json!({"id":r.get::<i32,_>("id"),"type":r.get::<Option<i32>,_>("type"),"key":r.get::<Option<String>,_>("key"),"value":r.get::<Option<String>,_>("value"),"sort":r.get::<Option<i32>,_>("sort")})).collect::<Vec<_>>())),"ok",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
