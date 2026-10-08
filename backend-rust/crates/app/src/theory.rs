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
        "SELECT id, type, name, create_time FROM t_theory_knowledge_classify ORDER BY create_time DESC",
    )
    .fetch_all(pool)
    .await
    {
        Ok(rows) => {
            let mut difficulty = Vec::new();
            let mut specialty = Vec::new();
            for row in rows {
                let create_time = row.get::<Option<chrono::NaiveDateTime>,_>("create_time").map(|value| value.format("%Y-%m-%d %H:%M:%S").to_string());
                let value = json!({"id": row.get::<String,_>("id"), "type": row.get::<Option<i32>,_>("type"), "name": row.get::<Option<String>,_>("name"), "createTime": create_time});
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
    let requested_type = body
        .get("type")
        .and_then(|value| {
            value
                .as_i64()
                .or_else(|| value.as_str().and_then(|text| text.parse().ok()))
        })
        .unwrap_or(0);
    list_basic_type(state, body, requested_type).await
}

pub(crate) async fn duty_business(State(state): State<AppState>) -> Response {
    list_basic_type(state, Value::Null, 1).await
}

pub(crate) async fn charge_equipment(State(state): State<AppState>) -> Response {
    list_basic_type(state, Value::Null, 2).await
}

async fn list_basic_type(state: AppState, body: Value, requested_type: i64) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
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
                json!({"id":body.get("id"),"knowledgeId":knowledge_id,"knowledgeSwfId":swf_id,"userId":body.get("userId"),"joinTime":now,"exitTime":body.get("exitTime"),"type":body.get("type").and_then(|value| value.as_i64().or_else(|| value.as_str().and_then(|text| text.trim().parse().ok()))).unwrap_or(0)}),
            ),
            "",
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
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let year = body.get("year").and_then(Value::as_str).unwrap_or("");
    let month = body
        .get("month")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty());
    let kind = body
        .get("type")
        .and_then(|value| {
            value
                .as_i64()
                .or_else(|| value.as_str().and_then(|text| text.parse().ok()))
        })
        .unwrap_or(0);
    let like = match month {
        Some(month) => match month.parse::<i32>() {
            Ok(month) => format!("{year}-{month:02}%"),
            Err(_) => return envelope_response(StatusCode::OK, 202, "请求参数错误", ""),
        },
        None => format!("{year}%"),
    };
    match study_statistics(pool, &user.id, kind, &like, month.is_none()).await {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(error) => {
            tracing::warn!(error = %error, "theory study statistics query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

// TheoryKnowledgeService.count builds perSwf via HashMap.merge (not put): new
// keys prepend to a linked bucket. Resizes preserve each split chain's order.
// Java tree bins (>=8 colliding entries once capacity reaches 64) can move the
// root to the front; this linked-bin emulator does not model that pathological
// collision order. Ordinary UUID/String courseware ids retain exact parity.
#[derive(Default)]
struct JavaStudyDurations {
    values: std::collections::HashMap<String, (i64, usize, u32)>,
    capacity: usize,
}

fn java_string_hash(key: &str) -> u32 {
    let hash = key.encode_utf16().fold(0u32, |hash, unit| {
        hash.wrapping_mul(31).wrapping_add(u32::from(unit))
    });
    hash ^ (hash >> 16)
}

impl JavaStudyDurations {
    fn merge(&mut self, key: String, seconds: i64) {
        // HashMap.merge checks the threshold before lookup, even for an update.
        if self.capacity == 0 {
            self.capacity = 16;
        } else if self.values.len() > self.capacity * 3 / 4 {
            self.capacity *= 2;
        }
        let size = self.values.len();
        let hash = java_string_hash(&key);
        if let Some(value) = self.values.get_mut(&key) {
            value.0 += seconds;
            return;
        }
        let colliders = if size >= 7 && self.capacity < 64 {
            self.values
                .values()
                .filter(|value| {
                    (value.2 as usize & (self.capacity - 1))
                        == (hash as usize & (self.capacity - 1))
                })
                .count()
        } else {
            0
        };
        self.values.insert(key, (seconds, size, hash));
        if colliders >= 7 {
            self.capacity *= 2;
        }
    }

    fn ordered(&self) -> impl Iterator<Item = (&String, i64)> {
        let mut entries = self.values.iter().collect::<Vec<_>>();
        entries.sort_unstable_by_key(|(_, (_, insertion, hash))| {
            (
                *hash as usize & (self.capacity - 1),
                std::cmp::Reverse(*insertion),
            )
        });
        entries
            .into_iter()
            .map(|(key, (seconds, _, _))| (key, *seconds))
    }
}

async fn study_statistics(
    pool: &sqlx::MySqlPool,
    user_id: &str,
    kind: i64,
    like: &str,
    by_month: bool,
) -> Result<Value, sqlx::Error> {
    let records = sqlx::query("SELECT knowledge_id,knowledge_swf_id,join_time,ABS(TIMESTAMPDIFF(SECOND,join_time,exit_time)) AS seconds FROM t_theory_knowledge_swf_record WHERE user_id=? AND type=? AND join_time LIKE ?")
        .bind(user_id).bind(kind).bind(like).fetch_all(pool).await?;
    if records.is_empty() {
        return Ok(json!({}));
    }
    let mut up = std::collections::HashMap::<String, i64>::new();
    let mut durations = std::collections::HashMap::<String, JavaStudyDurations>::new();
    for record in records {
        let join: String = record.try_get("join_time")?;
        let component = if by_month {
            join.get(5..7)
        } else {
            join.get(8..10)
        };
        let key = component
            .and_then(|value| value.parse::<u32>().ok())
            .ok_or_else(|| sqlx::Error::Protocol("invalid stored study join time".to_owned()))?
            .to_string();
        let seconds: i64 = record.try_get("seconds")?;
        *up.entry(key).or_default() += seconds;
        durations
            .entry(record.try_get("knowledge_id")?)
            .or_default()
            .merge(record.try_get("knowledge_swf_id")?, seconds);
    }
    let ids = durations.keys().collect::<Vec<_>>();
    let mut knowledge_query = sqlx::QueryBuilder::<sqlx::MySql>::new("SELECT id,type,status,cover,title,create_user_id,create_time,credit,difficulty_id,specialty_id FROM t_theory_knowledge WHERE id IN (");
    let mut knowledge_ids = knowledge_query.separated(",");
    for id in &ids {
        knowledge_ids.push_bind(id);
    }
    knowledge_ids.push_unseparated(")");
    let knowledges = knowledge_query.build().fetch_all(pool).await?;
    let mut swf_query = sqlx::QueryBuilder::<sqlx::MySql>::new(
        "SELECT id,knowledge_id,title FROM t_theory_knowledge_swf WHERE knowledge_id IN (",
    );
    let mut swf_ids = swf_query.separated(",");
    for id in &ids {
        swf_ids.push_bind(id);
    }
    swf_ids.push_unseparated(")");
    let swfs = swf_query.build().fetch_all(pool).await?;
    let mut answer_query = sqlx::QueryBuilder::<sqlx::MySql>::new(
        "SELECT knowledge_id,COUNT(*) AS answers FROM t_theory_knowledge_test_user WHERE user_id=",
    );
    answer_query
        .push_bind(user_id)
        .push(" AND knowledge_id IN (");
    let mut answer_ids = answer_query.separated(",");
    for id in &ids {
        answer_ids.push_bind(id);
    }
    answer_ids.push_unseparated(") GROUP BY knowledge_id");
    let answers = answer_query.build().fetch_all(pool).await?;
    let answer_counts = answers
        .iter()
        .map(|row| {
            Ok((
                row.try_get::<String, _>("knowledge_id")?,
                row.try_get::<i64, _>("answers")?,
            ))
        })
        .collect::<Result<std::collections::HashMap<_, _>, sqlx::Error>>()?;
    let mut swf_counts = std::collections::HashMap::<String, i64>::new();
    let mut swf_titles = std::collections::HashMap::<String, Option<String>>::new();
    for swf in swfs {
        *swf_counts.entry(swf.try_get("knowledge_id")?).or_default() += 1;
        swf_titles.insert(swf.try_get("id")?, swf.try_get("title")?);
    }
    let mut down = serde_json::Map::new();
    for knowledge in knowledges {
        let id: String = knowledge.try_get("id")?;
        let completed = swf_counts.get(&id).copied().unwrap_or(0)
            == answer_counts.get(&id).copied().unwrap_or(0);
        let key = study_knowledge_key(&knowledge, completed)?;
        let mut titled = serde_json::Map::new();
        if let Some(per_swf) = durations.get(&id) {
            for (swf_id, seconds) in per_swf.ordered() {
                if let Some(title) = swf_titles.get(swf_id) {
                    titled.insert(
                        title.as_deref().unwrap_or("null").to_owned(),
                        json!(seconds),
                    );
                }
            }
        }
        down.insert(key, Value::Object(titled));
    }
    Ok(json!({"up":up,"down":down}))
}

// Jackson serializes the Java entity map key through Lombok's field-ordered toString.
fn study_knowledge_key(
    row: &sqlx::mysql::MySqlRow,
    completed: bool,
) -> Result<String, sqlx::Error> {
    let text = |column| -> Result<&str, sqlx::Error> {
        Ok(row.try_get::<Option<&str>, _>(column)?.unwrap_or("null"))
    };
    let number = |column| -> Result<String, sqlx::Error> {
        Ok(row
            .try_get::<Option<i32>, _>(column)?
            .map(|value| value.to_string())
            .unwrap_or_else(|| "null".to_owned()))
    };
    let credit = if !completed {
        "0.0".to_owned()
    } else {
        row.try_get::<Option<f64>, _>("credit")?
            .map(study_credit_text)
            .unwrap_or_else(|| "null".to_owned())
    };
    Ok(format!("TheoryKnowledgeEntity(id={}, type={}, status={}, cover={}, title={}, createUserId={}, createTime={}, credit={}, difficultyId={}, specialtyId={})", text("id")?, number("type")?, number("status")?, text("cover")?, text("title")?, text("create_user_id")?, text("create_time")?, credit, text("difficulty_id")?, text("specialty_id")?))
}

fn study_credit_text(value: f64) -> String {
    let decimal = |text: String| {
        if text.contains('.') {
            text
        } else {
            format!("{text}.0")
        }
    };
    if value != 0.0 && !(0.001..10_000_000.0).contains(&value.abs()) {
        let scientific = format!("{value:e}");
        let (mantissa, exponent) = scientific.split_once('e').expect("float exponent");
        format!("{}E{exponent}", decimal(mantissa.to_owned()))
    } else {
        decimal(value.to_string())
    }
}

#[cfg(test)]
mod study_statistics_tests {
    use super::study_credit_text;

    #[test]
    fn display_credit_keeps_java_double_spelling() {
        for (value, expected) in [
            (0.0, "0.0"),
            (-0.0, "-0.0"),
            (2.0, "2.0"),
            (2.5, "2.5"),
            (0.0001, "1.0E-4"),
            (10_000_000.0, "1.0E7"),
        ] {
            assert_eq!(study_credit_text(value), expected);
        }
    }
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
    let supplied_id = body
        .get("id")
        .and_then(|value| match value {
            Value::String(id) => Some(id.clone()),
            Value::Number(id) => Some(id.to_string()),
            _ => None,
        })
        .filter(|id| !id.is_empty());
    let creating = supplied_id.is_none();
    let id = supplied_id.unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    let now = (Utc::now() + Duration::hours(8))
        .format("%Y-%m-%d %H:%M:%S")
        .to_string();
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if !creating {
        match sqlx::query_scalar::<_, String>(
            "SELECT id FROM t_theory_knowledge_classify WHERE id=? FOR UPDATE",
        )
        .bind(&id)
        .fetch_optional(&mut *tx)
        .await
        {
            Ok(Some(_)) => {}
            Ok(None) => {
                return envelope_response(StatusCode::OK, 202, "未查询到该分类", "未查询到该分类");
            }
            Err(error) => {
                tracing::warn!(%error, "classification update lookup failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        }
    }
    let result = if creating {
        sqlx::query("INSERT INTO t_theory_knowledge_classify (id,type,name,create_time,create_id) VALUES (?,?,?,?,?)")
            .bind(&id).bind(kind).bind(name).bind(&now).bind(&user.id).execute(&mut *tx).await
    } else {
        sqlx::query("UPDATE t_theory_knowledge_classify SET type=?,name=? WHERE id=?")
            .bind(kind)
            .bind(name)
            .bind(&id)
            .execute(&mut *tx)
            .await
    };
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
        Some(json!({"id":id,"type":kind,"name":name,"createTime":now})),
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

#[cfg(test)]
mod study_map_tests {
    use super::*;

    #[test]
    fn java_merge_bucket_order_and_utf16_hash_match_jdk21() {
        let mut durations = JavaStudyDurations::default();
        for key in ["AaAa", "AaBB", "BBAa", "BBBB"] {
            durations.merge(key.to_owned(), 1);
        }
        durations.merge("AaAa".to_owned(), 5);
        assert_eq!(
            durations
                .ordered()
                .map(|(key, seconds)| (key.as_str(), seconds))
                .collect::<Vec<_>>(),
            [("BBBB", 1), ("BBAa", 1), ("AaBB", 1), ("AaAa", 6)]
        );
        assert_eq!(java_string_hash("学习😀"), 718097488);
    }

    #[test]
    fn java_merge_resize_preserves_split_chains_and_pre_call_threshold() {
        let mut durations = JavaStudyDurations::default();
        for index in 0..13 {
            durations.merge(format!("k{index}"), 1);
        }
        assert_eq!(durations.capacity, 16);
        durations.merge("k13".to_owned(), 1);
        assert_eq!(durations.capacity, 32);
        assert_eq!(
            durations
                .ordered()
                .map(|(key, _)| key.as_str())
                .collect::<Vec<_>>(),
            [
                "k0", "k1", "k2", "k3", "k4", "k11", "k5", "k10", "k6", "k13", "k7", "k12", "k8",
                "k9"
            ]
        );
    }
}
