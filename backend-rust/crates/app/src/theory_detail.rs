use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;

use crate::platform::{
    auth::AuthUser,
    envelope::{envelope_json, envelope_response},
    state::AppState,
};

async fn detail_impl(state: AppState, id: &str, user_id: Option<&str>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let knowledge = match sqlx::query("SELECT id,type,status,cover,title,create_user_id,create_time,CAST(credit AS DOUBLE) AS credit,difficulty_id,specialty_id FROM t_theory_knowledge WHERE id=? LIMIT 1").bind(id).fetch_optional(pool).await {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 500, "数据异常", ""),
        Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
    };
    let swfs = match sqlx::query("SELECT id,knowledge_id,cover,title,content,create_user_id,create_time,sort FROM t_theory_knowledge_swf WHERE knowledge_id=? ORDER BY sort, create_time").bind(id).fetch_all(pool).await { Ok(rows) => rows, Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") };
    let mut swf_data = Vec::with_capacity(swfs.len());
    for row in swfs {
        let swf_id: String = row.get("id");
        let tests = match sqlx::query("SELECT id,title,knowledge_id,knowledge_swf_id,versions,create_time,create_user_id FROM t_theory_knowledge_test WHERE knowledge_swf_id=? ORDER BY create_time").bind(&swf_id).fetch_all(pool).await { Ok(rows) => rows, Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","") };
        let mut test_data = Vec::with_capacity(tests.len());
        for test in tests {
            let test_id: String = test.get("id");
            let contents = match sqlx::query("SELECT id,knowledge_id,knowledge_swf_id,knowledge_test_id,type,topic,options,answer,analysis,sort,create_user_id,create_time FROM t_theory_knowledge_test_content WHERE knowledge_test_id=? ORDER BY create_time").bind(&test_id).fetch_all(pool).await { Ok(rows) => rows, Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","") };
            test_data.push(json!({"id":test_id,"title":test.get::<Option<String>,_>("title"),"knowledgeId":test.get::<Option<String>,_>("knowledge_id"),"knowledgeSwfId":test.get::<Option<String>,_>("knowledge_swf_id"),"versions":test.get::<Option<i32>,_>("versions"),"createTime":test.get::<Option<String>,_>("create_time"),"createUserId":test.get::<Option<String>,_>("create_user_id"),"knowledgeTestContents":contents.iter().map(|content| json!({"id":content.get::<String,_>("id"),"knowledgeId":content.get::<Option<String>,_>("knowledge_id"),"knowledgeSwfId":content.get::<Option<String>,_>("knowledge_swf_id"),"knowledgeTestId":content.get::<Option<String>,_>("knowledge_test_id"),"type":content.get::<Option<i32>,_>("type"),"topic":content.get::<Option<String>,_>("topic"),"options":content.get::<Option<String>,_>("options"),"answer":content.get::<Option<String>,_>("answer"),"analysis":content.get::<Option<String>,_>("analysis"),"sort":content.get::<Option<i32>,_>("sort"),"createUserId":content.get::<Option<String>,_>("create_user_id"),"createTime":content.get::<Option<String>,_>("create_time")})).collect::<Vec<_>>() }));
        }
        let (score, have_test, record) = if let Some(uid) = user_id {
            let score = sqlx::query_scalar::<_, Option<i32>>("SELECT score FROM t_theory_knowledge_test_user WHERE user_id=? AND knowledge_swf_id=? ORDER BY create_time DESC LIMIT 1").bind(uid).bind(&swf_id).fetch_optional(pool).await.ok().flatten().flatten().unwrap_or(0);
            let seconds = sqlx::query_scalar::<_, Option<i64>>("SELECT SUM(TIMESTAMPDIFF(SECOND,join_time,exit_time)) FROM t_theory_knowledge_swf_record WHERE user_id=? AND knowledge_swf_id=?").bind(uid).bind(&swf_id).fetch_optional(pool).await.ok().flatten().flatten().unwrap_or(0);
            (
                json!(score),
                json!(test_data.iter().any(|test| test["versions"] == 1)),
                json!(seconds as f64 / 60.0),
            )
        } else {
            (Value::Null, Value::Null, json!(0))
        };
        swf_data.push(json!({"id":swf_id,"knowledgeId":row.get::<Option<String>,_>("knowledge_id"),"cover":row.get::<Option<String>,_>("cover"),"title":row.get::<Option<String>,_>("title"),"content":row.get::<Option<String>,_>("content"),"createUserId":row.get::<Option<String>,_>("create_user_id"),"createTime":row.get::<Option<String>,_>("create_time"),"sort":row.get::<Option<i32>,_>("sort"),"score":score,"haveTest":have_test,"record":record,"test":test_data}));
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"knowledge": {
        "id": knowledge.get::<String,_>("id"), "type": knowledge.get::<Option<i32>,_>("type"), "status": knowledge.get::<Option<i32>,_>("status"), "cover": knowledge.get::<Option<String>,_>("cover"), "title": knowledge.get::<Option<String>,_>("title"), "createUserId": knowledge.get::<Option<String>,_>("create_user_id"), "createTime": knowledge.get::<Option<String>,_>("create_time"), "credit": knowledge.get::<Option<f64>,_>("credit"), "difficultyId": knowledge.get::<Option<String>,_>("difficulty_id"), "specialtyId": knowledge.get::<Option<String>,_>("specialty_id")
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
