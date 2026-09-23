use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::json;
use sqlx::Row;

use super::{envelope_json, envelope_response, AppState};

pub(crate) async fn list_classify(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query(
        "SELECT id, type, name FROM t_theory_knowledge_classify ORDER BY create_time DESC",
    )
    .fetch_all(pool)
    .await
    {
        Ok(rows) => {
            let mut difficulty = Vec::new();
            let mut specialty = Vec::new();
            for row in rows {
                let value = json!({"id": row.get::<String,_>("id"), "type": row.get::<Option<i32>,_>("type"), "name": row.get::<Option<String>,_>("name"), "createTime": null});
                if row.get::<Option<i32>, _>("type") == Some(1) {
                    difficulty.push(value);
                } else {
                    specialty.push(value);
                }
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
        .and_then(serde_json::Value::as_i64)
        .unwrap_or(0);
    let rows = match sqlx::query("SELECT k.id,k.type,k.status,k.cover,k.title,k.create_user_id,k.create_time,COUNT(s.id) AS swfs,d.name AS difficulty_name,sp.name AS specialty_name FROM t_theory_knowledge k LEFT JOIN t_theory_knowledge_swf s ON s.knowledge_id=k.id LEFT JOIN t_theory_knowledge_classify d ON d.id=k.difficulty_id LEFT JOIN t_theory_knowledge_classify sp ON sp.id=k.specialty_id WHERE k.type=? GROUP BY k.id,k.type,k.status,k.cover,k.title,k.create_user_id,k.create_time,d.name,sp.name ORDER BY k.create_time DESC").bind(requested_type).fetch_all(pool).await {
        Ok(rows) => rows,
        Err(error) => { tracing::warn!(error = %error, "theory knowledge list query failed"); return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""); }
    };
    let data = rows.into_iter().map(|row| json!({"swfs": row.get::<i64,_>("swfs"), "createUserName": null, "specialtyName": row.get::<Option<String>,_>("specialty_name"), "difficultyName": row.get::<Option<String>,_>("difficulty_name"), "title": row.get::<Option<String>,_>("title"), "cover": row.get::<Option<String>,_>("cover"), "createTime": row.get::<Option<String>,_>("create_time"), "createUserId": row.get::<Option<String>,_>("create_user_id"), "id": row.get::<String,_>("id"), "status": row.get::<Option<i32>,_>("status").unwrap_or_default(), "type": row.get::<Option<i32>,_>("type").unwrap_or_default()})).collect::<Vec<_>>();
    envelope_json(StatusCode::OK, 200, Some(json!(data)), "ok", "")
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
    let rows=sqlx::query("SELECT join_time,exit_time FROM t_theory_knowledge_swf_record WHERE user_id=? AND type=? AND join_time LIKE ?").bind(&user.id).bind(kind).bind(&like).fetch_all(pool).await.unwrap_or_default();
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
        let seconds = sqlx::query_scalar::<_, Option<i64>>("SELECT TIMESTAMPDIFF(SECOND,?,?)")
            .bind(&join)
            .bind(&exit)
            .fetch_one(pool)
            .await
            .unwrap_or(Some(0))
            .unwrap_or(0);
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
    let count=sqlx::query_scalar::<_,i64>("SELECT COUNT(*) FROM t_theory_knowledge_exam_user WHERE user_id=? AND state=4 AND end_time LIKE ?").bind(&user.id).bind(&like).fetch_one(pool).await.unwrap_or(0);
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
        .and_then(serde_json::Value::as_str)
        .filter(|v| !v.is_empty())
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    match sqlx::query("INSERT INTO t_theory_knowledge_classify (id,type,name,create_time,create_id) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE type=VALUES(type),name=VALUES(name)").bind(&id).bind(kind).bind(name).bind(crate::now_time_millis().to_string()).bind(&user.id).execute(pool).await { Ok(_)=>envelope_json(StatusCode::OK,200,Some(json!({"id":id,"type":kind,"name":name})),"ok",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","") }
}
pub(crate) async fn delete_classify(
    State(state): State<AppState>,
    Json(body): Json<serde_json::Value>,
) -> Response {
    let Some(id) = body.get("id").and_then(serde_json::Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("DELETE FROM t_theory_knowledge_classify WHERE id=?")
        .bind(id)
        .execute(pool)
        .await
    {
        Ok(_) => envelope_json(StatusCode::OK, 200, None, "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
pub(crate) async fn delete_knowledge(
    State(state): State<AppState>,
    Json(body): Json<serde_json::Value>,
) -> Response {
    let Some(id) = body.get("id").and_then(serde_json::Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = pool.begin().await.unwrap();
    let _ = sqlx::query("DELETE FROM t_theory_knowledge_swf WHERE knowledge_id=?")
        .bind(id)
        .execute(&mut *tx)
        .await;
    if sqlx::query("DELETE FROM t_theory_knowledge WHERE id=?")
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
