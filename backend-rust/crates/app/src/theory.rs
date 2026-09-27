use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use chrono::{Duration, Utc};
use serde_json::{json, Value};
use sqlx::Row;

use super::{envelope_json, envelope_response, AppState, AuthUser};

pub(crate) async fn list_classify(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query(
        "SELECT id, type, name, create_id, create_time FROM t_theory_knowledge_classify ORDER BY create_time DESC",
    )
    .fetch_all(pool)
    .await
    {
        Ok(rows) => {
            let mut difficulty = Vec::new();
            let mut specialty = Vec::new();
            for row in rows {
                let create_time = row.get::<Option<chrono::NaiveDateTime>,_>("create_time").map(|value| value.format("%Y-%m-%d %H:%M:%S").to_string());
                let value = json!({"id": row.get::<String,_>("id"), "type": row.get::<Option<i32>,_>("type"), "name": row.get::<Option<String>,_>("name"), "createId": row.get::<Option<String>,_>("create_id"), "createTime": create_time});
                if row.get::<Option<i32>, _>("type") == Some(1) { difficulty.push(value); } else { specialty.push(value); }
            }
            envelope_json(
                StatusCode::OK,
                200,
                Some(json!({"difficultyList": difficulty, "specialtyList": specialty})),
                "ok",
                "",
            )
        }
        Err(error) => {
            tracing::warn!(error = %error, "theory classification query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(crate) async fn list_basic(
    State(state): State<AppState>,
    Json(body): Json<serde_json::Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let requested_type = body
        .get("type")
        .and_then(|value| {
            value
                .as_i64()
                .or_else(|| value.as_str().and_then(|text| text.parse().ok()))
        })
        .unwrap_or(0);
    let parse_ids = |value: Option<&Value>| {
        value.and_then(|value| {
            value
                .as_array()
                .map(|values| {
                    values
                        .iter()
                        .filter_map(Value::as_str)
                        .map(str::to_owned)
                        .collect::<Vec<_>>()
                })
                .or_else(|| {
                    value
                        .as_str()
                        .and_then(|text| serde_json::from_str::<Vec<String>>(text).ok())
                })
        })
    };
    let difficulty = parse_ids(body.get("difficulty"));
    let specialty = parse_ids(body.get("specialty"));
    let mut sql = String::from("SELECT k.id,k.type,k.status,k.cover,k.title,k.create_user_id,k.create_time,COUNT(s.id) AS swfs,d.name AS difficulty_name,sp.name AS specialty_name FROM t_theory_knowledge k LEFT JOIN t_theory_knowledge_swf s ON s.knowledge_id=k.id LEFT JOIN t_theory_knowledge_classify d ON d.id=k.difficulty_id LEFT JOIN t_theory_knowledge_classify sp ON sp.id=k.specialty_id WHERE k.type=?");
    if let Some(ids) = &difficulty {
        if !ids.is_empty() {
            sql.push_str(" AND k.difficulty_id IN (");
            sql.push_str(&vec!["?"; ids.len()].join(","));
            sql.push(')');
        }
    }
    if let Some(ids) = &specialty {
        if !ids.is_empty() {
            sql.push_str(" AND k.specialty_id IN (");
            sql.push_str(&vec!["?"; ids.len()].join(","));
            sql.push(')');
        }
    }
    sql.push_str(" GROUP BY k.id,k.type,k.status,k.cover,k.title,k.create_user_id,k.create_time,d.name,sp.name ORDER BY k.create_time DESC");
    let mut query = sqlx::query(&sql).bind(requested_type);
    if let Some(ids) = &difficulty {
        for id in ids {
            query = query.bind(id);
        }
    }
    if let Some(ids) = &specialty {
        for id in ids {
            query = query.bind(id);
        }
    }
    let rows = match query.fetch_all(pool).await {
        Ok(rows) => rows,
        Err(error) => {
            tracing::warn!(error = %error, "theory knowledge list query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let data = rows.into_iter().map(|row| json!({"swfs": row.get::<i64,_>("swfs"), "createUserName": Value::Null, "specialtyName": row.get::<Option<String>,_>("specialty_name"), "difficultyName": row.get::<Option<String>,_>("difficulty_name"), "title": row.get::<Option<String>,_>("title"), "cover": row.get::<Option<String>,_>("cover"), "createTime": row.get::<Option<String>,_>("create_time"), "createUserId": row.get::<Option<String>,_>("create_user_id"), "id": row.get::<String,_>("id"), "status": row.get::<Option<i32>,_>("status").unwrap_or_default(), "type": row.get::<Option<i32>,_>("type").unwrap_or_default()})).collect::<Vec<_>>();
    envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", "")
}
pub(crate) async fn list_open(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let requested_type = body
        .get("type")
        .and_then(|value| {
            value
                .as_i64()
                .or_else(|| value.as_str().and_then(|text| text.parse().ok()))
        })
        .unwrap_or(0);
    let parse_ids = |value: Option<&Value>| {
        value.and_then(|value| {
            value
                .as_array()
                .map(|values| {
                    values
                        .iter()
                        .filter_map(Value::as_str)
                        .map(str::to_owned)
                        .collect::<Vec<_>>()
                })
                .or_else(|| {
                    value
                        .as_str()
                        .and_then(|text| serde_json::from_str::<Vec<String>>(text).ok())
                })
        })
    };
    let difficulty = parse_ids(body.get("difficulty"));
    let specialty = parse_ids(body.get("specialty"));
    let status = body
        .get("status")
        .and_then(|value| {
            value
                .as_array()
                .map(|values| values.iter().filter_map(Value::as_i64).collect::<Vec<_>>())
                .or_else(|| {
                    value
                        .as_str()
                        .and_then(|text| serde_json::from_str::<Vec<i64>>(text).ok())
                })
        })
        .unwrap_or_else(|| vec![1]);
    let status_placeholders = vec!["?"; status.len()].join(",");
    let mut sql = format!("SELECT k.id,k.type,k.status,k.cover,k.title,k.create_user_id,k.create_time,COUNT(s.id) AS swfs,d.name AS difficulty_name,sp.name AS specialty_name FROM t_theory_knowledge k LEFT JOIN t_theory_knowledge_swf s ON s.knowledge_id=k.id LEFT JOIN t_theory_knowledge_classify d ON d.id=k.difficulty_id LEFT JOIN t_theory_knowledge_classify sp ON sp.id=k.specialty_id WHERE k.type=? AND k.status IN ({status_placeholders})");
    if let Some(ids) = &difficulty {
        if !ids.is_empty() {
            sql.push_str(" AND k.difficulty_id IN (");
            sql.push_str(&vec!["?"; ids.len()].join(","));
            sql.push(')');
        }
    }
    if let Some(ids) = &specialty {
        if !ids.is_empty() {
            sql.push_str(" AND k.specialty_id IN (");
            sql.push_str(&vec!["?"; ids.len()].join(","));
            sql.push(')');
        }
    }
    sql.push_str(" GROUP BY k.id,k.type,k.status,k.cover,k.title,k.create_user_id,k.create_time,d.name,sp.name ORDER BY k.create_time DESC");
    let mut query = sqlx::query(&sql).bind(requested_type);
    for item in &status {
        query = query.bind(item);
    }
    if let Some(ids) = &difficulty {
        for id in ids {
            query = query.bind(id);
        }
    }
    if let Some(ids) = &specialty {
        for id in ids {
            query = query.bind(id);
        }
    }
    let rows = match query.fetch_all(pool).await {
        Ok(rows) => rows,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let mut data = Vec::with_capacity(rows.len());
    for row in rows {
        let id: String = row.get("id");
        let done = match sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM t_theory_knowledge_test_user WHERE user_id=? AND knowledge_id=? AND score=100").bind(&user.id).bind(&id).fetch_one(pool).await { Ok(value) => value, Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "") };
        let tests = match sqlx::query_scalar::<_, i64>(
            "SELECT COUNT(*) FROM t_theory_knowledge_test WHERE knowledge_id=? AND versions=1",
        )
        .bind(&id)
        .fetch_one(pool)
        .await
        {
            Ok(value) => value,
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        };
        data.push(json!({"swfs":row.get::<i64,_>("swfs"),"doneCount":done,"swfTestCount":tests,"createUserName":Value::Null,"specialtyName":row.get::<Option<String>,_>("specialty_name"),"difficultyName":row.get::<Option<String>,_>("difficulty_name"),"title":row.get::<Option<String>,_>("title"),"cover":row.get::<Option<String>,_>("cover"),"createTime":row.get::<Option<String>,_>("create_time"),"createUserId":row.get::<Option<String>,_>("create_user_id"),"id":id,"status":row.get::<Option<i32>,_>("status").unwrap_or_default(),"type":row.get::<Option<i32>,_>("type").unwrap_or_default()}));
    }
    envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", "")
}

pub(crate) async fn save_knowledge(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(knowledge) = body.get("knowledge").and_then(Value::as_object) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(title) = knowledge
        .get("title")
        .and_then(Value::as_str)
        .filter(|value| !value.trim().is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "标题不能是空!", "");
    };
    let Some(swfs) = body.get("knowledgeSwfs").and_then(Value::as_array) else {
        return envelope_response(StatusCode::OK, 202, "课件列表缺失", "");
    };
    if swfs.iter().any(|swf| {
        swf.get("title")
            .and_then(Value::as_str)
            .is_none_or(|value| value.trim().is_empty())
    }) {
        return envelope_response(StatusCode::OK, 202, "标题不能是空!", "");
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = knowledge
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
        .map(str::to_owned)
        .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let existing_owner = match sqlx::query_scalar::<_, Option<String>>(
        "SELECT create_user_id FROM t_theory_knowledge WHERE id=?",
    )
    .bind(&id)
    .fetch_optional(&mut *tx)
    .await
    {
        Ok(value) => value.flatten(),
        Err(_) => {
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let create_user_id = existing_owner.unwrap_or_else(|| user.id.clone());
    let now = (Utc::now() + Duration::hours(8))
        .format("%Y-%m-%d %H:%M:%S")
        .to_string();
    if sqlx::query("INSERT INTO t_theory_knowledge (id,type,status,cover,title,create_user_id,create_time,credit,difficulty_id,specialty_id) VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE type=VALUES(type),status=VALUES(status),cover=VALUES(cover),title=VALUES(title),credit=VALUES(credit),difficulty_id=VALUES(difficulty_id),specialty_id=VALUES(specialty_id)")
        .bind(&id).bind(knowledge.get("type").and_then(Value::as_i64)).bind(knowledge.get("status").and_then(Value::as_i64)).bind(knowledge.get("cover").and_then(Value::as_str)).bind(title).bind(&create_user_id).bind(&now).bind(knowledge.get("credit").and_then(Value::as_f64)).bind(knowledge.get("difficultyId").and_then(Value::as_str)).bind(knowledge.get("specialtyId").and_then(Value::as_str)).execute(&mut *tx).await.is_err() {
        let _ = tx.rollback().await; return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    let old_swfs = match sqlx::query("SELECT id FROM t_theory_knowledge_swf WHERE knowledge_id=?")
        .bind(&id)
        .fetch_all(&mut *tx)
        .await
    {
        Ok(rows) => rows,
        Err(_) => {
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    for row in old_swfs {
        let swf_id: String = row.get("id");
        let old_tests =
            match sqlx::query("SELECT id FROM t_theory_knowledge_test WHERE knowledge_swf_id=?")
                .bind(&swf_id)
                .fetch_all(&mut *tx)
                .await
            {
                Ok(rows) => rows,
                Err(_) => {
                    let _ = tx.rollback().await;
                    return envelope_response(
                        StatusCode::INTERNAL_SERVER_ERROR,
                        500,
                        "服务器错误",
                        "",
                    );
                }
            };
        if sqlx::query("DELETE FROM t_theory_knowledge_test_content WHERE knowledge_swf_id=?")
            .bind(&swf_id)
            .execute(&mut *tx)
            .await
            .is_err()
            || sqlx::query("DELETE FROM t_theory_knowledge_test_user WHERE knowledge_swf_id=?")
                .bind(&swf_id)
                .execute(&mut *tx)
                .await
                .is_err()
            || sqlx::query("DELETE FROM t_theory_knowledge_test WHERE knowledge_swf_id=?")
                .bind(&swf_id)
                .execute(&mut *tx)
                .await
                .is_err()
            || sqlx::query("DELETE FROM t_theory_knowledge_swf_record WHERE knowledge_swf_id=?")
                .bind(&swf_id)
                .execute(&mut *tx)
                .await
                .is_err()
        {
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
        let _ = old_tests;
    }
    if sqlx::query("DELETE FROM t_theory_knowledge_swf WHERE knowledge_id=?")
        .bind(&id)
        .execute(&mut *tx)
        .await
        .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    for (swf_index, swf) in swfs.iter().enumerate() {
        let swf_id = swf
            .get("id")
            .and_then(Value::as_str)
            .filter(|value| !value.is_empty())
            .map(str::to_owned)
            .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
        if sqlx::query("INSERT INTO t_theory_knowledge_swf (id,knowledge_id,cover,title,content,create_user_id,create_time,sort) VALUES (?,?,?,?,?,?,?,?)")
            .bind(&swf_id).bind(&id).bind(swf.get("cover").and_then(Value::as_str)).bind(swf.get("title").and_then(Value::as_str)).bind(swf.get("content").and_then(Value::as_str)).bind(&create_user_id).bind(&now).bind(swf.get("sort").and_then(Value::as_i64).unwrap_or(swf_index as i64)).execute(&mut *tx).await.is_err() {
            let _=tx.rollback().await; return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");
        }
        if let Some(tests) = swf
            .get("test")
            .or_else(|| swf.get("tests"))
            .and_then(Value::as_array)
        {
            for test in tests {
                let test_id = test
                    .get("id")
                    .and_then(Value::as_str)
                    .filter(|value| !value.is_empty())
                    .map(str::to_owned)
                    .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
                if sqlx::query("INSERT INTO t_theory_knowledge_test (id,knowledge_id,knowledge_swf_id,title,versions,create_user_id,create_time) VALUES (?,?,?,?,?,?,?)")
                    .bind(&test_id).bind(&id).bind(&swf_id).bind(test.get("title").and_then(Value::as_str)).bind(test.get("versions").and_then(Value::as_i64).unwrap_or(1)).bind(&create_user_id).bind(&now).execute(&mut *tx).await.is_err() {
                    let _=tx.rollback().await; return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");
                }
                if let Some(contents) = test
                    .get("knowledgeTestContents")
                    .or_else(|| test.get("contents"))
                    .and_then(Value::as_array)
                {
                    for (sort, content) in contents.iter().enumerate() {
                        let content_id = content
                            .get("id")
                            .and_then(Value::as_str)
                            .filter(|value| !value.is_empty())
                            .map(str::to_owned)
                            .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
                        if sqlx::query("INSERT INTO t_theory_knowledge_test_content (id,knowledge_id,knowledge_swf_id,knowledge_test_id,type,topic,options,answer,analysis,sort,create_user_id,create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")
                            .bind(&content_id).bind(&id).bind(&swf_id).bind(&test_id).bind(content.get("type").and_then(Value::as_i64)).bind(content.get("topic").and_then(Value::as_str)).bind(content.get("options").and_then(Value::as_str)).bind(content.get("answer").and_then(Value::as_str)).bind(content.get("analysis").and_then(Value::as_str)).bind(content.get("sort").and_then(Value::as_i64).unwrap_or(sort as i64)).bind(&create_user_id).bind(&now).execute(&mut *tx).await.is_err() {
                            let _=tx.rollback().await; return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","");
                        }
                    }
                }
            }
        }
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"id":id,"type":knowledge.get("type"),"status":knowledge.get("status"),"cover":knowledge.get("cover"),"title":title,"createUserId":create_user_id,"createTime":now,"credit":knowledge.get("credit"),"difficultyId":knowledge.get("difficultyId"),"specialtyId":knowledge.get("specialtyId")}),
        ),
        "ok",
        "",
    )
}

pub(crate) async fn save_record(
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
    let supplied_join = body.get("joinTime").and_then(Value::as_str).unwrap_or("");
    let now = (Utc::now() + Duration::hours(8))
        .format("%Y-%m-%d %H:%M:%S")
        .to_string();
    if supplied_join.trim().is_empty() {
        return envelope_json(
            StatusCode::OK,
            200,
            Some(
                json!({"id":Value::Null,"knowledgeId":knowledge_id,"knowledgeSwfId":swf_id,"userId":user.id,"joinTime":now,"exitTime":Value::Null}),
            ),
            "ok",
            "",
        );
    }
    let exit = body
        .get("exitTime")
        .and_then(Value::as_str)
        .filter(|value| !value.trim().is_empty())
        .unwrap_or(&now);
    let join_dt = match chrono::NaiveDateTime::parse_from_str(supplied_join, "%Y-%m-%d %H:%M:%S") {
        Ok(value) => value,
        Err(_) => return envelope_response(StatusCode::OK, 202, "时间格式错误", ""),
    };
    let exit_dt = match chrono::NaiveDateTime::parse_from_str(exit, "%Y-%m-%d %H:%M:%S") {
        Ok(value) => value,
        Err(_) => return envelope_response(StatusCode::OK, 202, "时间格式错误", ""),
    };
    if exit_dt < join_dt || (exit_dt - join_dt).num_minutes() < 1 {
        return envelope_json(StatusCode::OK, 200, None, "ok", "");
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(kind) = (match sqlx::query_scalar::<_, Option<i32>>(
        "SELECT type FROM t_theory_knowledge WHERE id=?",
    )
    .bind(knowledge_id)
    .fetch_optional(pool)
    .await
    {
        Ok(value) => value.flatten(),
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }) else {
        return envelope_response(StatusCode::OK, 500, "未查询到该教案", "");
    };
    let valid_swf = match sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM t_theory_knowledge_swf WHERE id=? AND knowledge_id=?",
    )
    .bind(swf_id)
    .bind(knowledge_id)
    .fetch_one(pool)
    .await
    {
        Ok(value) => value > 0,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if !valid_swf {
        return envelope_response(StatusCode::OK, 500, "数据异常", "");
    }
    let record_id = uuid::Uuid::new_v4().to_string();
    if sqlx::query("INSERT INTO t_theory_knowledge_swf_record (id,knowledge_id,knowledge_swf_id,type,user_id,join_time,exit_time) VALUES (?,?,?,?,?,?,?)").bind(&record_id).bind(knowledge_id).bind(swf_id).bind(kind).bind(&user.id).bind(supplied_join).bind(exit).execute(pool).await.is_err() { return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误",""); }
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"id":record_id,"knowledgeId":knowledge_id,"knowledgeSwfId":swf_id,"type":kind,"userId":user.id,"joinTime":supplied_join,"exitTime":exit}),
        ),
        "ok",
        "",
    )
}

pub(crate) async fn record_statistics(
    State(state): State<AppState>,
    Extension(user): Extension<super::AuthUser>,
    Json(body): Json<serde_json::Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let year = body
        .get("year")
        .and_then(serde_json::Value::as_str)
        .unwrap_or("");
    let month = body
        .get("month")
        .and_then(serde_json::Value::as_str)
        .filter(|v| !v.is_empty());
    let kind = body
        .get("type")
        .and_then(serde_json::Value::as_i64)
        .unwrap_or(0);
    let like = month
        .map(|m| format!("{year}-{m:0>2}%"))
        .unwrap_or_else(|| format!("{year}%"));
    let rows = match sqlx::query("SELECT join_time,exit_time FROM t_theory_knowledge_swf_record WHERE user_id=? AND type=? AND join_time LIKE ?").bind(&user.id).bind(kind).bind(&like).fetch_all(pool).await { Ok(rows) => rows, Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","") };
    let mut buckets = std::collections::HashMap::<String, i64>::new();
    for row in &rows {
        let join = row
            .get::<Option<String>, _>("join_time")
            .unwrap_or_default();
        let exit = row
            .get::<Option<String>, _>("exit_time")
            .unwrap_or_default();
        let key = if month.is_some() {
            join.get(8..10)
                .unwrap_or("0")
                .trim_start_matches('0')
                .to_owned()
        } else {
            join.get(5..7)
                .unwrap_or("0")
                .trim_start_matches('0')
                .to_owned()
        };
        let seconds = match sqlx::query_scalar::<_, Option<i64>>("SELECT TIMESTAMPDIFF(SECOND,?,?)")
            .bind(&join)
            .bind(&exit)
            .fetch_one(pool)
            .await
        {
            Ok(value) => value.unwrap_or(0),
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        };
        *buckets.entry(key).or_default() += seconds;
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"up":buckets,"down":{}})),
        "ok",
        "",
    )
}

pub(crate) async fn grade_count(
    State(state): State<AppState>,
    Extension(user): Extension<super::AuthUser>,
    Json(body): Json<serde_json::Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let year = body
        .get("year")
        .and_then(serde_json::Value::as_str)
        .unwrap_or("");
    let month = body
        .get("month")
        .and_then(serde_json::Value::as_str)
        .filter(|v| !v.is_empty());
    let like = month
        .map(|m| format!("{year}-{m:0>2}%"))
        .unwrap_or_else(|| format!("{year}%"));
    let count = match sqlx::query_scalar::<_,i64>("SELECT COUNT(*) FROM t_theory_knowledge_exam_user WHERE user_id=? AND state=4 AND end_time LIKE ?").bind(&user.id).bind(&like).fetch_one(pool).await { Ok(value) => value, Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","") };
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"up":{"all":count,"good":count},"down":{}})),
        "ok",
        "",
    )
}

pub(crate) async fn save_classify(
    State(state): State<AppState>,
    Extension(user): Extension<super::AuthUser>,
    Json(body): Json<serde_json::Value>,
) -> Response {
    let Some(name) = body
        .get("name")
        .and_then(serde_json::Value::as_str)
        .filter(|v| !v.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(kind) = body.get("type").and_then(serde_json::Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|v| !v.is_empty())
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    let now = (Utc::now() + Duration::hours(8))
        .format("%Y-%m-%d %H:%M:%S")
        .to_string();
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let result = sqlx::query("INSERT INTO t_theory_knowledge_classify (id,type,name,create_time,create_id) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE type=VALUES(type),name=VALUES(name)").bind(&id).bind(kind).bind(name).bind(&now).bind(&user.id).execute(&mut *tx).await;
    if result.is_err() {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"id":id,"type":kind,"name":name,"createId":user.id,"createTime":now})),
        "ok",
        "",
    )
}
pub(crate) async fn delete_classify(
    State(state): State<AppState>,
    Json(body): Json<serde_json::Value>,
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
    if sqlx::query("DELETE FROM t_theory_knowledge_classify WHERE id=?")
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
pub(crate) async fn delete_knowledge(
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
    if sqlx::query("DELETE FROM t_theory_knowledge_test_user WHERE knowledge_id=?")
        .bind(id)
        .execute(&mut *tx)
        .await
        .is_err()
        || sqlx::query("DELETE FROM t_theory_knowledge_swf_record WHERE knowledge_id=?")
            .bind(id)
            .execute(&mut *tx)
            .await
            .is_err()
        || sqlx::query("DELETE FROM t_theory_knowledge_test_content WHERE knowledge_id=?")
            .bind(id)
            .execute(&mut *tx)
            .await
            .is_err()
        || sqlx::query("DELETE FROM t_theory_knowledge_test WHERE knowledge_id=?")
            .bind(id)
            .execute(&mut *tx)
            .await
            .is_err()
        || sqlx::query("DELETE FROM t_theory_knowledge_swf WHERE knowledge_id=?")
            .bind(id)
            .execute(&mut *tx)
            .await
            .is_err()
        || sqlx::query("DELETE FROM t_theory_knowledge WHERE id=?")
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
