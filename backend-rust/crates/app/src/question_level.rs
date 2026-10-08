use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;

pub(crate) async fn save_level(
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
        .filter(|v| !v.is_empty())
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    let parent_id = body.get("parentId").and_then(Value::as_str);
    let name = body.get("name").and_then(Value::as_str);
    if name.is_none_or(|v| v.trim().is_empty()) {
        return envelope_response(StatusCode::OK, 202, "题库名称不能为空", "");
    }
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => {
            tracing::warn!(%error, "start question level save failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let result = if body
        .get("id")
        .and_then(Value::as_str)
        .is_some_and(|v| !v.is_empty())
    {
        sqlx::query("UPDATE t_theory_question_level SET parent_id=?,name=? WHERE id=?")
            .bind(parent_id)
            .bind(name)
            .bind(&id)
            .execute(&mut *tx)
            .await
    } else {
        sqlx::query("INSERT INTO t_theory_question_level (id,parent_id,name,create_user_id,create_time) VALUES (?,?,?,?,?)").bind(&id).bind(parent_id).bind(name).bind(&user.id).bind(crate::now_time_millis().to_string()).execute(&mut *tx).await
    };
    if let Err(error) = result {
        tracing::warn!(%error, "save question level failed");
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    let row = match sqlx::query("SELECT id,parent_id,name,create_user_id,create_time FROM t_theory_question_level WHERE id=?")
        .bind(&id)
        .fetch_optional(&mut *tx)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => {
            // Updating an id that does not exist: Java throws IllegalArgumentException -> 202.
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::OK, 202, "未查询到该题目分类", "未查询到该题目分类");
        }
        Err(error) => {
            tracing::warn!(%error, "reload question level failed");
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    if let Err(error) = tx.commit().await {
        tracing::warn!(%error,"commit question level failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"id":row.get::<String,_>("id"),"parentId":row.get::<Option<String>,_>("parent_id"),"name":row.get::<Option<String>,_>("name"),"createUserId":row.get::<Option<String>,_>("create_user_id"),"createTime":row.get::<Option<String>,_>("create_time")}),
        ),
        "ok",
        "",
    )
}
