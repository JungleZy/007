use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;
use std::collections::HashMap;
use uuid::Uuid;

use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;

fn question_json(row: &sqlx::mysql::MySqlRow) -> Value {
    // Java's entity testPaperId does not populate DTO testpaperId (lowercase p).
    json!({"id":row.get::<String,_>("id"),"testpaperId":Value::Null,"parentId":row.get::<Option<String>,_>("parent_id"),"score":row.get::<Option<i32>,_>("score"),"sort":row.get::<Option<i32>,_>("sort"),"type":row.get::<Option<i32>,_>("type"),"topic":row.get::<Option<String>,_>("topic"),"options":row.get::<Option<String>,_>("options"),"answer":row.get::<Option<String>,_>("answer"),"analysis":row.get::<Option<String>,_>("analysis")})
}
fn value_text(value: Option<&Value>) -> Option<String> {
    value.map(|value| {
        value
            .as_str()
            .map(str::to_owned)
            .unwrap_or_else(|| value.to_string())
    })
}
async fn paper(pool: &sqlx::MySqlPool, row: &sqlx::mysql::MySqlRow) -> Result<Value, sqlx::Error> {
    let id: String = row.get("id");
    let qs=sqlx::query("SELECT id,parent_id,score,sort,type,topic,options,answer,analysis FROM t_theory_knowledge_test_paper_question WHERE test_paper_id=? ORDER BY sort").bind(&id).fetch_all(pool).await?;
    let mut grouped: HashMap<i32, Vec<Value>> = HashMap::new();
    for q in qs {
        grouped
            .entry(q.get::<Option<i32>, _>("type").unwrap_or_default())
            .or_default()
            .push(question_json(&q));
    }
    Ok(
        json!({"id":id,"testPaperId":Value::Null,"name":row.get::<Option<String>,_>("name"),"levelId":row.get::<Option<String>,_>("level_id"),"total":row.get::<Option<i32>,_>("total"),"passTheExamThan":row.get::<Option<String>,_>("pass_the_exam_than"),"passMark":row.get::<Option<i32>,_>("pass_mark"),"createTime":row.get::<Option<String>,_>("create_time"),"singleChoice":grouped.remove(&1).unwrap_or_default(),"multipleChoice":grouped.remove(&2).unwrap_or_default(),"judge":grouped.remove(&3).unwrap_or_default(),"completion":grouped.remove(&4).unwrap_or_default(),"shortAnswer":grouped.remove(&5).unwrap_or_default()}),
    )
}
pub(super) async fn list(State(state): State<AppState>) -> Response {
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
pub(super) async fn by_id(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
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
    match sqlx::query("SELECT id,name,level_id,total,pass_the_exam_than,pass_mark,create_time FROM t_theory_knowledge_test_paper WHERE id=? LIMIT 1").bind(id).fetch_optional(pool).await{Ok(Some(row))=>match paper(pool,&row).await{Ok(v)=>envelope_json(StatusCode::OK,200,Some(v),"ok",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")},Ok(None)=>envelope_response(StatusCode::OK,202,"未查询到该试卷",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
async fn level_ids(pool: &sqlx::MySqlPool, root: &str) -> Result<Vec<String>, sqlx::Error> {
    let rows = sqlx::query("SELECT id,parent_id FROM t_theory_question_level")
        .fetch_all(pool)
        .await?;
    let mut ids = vec![root.to_owned()];
    let mut changed = true;
    while changed {
        changed = false;
        for row in &rows {
            let id: String = row.get("id");
            if row
                .get::<Option<String>, _>("parent_id")
                .as_ref()
                .is_some_and(|parent| ids.contains(parent))
                && !ids.contains(&id)
            {
                ids.push(id);
                changed = true;
            }
        }
    }
    Ok(ids)
}
pub(super) async fn by_level_name(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let name = body.get("name").and_then(Value::as_str).unwrap_or("");
    let level_ids = match body
        .get("levelId")
        .and_then(Value::as_str)
        .filter(|level| !level.is_empty())
    {
        Some(level) => match level_ids(pool, level).await {
            Ok(ids) => Some(ids),
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        },
        None => None,
    };
    let mut sql = String::from("SELECT id,name,level_id,total,pass_the_exam_than,pass_mark,create_time FROM t_theory_knowledge_test_paper WHERE 1=1");
    if let Some(ids) = &level_ids {
        if ids.is_empty() {
            return envelope_json(StatusCode::OK, 200, Some(json!([])), "ok", "");
        }
        sql.push_str(" AND level_id IN (");
        sql.push_str(&vec!["?"; ids.len()].join(","));
        sql.push(')');
    }
    if !name.is_empty() {
        sql.push_str(" AND name LIKE ?");
    }
    sql.push_str(" ORDER BY create_time DESC");
    let mut query = sqlx::query(&sql);
    if let Some(ids) = &level_ids {
        for id in ids {
            query = query.bind(id);
        }
    }
    if !name.is_empty() {
        query = query.bind(format!("%{name}%"));
    }
    let rows = match query.fetch_all(pool).await {
        Ok(rows) => rows,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let mut out = Vec::with_capacity(rows.len());
    for row in rows {
        match paper(pool, &row).await {
            Ok(value) => out.push(value),
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        }
    }
    envelope_json(StatusCode::OK, 200, Some(json!(out)), "ok", "")
}

pub(super) async fn save(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let id = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|v| !v.is_empty())
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| Uuid::new_v4().to_string());
    if body.get("id").and_then(Value::as_str).is_some() {
        if sqlx::query("DELETE FROM t_theory_knowledge_test_paper_question WHERE test_paper_id=?")
            .bind(&id)
            .execute(&mut *tx)
            .await
            .is_err()
        {
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    }
    if sqlx::query("INSERT INTO t_theory_knowledge_test_paper (id,name,level_id,total,pass_the_exam_than,pass_mark,create_user_id,create_time) VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),level_id=VALUES(level_id),total=VALUES(total),pass_the_exam_than=VALUES(pass_the_exam_than),pass_mark=VALUES(pass_mark),create_user_id=VALUES(create_user_id)").bind(&id).bind(body.get("name").and_then(Value::as_str)).bind(body.get("levelId").and_then(Value::as_str)).bind(body.get("total").and_then(Value::as_i64)).bind(body.get("passTheExamThan").and_then(Value::as_str)).bind(body.get("passMark").and_then(Value::as_i64)).bind(&user.id).bind(crate::platform::clock::now_time_millis().to_string()).execute(&mut *tx).await.is_err(){let _=tx.rollback().await;return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");}
    for (kind, key) in [
        (1, "singleChoice"),
        (2, "multipleChoice"),
        (3, "judge"),
        (4, "completion"),
        (5, "shortAnswer"),
    ] {
        if let Some(items) = body.get(key).and_then(Value::as_array) {
            for (sort, item) in items.iter().enumerate() {
                let options = value_text(item.get("options"));
                let answer = value_text(item.get("answer"));
                let analysis = value_text(item.get("analysis"));
                if sqlx::query("INSERT INTO t_theory_knowledge_test_paper_question (id,test_paper_id,parent_id,score,sort,type,topic,options,answer,analysis) VALUES (?,?,?,?,?,?,?,?,?,?)").bind(Uuid::new_v4().to_string()).bind(&id).bind(item.get("parentId").and_then(Value::as_str)).bind(item.get("score").and_then(Value::as_i64)).bind(sort as i32).bind(kind).bind(item.get("topic").and_then(Value::as_str)).bind(options).bind(answer).bind(analysis).execute(&mut *tx).await.is_err(){let _=tx.rollback().await;return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");}
            }
        }
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, None, "ok", "")
}

pub(super) async fn delete(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
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
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if sqlx::query("DELETE FROM t_theory_knowledge_test_paper_question WHERE test_paper_id=?")
        .bind(id)
        .execute(&mut *tx)
        .await
        .is_err()
        || sqlx::query("DELETE FROM t_theory_knowledge_test_paper WHERE id=?")
            .bind(id)
            .execute(&mut *tx)
            .await
            .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, None, "ok", "")
}
