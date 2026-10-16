use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;
use uuid::Uuid;

use crate::platform::{
    auth::AuthUser,
    envelope::{envelope_json, envelope_response},
    state::AppState,
};

pub(super) fn content_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id": row.get::<String,_>("id"), "knowledgeId": row.get::<Option<String>,_>("knowledge_id"), "knowledgeSwfId": row.get::<Option<String>,_>("knowledge_swf_id"), "knowledgeTestId": row.get::<Option<String>,_>("knowledge_test_id"), "type": row.get::<Option<i32>,_>("type"), "topic": row.get::<Option<String>,_>("topic"), "options": row.get::<Option<String>,_>("options"), "answer": row.get::<Option<String>,_>("answer"), "analysis": row.get::<Option<String>,_>("analysis"), "sort": row.get::<Option<i32>,_>("sort"), "createUserId": row.get::<Option<String>,_>("create_user_id"), "createTime": row.get::<Option<String>,_>("create_time")})
}
fn test_json(row: &sqlx::mysql::MySqlRow, contents: Vec<Value>) -> Value {
    json!({"knowledgeTest": {"id": row.get::<String,_>("id"), "title": row.get::<Option<String>,_>("title"), "knowledgeId": row.get::<Option<String>,_>("knowledge_id"), "knowledgeSwfId": row.get::<Option<String>,_>("knowledge_swf_id"), "versions": row.get::<Option<i32>,_>("versions"), "createTime": row.get::<Option<String>,_>("create_time"), "createUserId": row.get::<Option<String>,_>("create_user_id")}, "knowledgeTestContents": contents})
}
pub(crate) async fn save(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(test) = body.get("knowledgeTest").and_then(Value::as_object) else {
        return envelope_response(StatusCode::OK, 202, "测验信息不能为空", "");
    };
    let Some(swf_id) = test
        .get("knowledgeSwfId")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = test
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
        .map(str::to_owned)
        .unwrap_or_else(|| Uuid::new_v4().to_string());
    let existing = match sqlx::query("SELECT knowledge_id,knowledge_swf_id,create_user_id,create_time FROM t_theory_knowledge_test WHERE id=?").bind(&id).fetch_optional(pool).await {
        Ok(row) => row,
        Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    };
    if test
        .get("id")
        .and_then(Value::as_str)
        .is_some_and(|id| !id.is_empty())
        && existing.is_none()
    {
        return envelope_response(StatusCode::OK, 202, "未查询到该测验", "");
    }
    let stored_swf = existing
        .as_ref()
        .and_then(|row| row.get::<Option<String>, _>("knowledge_swf_id"));
    let swf_id = stored_swf.as_deref().unwrap_or(swf_id);
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    // No consistent read may precede this parent lock: concurrent activations must
    // see the preceding writer's committed versions under REPEATABLE READ.
    match sqlx::query_scalar::<_, String>(
        "SELECT id FROM t_theory_knowledge_swf WHERE id=? FOR UPDATE",
    )
    .bind(swf_id)
    .fetch_optional(&mut *tx)
    .await
    {
        Ok(Some(_)) => {}
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该课件", ""),
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
    let existing = if existing.is_some() {
        match sqlx::query("SELECT knowledge_id,knowledge_swf_id,create_user_id,create_time FROM t_theory_knowledge_test WHERE id=?")
            .bind(&id).fetch_optional(&mut *tx).await {
            Ok(Some(row)) if row.get::<Option<String>, _>("knowledge_swf_id").as_deref() == Some(swf_id) => Some(row),
            Ok(_) => return envelope_response(StatusCode::OK, 202, "测验已删除或所属课件已变更", ""),
            Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
        }
    } else {
        None
    };
    let knowledge_id = match existing.as_ref() {
        Some(row) => row.get::<Option<String>, _>("knowledge_id"),
        None => test
            .get("knowledgeId")
            .and_then(Value::as_str)
            .map(str::to_owned),
    };
    let create_user = existing
        .as_ref()
        .and_then(|row| row.get::<Option<String>, _>("create_user_id"))
        .unwrap_or_else(|| user.id.clone());
    let create_time = existing
        .as_ref()
        .and_then(|row| row.get::<Option<String>, _>("create_time"))
        .unwrap_or_else(|| crate::platform::clock::now_time_millis().to_string());
    let mut version = test.get("versions").and_then(Value::as_i64).unwrap_or(1);
    let siblings = match sqlx::query(
        "SELECT id,versions FROM t_theory_knowledge_test WHERE knowledge_swf_id=?",
    )
    .bind(swf_id)
    .fetch_all(&mut *tx)
    .await
    {
        Ok(rows) => rows,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if siblings.is_empty()
        || (version == 0
            && !siblings
                .iter()
                .any(|row| row.get::<Option<i32>, _>("versions") == Some(1)))
    {
        version = 1;
    }
    if version == 1 {
        for sibling in &siblings {
            let sibling_id = sibling.get::<String, _>("id");
            if sibling_id != id
                && sqlx::query("UPDATE t_theory_knowledge_test SET versions=0 WHERE id=?")
                    .bind(sibling_id)
                    .execute(&mut *tx)
                    .await
                    .is_err()
            {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        }
    }
    let mutation = if existing.is_some() {
        sqlx::query("UPDATE t_theory_knowledge_test SET title=?,versions=?,knowledge_id=?,knowledge_swf_id=?,create_user_id=?,create_time=? WHERE id=?").bind(test.get("title").and_then(Value::as_str)).bind(version).bind(&knowledge_id).bind(swf_id).bind(&create_user).bind(&create_time).bind(&id).execute(&mut *tx).await
    } else {
        sqlx::query("INSERT INTO t_theory_knowledge_test (id,title,knowledge_id,knowledge_swf_id,versions,create_user_id,create_time) VALUES (?,?,?,?,?,?,?)").bind(&id).bind(test.get("title").and_then(Value::as_str)).bind(&knowledge_id).bind(swf_id).bind(version).bind(&create_user).bind(&create_time).execute(&mut *tx).await
    };
    if mutation.is_err() {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if let Some(contents) = body.get("knowledgeTestContents").and_then(Value::as_array) {
        let old_ids = match sqlx::query_scalar::<_, String>(
            "SELECT id FROM t_theory_knowledge_test_content WHERE knowledge_test_id=?",
        )
        .bind(&id)
        .fetch_all(&mut *tx)
        .await
        {
            Ok(ids) => ids,
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        };
        for content_id in old_ids {
            if sqlx::query("DELETE FROM t_theory_knowledge_test_content WHERE id=?")
                .bind(content_id)
                .execute(&mut *tx)
                .await
                .is_err()
            {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        }
        for (sort, content) in contents.iter().enumerate() {
            let content_id = content
                .get("id")
                .and_then(Value::as_str)
                .filter(|value| !value.is_empty())
                .map(str::to_owned)
                .unwrap_or_else(|| Uuid::new_v4().to_string());
            if sqlx::query("INSERT INTO t_theory_knowledge_test_content (id,knowledge_id,knowledge_swf_id,knowledge_test_id,type,topic,options,answer,analysis,sort,create_user_id,create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)").bind(&content_id).bind(&knowledge_id).bind(swf_id).bind(&id).bind(content.get("type").and_then(Value::as_i64)).bind(content.get("topic").and_then(Value::as_str)).bind(content.get("options").and_then(Value::as_str)).bind(content.get("answer").and_then(Value::as_str)).bind(content.get("analysis").and_then(Value::as_str)).bind(sort as i64).bind(&user.id).bind(crate::platform::clock::now_time_millis().to_string()).execute(&mut *tx).await.is_err() { return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误",""); }
        }
    }
    let contents = if body
        .get("knowledgeTestContents")
        .and_then(Value::as_array)
        .is_some()
    {
        match sqlx::query("SELECT id,knowledge_id,knowledge_swf_id,knowledge_test_id,type,topic,options,answer,analysis,sort,create_user_id,create_time FROM t_theory_knowledge_test_content WHERE knowledge_test_id=? ORDER BY sort").bind(&id).fetch_all(&mut *tx).await {
            Ok(rows) => json!(rows.iter().map(content_json).collect::<Vec<_>>()),
            Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
        }
    } else {
        Value::Null
    };
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"knowledgeTest":{"id":id,"title":test.get("title"),"knowledgeId":knowledge_id,"knowledgeSwfId":swf_id,"versions":version,"createUserId":create_user,"createTime":create_time},"knowledgeTestContents":contents}),
        ),
        "ok",
        "",
    )
}

pub(crate) async fn save_user(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(knowledge_id) = body
        .get("knowledgeId")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(swf_id) = body
        .get("knowledgeSwfId")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = Uuid::new_v4().to_string();
    let now = crate::platform::clock::now_time_millis().to_string();
    if sqlx::query("INSERT INTO t_theory_knowledge_test_user (id,knowledge_id,knowledge_swf_id,user_id,content,score,create_time) VALUES (?,?,?,?,?,?,?)").bind(&id).bind(knowledge_id).bind(swf_id).bind(&user.id).bind(body.get("content").and_then(Value::as_str)).bind(body.get("score").and_then(Value::as_i64)).bind(&now).execute(pool).await.is_err() { return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误",""); }
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"id":id,"knowledgeId":knowledge_id,"knowledgeSwfId":swf_id,"userId":user.id,"content":body.get("content"),"score":body.get("score"),"createTime":now}),
        ),
        "ok",
        "",
    )
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
    let mut result = Vec::with_capacity(tests.len());
    for test in tests {
        let test_id: String = test.get("id");
        let content_rows = match sqlx::query("SELECT id,knowledge_id,knowledge_swf_id,knowledge_test_id,type,topic,options,answer,analysis,sort,create_user_id,create_time FROM t_theory_knowledge_test_content WHERE knowledge_test_id=? ORDER BY create_time").bind(&test_id).fetch_all(pool).await { Ok(rows) => rows, Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","") };
        result.push(test_json(
            &test,
            content_rows.iter().map(content_json).collect(),
        ));
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
    let test = match sqlx::query("SELECT id FROM t_theory_knowledge_test WHERE knowledge_swf_id=? AND versions=1 ORDER BY create_time DESC LIMIT 1").bind(id).fetch_optional(pool).await {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_json(StatusCode::OK, 200, Some(json!([])), "ok", ""),
        Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    };
    match sqlx::query("SELECT id,knowledge_id,knowledge_swf_id,knowledge_test_id,type,topic,options,answer,analysis,sort,create_user_id,create_time FROM t_theory_knowledge_test_content WHERE knowledge_test_id=? ORDER BY create_time").bind(test.get::<String,_>("id")).fetch_all(pool).await { Ok(rows) => envelope_json(StatusCode::OK, 200, Some(json!(rows.iter().map(content_json).collect::<Vec<_>>())), "ok", ""), Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") }
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
    match sqlx::query("SELECT id,knowledge_id,knowledge_swf_id,user_id,content,score,create_time FROM t_theory_knowledge_test_user WHERE id=? LIMIT 1").bind(id).fetch_optional(pool).await {
        Ok(Some(row)) if user.is_admin || row.get::<Option<String>,_>("user_id").as_deref()==Some(user.id.as_str()) => envelope_json(StatusCode::OK,200,Some(json!({"id":row.get::<String,_>("id"),"knowledgeId":row.get::<Option<String>,_>("knowledge_id"),"knowledgeSwfId":row.get::<Option<String>,_>("knowledge_swf_id"),"userId":row.get::<Option<String>,_>("user_id"),"content":row.get::<Option<String>,_>("content"),"score":row.get::<Option<i32>,_>("score"),"createTime":row.get::<Option<String>,_>("create_time")})),"ok",""),
        Ok(Some(_)) => envelope_response(StatusCode::OK,207,"无权限",""),
        // Java returns ResponseResult.success(testUserDao.findById(id)): a missing row is a 200 with null data.
        Ok(None) => envelope_json(StatusCode::OK, 200, None, "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")
    }
}
