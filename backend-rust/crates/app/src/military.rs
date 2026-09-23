use super::{envelope_json, envelope_response, AppState};
use axum::{extract::State, http::StatusCode, response::Response};
use serde_json::json;
use sqlx::Row;

pub(crate) async fn list(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,parent_id,`key`,value,sort FROM t_military_term_data ORDER BY sort").fetch_all(pool).await{Ok(rows)=>envelope_json(StatusCode::OK,200,Some(json!(rows.iter().map(|row|json!({"id":row.get::<String,_>("id"),"parentId":row.get::<Option<String>,_>("parent_id"),"key":row.get::<Option<String>,_>("key"),"value":row.get::<Option<String>,_>("value"),"sort":row.get::<Option<i32>,_>("sort"),"child":[]})).collect::<Vec<_>>())),"ok",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
