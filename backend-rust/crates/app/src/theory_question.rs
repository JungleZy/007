use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;
use std::collections::HashSet;

use super::{envelope_json, envelope_response, AppState, AuthUser};
fn question_value(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id":row.get::<String,_>("id"),"type":row.get::<Option<i32>,_>("type"),"topic":row.get::<Option<String>,_>("topic"),"options":row.get::<Option<String>,_>("options"),"answer":row.get::<Option<String>,_>("answer"),"analysis":row.get::<Option<String>,_>("analysis"),"createUserId":row.get::<Option<String>,_>("create_user_id"),"createTime":row.get::<Option<String>,_>("create_time"),"levelId":row.get::<Option<String>,_>("level_id")})
}

pub(crate) async fn save_question(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned);
    let question_id = id
        .clone()
        .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    let question_type = body
        .get("type")
        .and_then(Value::as_i64)
        .map(|value| value as i32);
    let topic = body.get("topic").and_then(Value::as_str);
    let options = body.get("options").and_then(Value::as_str);
    let answer = body.get("answer").and_then(Value::as_str);
    let analysis = body.get("analysis").and_then(Value::as_str);
    let level_id = body.get("levelId").and_then(Value::as_str);
    let mut tx = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let result = if id.is_some() {
        sqlx::query("UPDATE t_theory_question SET topic=?,create_user_id=?,analysis=?,type=?,answer=?,level_id=?,options=? WHERE id=?")
            .bind(topic).bind(&user.id).bind(analysis).bind(question_type).bind(answer).bind(level_id).bind(options).bind(&question_id)
            .execute(&mut *tx).await
    } else {
        sqlx::query("INSERT INTO t_theory_question (id,type,topic,options,answer,analysis,create_user_id,create_time,level_id) VALUES (?,?,?,?,?,?,?,?,?)")
            .bind(&question_id).bind(question_type).bind(topic).bind(options).bind(answer).bind(analysis).bind(&user.id).bind(crate::now_time_millis().to_string()).bind(level_id)
            .execute(&mut *tx).await
    };
    if result.is_err() {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    let row = match sqlx::query("SELECT id,type,topic,options,answer,analysis,create_user_id,create_time,level_id FROM t_theory_question WHERE id=?")
        .bind(&question_id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row,
        Ok(None) => { let _ = tx.rollback().await; return envelope_response(StatusCode::OK, 202, "未查询到该试题", ""); }
        Err(_) => { let _ = tx.rollback().await; return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""); }
    };
    let data = json!({"id": row.get::<String,_>("id"), "type": row.get::<Option<i32>,_>("type"), "topic": row.get::<Option<String>,_>("topic"), "options": row.get::<Option<String>,_>("options"), "answer": row.get::<Option<String>,_>("answer"), "analysis": row.get::<Option<String>,_>("analysis"), "createUserId": row.get::<Option<String>,_>("create_user_id"), "createTime": row.get::<Option<String>,_>("create_time"), "levelId": row.get::<Option<String>,_>("level_id")});
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, Some(data), "ok", "")
}

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
    let data = rows
        .into_iter()
        .filter(|row| {
            // level_id is nullable in t_theory_question; a NULL row belongs to no level
            // and must be skipped rather than panic the handler.
            row.get::<Option<String>, _>("level_id")
                .is_some_and(|level| ids.contains(&level))
                && kind.is_none_or(|v| row.get::<Option<i32>, _>("type") == Some(v))
                && row
                    .get::<Option<String>, _>("topic")
                    .is_none_or(|v| v.contains(name))
        })
        .map(|row| {
            json!({
                "id": row.get::<String, _>("id"),
                "type": row.get::<Option<i32>, _>("type"),
                "topic": row.get::<Option<String>, _>("topic"),
                "options": row.get::<Option<String>, _>("options"),
                "answer": row.get::<Option<String>, _>("answer"),
                "analysis": row.get::<Option<String>, _>("analysis"),
                "createUserId": row.get::<Option<String>, _>("create_user_id"),
                "createTime": row.get::<Option<String>, _>("create_time"),
                "levelId": row.get::<Option<String>, _>("level_id"),
                "createUserName": Value::Null
            })
        })
        .collect::<Vec<_>>();
    envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", "")
}
pub(crate) async fn export_questions(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(level_id) = body
        .get("levelId")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,topic,options,answer,analysis,create_user_id,create_time,level_id FROM t_theory_question WHERE level_id=? ORDER BY create_time DESC").bind(level_id).fetch_all(pool).await {
        Ok(rows) => envelope_json(StatusCode::OK,200,Some(json!(rows.iter().map(question_value).collect::<Vec<_>>())),"ok",""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")
    }
}

pub(crate) async fn delete_level(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
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
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if sqlx::query("DELETE FROM t_theory_question WHERE level_id=?")
        .bind(id)
        .execute(&mut *tx)
        .await
        .is_err()
        || sqlx::query("DELETE FROM t_theory_question_level WHERE id=?")
            .bind(id)
            .execute(&mut *tx)
            .await
            .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    let rows = match sqlx::query("SELECT id,parent_id,name,create_user_id,create_time FROM t_theory_question_level ORDER BY create_time DESC").fetch_all(&mut *tx).await { Ok(rows) => rows, Err(_) => { let _=tx.rollback().await; return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误",""); } };
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK,200,Some(json!(rows.iter().map(|row| json!({"id":row.get::<String,_>("id"),"parentId":row.get::<Option<String>,_>("parent_id"),"name":row.get::<Option<String>,_>("name"),"createUserId":row.get::<Option<String>,_>("create_user_id"),"createTime":row.get::<Option<String>,_>("create_time")})).collect::<Vec<_>>())),"ok","")
}

pub(crate) async fn export_template() -> Response {
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!([
            {"field":"type","title":"测验类型","required":true,"example":"1","remark":"1单选、2多选、3判断、4填空、5简答"},
            {"field":"topic","title":"题目","required":true,"example":"题干","remark":"题目正文"},
            {"field":"options","title":"选项","required":false,"example":"[\"A.选项一\"]","remark":"选择题填写"},
            {"field":"answer","title":"答案","required":false,"example":"A","remark":"多选题答案连写"},
            {"field":"analysis","title":"解析","required":false,"example":"解析","remark":"可留空"},
            {"field":"levelId","title":"所属题库 ID","required":true,"example":"level-id","remark":"必须是存在的题库节点"}
        ])),
        "ok",
        "",
    )
}

pub(crate) async fn save_batch(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(items) = body.as_array() else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    if items.is_empty() {
        return envelope_response(StatusCode::OK, 202, "导入数据为空或格式不完整", "");
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let mut saved = Vec::new();
    for (index, item) in items.iter().enumerate() {
        let row = index + 1;
        let topic = item
            .get("topic")
            .and_then(Value::as_str)
            .filter(|v| !v.trim().is_empty());
        let qtype = item.get("type").and_then(Value::as_i64);
        let level = item
            .get("levelId")
            .and_then(Value::as_str)
            .filter(|v| !v.trim().is_empty());
        if topic.is_none() || qtype.is_none() || level.is_none() {
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::OK, 202, &format!("第 {row} 行格式不完整"), "");
        }
        let id = uuid::Uuid::new_v4().to_string();
        if sqlx::query("INSERT INTO t_theory_question (id,type,topic,options,answer,analysis,create_user_id,create_time,level_id) VALUES (?,?,?,?,?,?,?,?,?)").bind(&id).bind(qtype).bind(topic).bind(item.get("options").and_then(Value::as_str)).bind(item.get("answer").and_then(Value::as_str)).bind(item.get("analysis").and_then(Value::as_str)).bind(&user.id).bind(crate::now_time_millis().to_string()).bind(level).execute(&mut *tx).await.is_err() { let _=tx.rollback().await; return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误",""); }
        saved.push(json!({"id":id,"type":qtype,"topic":topic,"levelId":level}));
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(saved)), "ok", "")
}

pub(crate) async fn delete_question(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
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
    if sqlx::query("DELETE FROM t_theory_question WHERE id=?")
        .bind(id)
        .execute(&mut *tx)
        .await
        .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    let rows = match sqlx::query("SELECT id,type,topic,options,answer,analysis,create_user_id,create_time,level_id FROM t_theory_question ORDER BY create_time DESC").fetch_all(&mut *tx).await { Ok(rows) => rows, Err(_) => { let _=tx.rollback().await; return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误",""); } };
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!(rows.iter().map(question_value).collect::<Vec<_>>())),
        "ok",
        "",
    )
}
