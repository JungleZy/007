use axum::{extract::State, http::StatusCode, response::Response};
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
