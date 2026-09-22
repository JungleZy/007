use axum::{extract::State, http::StatusCode, response::Response, Json};
use serde_json::{json, Value};
use sqlx::Row;
use std::collections::HashMap;

use super::{envelope_json, envelope_response, AppState};

fn question_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id":row.get::<String,_>("id"),"testpaperId":row.get::<Option<String>,_>("test_paper_id"),"parentId":row.get::<Option<String>,_>("parent_id"),"score":row.get::<Option<i32>,_>("score"),"sort":row.get::<Option<i32>,_>("sort"),"type":row.get::<Option<i32>,_>("type"),"topic":row.get::<Option<String>,_>("topic"),"options":row.get::<Option<String>,_>("options"),"answer":row.get::<Option<String>,_>("answer"),"analysis":row.get::<Option<String>,_>("analysis")})
}
async fn paper(pool: &sqlx::MySqlPool, row: &sqlx::mysql::MySqlRow) -> Result<Value, sqlx::Error> {
    let id: String = row.get("id");
    let qs=sqlx::query("SELECT id,test_paper_id,parent_id,score,sort,type,topic,options,answer,analysis FROM t_theory_knowledge_test_paper_question WHERE test_paper_id=? ORDER BY sort").bind(&id).fetch_all(pool).await?;
    let mut grouped: HashMap<i32, Vec<Value>> = HashMap::new();
    for q in qs {
        grouped
            .entry(q.get::<Option<i32>, _>("type").unwrap_or_default())
            .or_default()
            .push(question_json(&q));
    }
    Ok(
        json!({"id":id,"name":row.get::<Option<String>,_>("name"),"levelId":row.get::<Option<String>,_>("level_id"),"total":row.get::<Option<i32>,_>("total"),"passTheExamThan":row.get::<Option<String>,_>("pass_the_exam_than"),"passMark":row.get::<Option<i32>,_>("pass_mark"),"createTime":row.get::<Option<String>,_>("create_time"),"singleChoice":grouped.remove(&1).unwrap_or_default(),"multipleChoice":grouped.remove(&2).unwrap_or_default(),"judge":grouped.remove(&3).unwrap_or_default(),"completion":grouped.remove(&4).unwrap_or_default(),"shortAnswer":grouped.remove(&5).unwrap_or_default()}),
    )
}
pub(crate) async fn list(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let rows=match sqlx::query("SELECT id,name,level_id,total,pass_the_exam_than,pass_mark,create_time FROM t_theory_knowledge_test_paper ORDER BY create_time").fetch_all(pool).await{Ok(r)=>r,Err(_)=>return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")};
    let mut out = Vec::new();
    for row in rows {
        match paper(pool, &row).await {
            Ok(v) => out.push(v),
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        }
    }
    envelope_json(StatusCode::OK, 200, Some(json!(out)), "ok", "")
}
pub(crate) async fn by_id(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|v| !v.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,name,level_id,total,pass_the_exam_than,pass_mark,create_time FROM t_theory_knowledge_test_paper WHERE id=? LIMIT 1").bind(id).fetch_optional(pool).await{Ok(Some(row))=>match paper(pool,&row).await{Ok(v)=>envelope_json(StatusCode::OK,200,Some(v),"ok",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")},Ok(None)=>envelope_response(StatusCode::OK,500,"未查询到该试卷",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
pub(crate) async fn by_level_name(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let level = body.get("levelId").and_then(Value::as_str);
    let name = body.get("name").and_then(Value::as_str).unwrap_or("");
    let rows=match (level,name.is_empty()){(Some(l),false)=>sqlx::query("SELECT id,name,level_id,total,pass_the_exam_than,pass_mark,create_time FROM t_theory_knowledge_test_paper WHERE level_id=? AND name LIKE ?").bind(l).bind(format!("%{name}%")).fetch_all(pool).await,(Some(l),true)=>sqlx::query("SELECT id,name,level_id,total,pass_the_exam_than,pass_mark,create_time FROM t_theory_knowledge_test_paper WHERE level_id=?").bind(l).fetch_all(pool).await,(None,false)=>sqlx::query("SELECT id,name,level_id,total,pass_the_exam_than,pass_mark,create_time FROM t_theory_knowledge_test_paper WHERE name LIKE ?").bind(format!("%{name}%")).fetch_all(pool).await,(None,true)=>sqlx::query("SELECT id,name,level_id,total,pass_the_exam_than,pass_mark,create_time FROM t_theory_knowledge_test_paper").fetch_all(pool).await};
    match rows {
        Ok(rows) => {
            let mut out = Vec::new();
            for row in rows {
                if let Ok(v) = paper(pool, &row).await {
                    out.push(v)
                }
            }
            envelope_json(StatusCode::OK, 200, Some(json!(out)), "ok", "")
        }
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
