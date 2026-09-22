use axum::{extract::State, http::StatusCode, response::Response, Json};
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
