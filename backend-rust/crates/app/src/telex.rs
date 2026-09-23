use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rust_decimal::Decimal;
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::Row;

#[derive(Debug, Deserialize)]
pub(crate) struct TypeRequest {
    #[serde(rename = "type")]
    pub train_type: Option<i32>,
}

pub(crate) async fn statistical_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let rows=match sqlx::query("SELECT id,type,total_count,total_time,avg_speed,user_id FROM t_telex_pat_train_statistical WHERE user_id=? ORDER BY type").bind(&user.id).fetch_all(pool).await{Ok(rows)=>rows,Err(_)=>return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")};
    let mut by_type = std::collections::HashMap::new();
    for row in rows {
        let kind = row.get::<Option<i32>, _>("type").unwrap_or(0);
        by_type.insert(kind,json!({"id":row.get::<String,_>("id"),"type":kind,"totalCount":row.get::<Option<i32>,_>("total_count").unwrap_or(0),"totalTime":row.get::<Option<String>,_>("total_time").unwrap_or_else(||"0".to_string()),"avgSpeed":row.get::<Option<Decimal>,_>("avg_speed").map(|value|value.to_string()).unwrap_or_else(||"0".to_string()),"userId":row.get::<Option<String>,_>("user_id")}));
    }
    let result=(0..4).map(|kind|by_type.remove(&kind).unwrap_or_else(||json!({"id":null,"type":kind,"totalCount":0,"totalTime":"0","avgSpeed":"0","userId":user.id}))).collect::<Vec<_>>();
    envelope_json(StatusCode::OK, 200, Some(Value::Array(result)), "ok", "")
}

pub(crate) async fn last_train(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TypeRequest>,
) -> Response {
    let Some(kind) = body.train_type else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,numbs,title,status,duration,total_number,error_number,accuracy,speed,content FROM t_telex_pat_train WHERE create_user_id=? AND type=? ORDER BY create_time DESC LIMIT 1").bind(&user.id).bind(kind).fetch_optional(pool).await{
  Ok(Some(row))=>envelope_json(StatusCode::OK,200,Some(json!({"id":row.get::<String,_>("id"),"type":row.get::<Option<i32>,_>("type"),"numbs":row.get::<Option<i32>,_>("numbs"),"title":row.get::<Option<String>,_>("title"),"status":row.get::<Option<i32>,_>("status"),"duration":row.get::<Option<String>,_>("duration"),"totalNumber":row.get::<Option<i32>,_>("total_number"),"errorNumber":row.get::<Option<i32>,_>("error_number"),"accuracy":row.get::<Option<i32>,_>("accuracy"),"speed":row.get::<Option<String>,_>("speed"),"content":row.get::<Option<String>,_>("content")})),"ok",""),
  Ok(None)=>envelope_json(StatusCode::OK,200,None,"ok",""),
  Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")
 }
}
