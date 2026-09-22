use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;

use super::{envelope_json, envelope_response, AppState, AuthUser};

fn content_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id": row.get::<String,_>("id"), "knowledgeId": row.get::<Option<String>,_>("knowledge_id"), "knowledgeSwfId": row.get::<Option<String>,_>("knowledge_swf_id"), "knowledgeTestId": row.get::<Option<String>,_>("knowledge_test_id"), "type": row.get::<Option<i32>,_>("type"), "topic": row.get::<Option<String>,_>("topic"), "options": row.get::<Option<String>,_>("options"), "answer": row.get::<Option<String>,_>("answer"), "analysis": row.get::<Option<String>,_>("analysis"), "sort": row.get::<Option<i32>,_>("sort"), "createUserId": row.get::<Option<String>,_>("create_user_id"), "createTime": row.get::<Option<String>,_>("create_time")})
}
fn test_json(row: &sqlx::mysql::MySqlRow, contents: Vec<Value>) -> Value {
    json!({"knowledgeTest": {"id": row.get::<String,_>("id"), "title": row.get::<Option<String>,_>("title"), "knowledgeId": row.get::<Option<String>,_>("knowledge_id"), "knowledgeSwfId": row.get::<Option<String>,_>("knowledge_swf_id"), "versions": row.get::<Option<i32>,_>("versions"), "createTime": row.get::<Option<String>,_>("create_time"), "createUserId": row.get::<Option<String>,_>("create_user_id")}, "knowledgeTestContents": contents})
}

pub(crate) async fn by_swf(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let tests = match sqlx::query("SELECT id,title,knowledge_id,knowledge_swf_id,versions,create_time,create_user_id FROM t_theory_knowledge_test WHERE knowledge_swf_id=? ORDER BY create_time").bind(id).fetch_all(pool).await { Ok(rows) => rows, Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") };
    let mut result = Vec::new();
    for test in tests {
        let contents = sqlx::query("SELECT id,knowledge_id,knowledge_swf_id,knowledge_test_id,type,topic,options,answer,analysis,sort,create_user_id,create_time FROM t_theory_knowledge_test_content WHERE knowledge_test_id=? ORDER BY sort").bind(test.get::<String,_>("id")).fetch_all(pool).await.unwrap_or_default().iter().map(content_json).collect();
        result.push(test_json(&test, contents));
    }
    envelope_json(StatusCode::OK, 200, Some(json!(result)), "ok", "")
}

pub(crate) async fn enabled(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(test) = sqlx::query("SELECT id FROM t_theory_knowledge_test WHERE knowledge_swf_id=? AND versions=1 ORDER BY create_time DESC LIMIT 1").bind(id).fetch_optional(pool).await.ok().flatten() else { return envelope_response(StatusCode::OK, 500, "数据异常", ""); };
    match sqlx::query("SELECT id,knowledge_id,knowledge_swf_id,knowledge_test_id,type,topic,options,answer,analysis,sort,create_user_id,create_time FROM t_theory_knowledge_test_content WHERE knowledge_test_id=? ORDER BY sort").bind(test.get::<String,_>("id")).fetch_all(pool).await { Ok(rows) => envelope_json(StatusCode::OK, 200, Some(json!(rows.iter().map(content_json).collect::<Vec<_>>())), "ok", ""), Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") }
}

async fn user_results(pool: &sqlx::MySqlPool, user: &AuthUser, clause: &str, id: &str) -> Response {
    let query = format!("SELECT id,knowledge_id,knowledge_swf_id,user_id,content,score,create_time FROM t_theory_knowledge_test_user WHERE {clause}=? AND user_id=? ORDER BY create_time");
    match sqlx::query(&query).bind(id).bind(&user.id).fetch_all(pool).await { Ok(rows) => envelope_json(StatusCode::OK, 200, Some(json!(rows.iter().map(|row| json!({"id": row.get::<String,_>("id"), "knowledgeId": row.get::<Option<String>,_>("knowledge_id"), "knowledgeSwfId": row.get::<Option<String>,_>("knowledge_swf_id"), "userId": row.get::<Option<String>,_>("user_id"), "content": row.get::<Option<String>,_>("content"), "score": row.get::<Option<i32>,_>("score"), "createTime": row.get::<Option<String>,_>("create_time")})).collect::<Vec<_>>())), "ok", ""), Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") }
}

pub(crate) async fn by_knowledge(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body.get("id").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    user_results(pool, &user, "knowledge_id", id).await
}
pub(crate) async fn by_swf_user(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body.get("id").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,knowledge_id,knowledge_swf_id,user_id,content,score,create_time FROM t_theory_knowledge_test_user WHERE knowledge_swf_id=? AND user_id=? LIMIT 1").bind(id).bind(&user.id).fetch_optional(pool).await { Ok(Some(row)) => envelope_json(StatusCode::OK,200,Some(json!({"id":row.get::<String,_>("id"),"knowledgeId":row.get::<Option<String>,_>("knowledge_id"),"knowledgeSwfId":row.get::<Option<String>,_>("knowledge_swf_id"),"userId":row.get::<Option<String>,_>("user_id"),"content":row.get::<Option<String>,_>("content"),"score":row.get::<Option<i32>,_>("score"),"createTime":row.get::<Option<String>,_>("create_time")})),"ok","") , Ok(None)=>envelope_json(StatusCode::OK,200,None,"ok",""), Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","") }
}
pub(crate) async fn by_id(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body.get("id").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,knowledge_id,knowledge_swf_id,user_id,content,score,create_time FROM t_theory_knowledge_test_user WHERE id=? LIMIT 1").bind(id).fetch_optional(pool).await { Ok(Some(row)) if row.get::<Option<String>,_>("user_id").as_deref()==Some(user.id.as_str()) => envelope_json(StatusCode::OK,200,Some(json!({"id":row.get::<String,_>("id"),"knowledgeId":row.get::<Option<String>,_>("knowledge_id"),"knowledgeSwfId":row.get::<Option<String>,_>("knowledge_swf_id"),"userId":row.get::<Option<String>,_>("user_id"),"content":row.get::<Option<String>,_>("content"),"score":row.get::<Option<i32>,_>("score"),"createTime":row.get::<Option<String>,_>("create_time")})),"ok","") , Ok(Some(_))=>envelope_response(StatusCode::OK,207,"无权限",""), Ok(None)=>envelope_response(StatusCode::OK,500,"数据异常",""), Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","") }
}
