use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rust_decimal::Decimal;
use serde_json::{json, Value};
use sqlx::Row;

use super::{envelope_json, envelope_response, AppState, AuthUser};

async fn detail_impl(state: AppState, id: &str, user_id: Option<&str>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let knowledge = match sqlx::query("SELECT id,type,status,cover,title,create_user_id,create_time,credit,difficulty_id,specialty_id FROM t_theory_knowledge WHERE id=? LIMIT 1").bind(id).fetch_optional(pool).await {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 500, "数据异常", ""),
        Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
    };
    let swfs = match sqlx::query("SELECT id,knowledge_id,cover,title,content,create_user_id,create_time,sort FROM t_theory_knowledge_swf WHERE knowledge_id=? ORDER BY sort, create_time").bind(id).fetch_all(pool).await {
        Ok(rows) => rows,
        Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
    };
    let swf_data = swfs.into_iter().map(|row| json!({
        "id": row.get::<String,_>("id"), "knowledgeId": row.get::<Option<String>,_>("knowledge_id"), "cover": row.get::<Option<String>,_>("cover"), "title": row.get::<Option<String>,_>("title"), "content": row.get::<Option<String>,_>("content"), "createUserId": row.get::<Option<String>,_>("create_user_id"), "createTime": row.get::<Option<String>,_>("create_time"), "sort": row.get::<Option<i32>,_>("sort"), "score": if user_id.is_some() { json!(0) } else { Value::Null }, "haveTest": if user_id.is_some() { json!(false) } else { Value::Null }, "record": 0, "test": []
    })).collect::<Vec<_>>();
    let _ = user_id;
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"knowledge": {
        "id": knowledge.get::<String,_>("id"), "type": knowledge.get::<Option<i32>,_>("type"), "status": knowledge.get::<Option<i32>,_>("status"), "cover": knowledge.get::<Option<String>,_>("cover"), "title": knowledge.get::<Option<String>,_>("title"), "createUserId": knowledge.get::<Option<String>,_>("create_user_id"), "createTime": knowledge.get::<Option<String>,_>("create_time"), "credit": knowledge.get::<Option<Decimal>,_>("credit").map(|value| value.to_string()), "difficultyId": knowledge.get::<Option<String>,_>("difficulty_id"), "specialtyId": knowledge.get::<Option<String>,_>("specialty_id")
    }, "knowledgeSwfs": swf_data})),
        "ok",
        "",
    )
}

pub(crate) async fn by_id(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    detail_impl(state, id, None).await
}

pub(crate) async fn by_id_and_token(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    detail_impl(state, id, Some(&user.id)).await
}
