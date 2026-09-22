use axum::{extract::State, http::StatusCode, response::Response, Json};
use serde_json::{json, Value};
use sqlx::Row;
use std::collections::HashSet;

use super::{envelope_json, envelope_response, AppState};

pub(crate) async fn list_levels(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,parent_id,name,create_user_id,create_time FROM t_theory_question_level ORDER BY create_time DESC").fetch_all(pool).await {
        Ok(rows)=>envelope_json(StatusCode::OK,200,Some(json!(rows.iter().map(|row| json!({"id":row.get::<String,_>("id"),"parentId":row.get::<Option<String>,_>("parent_id"),"name":row.get::<Option<String>,_>("name"),"createUserId":row.get::<Option<String>,_>("create_user_id"),"createTime":row.get::<Option<String>,_>("create_time")})).collect::<Vec<_>>())),"ok",""),
        Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")
    }
}

pub(crate) async fn list_questions(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(root) = body
        .get("levelId")
        .and_then(Value::as_str)
        .filter(|v| !v.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let levels = match sqlx::query("SELECT id,parent_id FROM t_theory_question_level")
        .fetch_all(pool)
        .await
    {
        Ok(rows) => rows,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let mut ids = HashSet::from([root.to_owned()]);
    let mut changed = true;
    while changed {
        changed = false;
        for row in &levels {
            let id: String = row.get("id");
            let parent = row.get::<Option<String>, _>("parent_id");
            if parent.as_ref().is_some_and(|p| ids.contains(p)) && ids.insert(id) {
                changed = true;
            }
        }
    }
    let rows=match sqlx::query("SELECT id,type,topic,options,answer,analysis,create_user_id,create_time,level_id FROM t_theory_question").fetch_all(pool).await { Ok(rows)=>rows, Err(_)=>return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","") };
    let kind = body
        .get("type")
        .and_then(Value::as_str)
        .and_then(|v| v.parse::<i32>().ok());
    let name = body.get("name").and_then(Value::as_str).unwrap_or("");
    let data=rows.into_iter().filter(|row| ids.contains(&row.get::<String,_>("level_id")) && kind.is_none_or(|v|row.get::<Option<i32>,_>("type")==Some(v)) && row.get::<Option<String>,_>("topic").is_none_or(|v|v.contains(name))).map(|row|json!({"id":row.get::<String,_>("id"),"type":row.get::<Option<i32>,_>("type"),"topic":row.get::<Option<String>,_>("topic"),"options":row.get::<Option<String>,_>("options"),"answer":row.get::<Option<String>,_>("answer"),"analysis":row.get::<Option<String>,_>("analysis"),"createUserId":row.get::<Option<String>,_>("create_user_id"),"createTime":row.get::<Option<String>,_>("create_time"),"levelId":row.get::<Option<String>,_>("level_id"),"createUserName":Value::Null})).collect::<Vec<_>>();
    envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", "")
}
