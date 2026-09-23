use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde::Deserialize;
use serde_json::json;
use sqlx::Row;

#[derive(Debug, Deserialize)]
pub(crate) struct TypeRequest {
    #[serde(rename = "type")]
    pub train_type: Option<i32>,
}

pub(crate) async fn find_by_type(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TypeRequest>,
) -> Response {
    let Some(train_type) = body.train_type else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,create_user_id,type,total_time,total_num,total_error,create_time FROM t_telegraph_key_pat_train WHERE create_user_id=? AND type=? LIMIT 1").bind(&user.id).bind(train_type).fetch_optional(pool).await{
  Ok(Some(row))=>envelope_json(StatusCode::OK,200,Some(json!({"id":row.get::<String,_>("id"),"type":row.get::<Option<i32>,_>("type"),"totalTime":row.get::<Option<i32>,_>("total_time"),"totalNum":row.get::<Option<i32>,_>("total_num"),"totalError":row.get::<Option<i32>,_>("total_error")})),"ok",""),
  Ok(None)=>envelope_json(StatusCode::OK,200,Some(json!({"id":null,"type":train_type,"totalTime":0,"totalNum":0,"totalError":0})),"ok",""),
  Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")
 }
}
