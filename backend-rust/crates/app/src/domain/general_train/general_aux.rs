use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::Row;
use std::collections::HashSet;

use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use crate::ws;

#[derive(Debug, Deserialize)]
pub(super) struct TrainQuery {
    #[serde(rename = "trainId")]
    pub(super) train_id: Option<String>,
}

#[derive(Debug, Deserialize)]
pub(super) struct UserQuery {
    #[serde(rename = "trainId")]
    pub(super) train_id: Option<String>,
    #[serde(rename = "userId")]
    pub(super) user_id: Option<String>,
}

fn db_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "general online endpoint database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

async fn can_read(
    pool: &sqlx::MySqlPool,
    train_table: &str,
    member_table: &str,
    train_id: &str,
    user: &AuthUser,
) -> Result<bool, sqlx::Error> {
    if user.is_admin {
        return Ok(true);
    }
    let sql = format!(
        "SELECT EXISTS(SELECT 1 FROM {train_table} t WHERE t.id=? AND (t.create_user=? OR EXISTS(SELECT 1 FROM {member_table} m WHERE m.train_id=t.id AND m.user_id=?))) AS allowed"
    );
    Ok(sqlx::query(&sql)
        .bind(train_id)
        .bind(&user.id)
        .bind(&user.id)
        .fetch_one(pool)
        .await?
        .get::<i64, _>("allowed")
        == 1)
}
async fn can_manage(
    pool: &sqlx::MySqlPool,
    train_id: &str,
    user: &AuthUser,
) -> Result<bool, sqlx::Error> {
    if user.is_admin {
        return Ok(true);
    }
    Ok(sqlx::query(
        "SELECT EXISTS(SELECT 1 FROM general_ticker_pat t WHERE t.id=? AND (t.create_user=? OR EXISTS(SELECT 1 FROM general_ticker_pat_train_user m WHERE m.train_id=t.id AND m.user_id=? AND m.role=1))) AS allowed",
    )
    .bind(train_id)
    .bind(&user.id)
    .bind(&user.id)
    .fetch_one(pool)
    .await?
    .get::<i64, _>("allowed")
        == 1)
}
async fn train_exists(
    pool: &sqlx::MySqlPool,
    train_table: &str,
    train_id: &str,
) -> Result<bool, sqlx::Error> {
    let sql = format!("SELECT EXISTS(SELECT 1 FROM {train_table} WHERE id=?) AS present");
    Ok(sqlx::query(&sql)
        .bind(train_id)
        .fetch_one(pool)
        .await?
        .get::<i64, _>("present")
        == 1)
}

async fn online_users(
    state: AppState,
    user: AuthUser,
    train_id: String,
    kind: &'static str,
    train_table: &'static str,
    member_table: &'static str,
) -> Response {
    let train_id = train_id.trim();
    if train_id.parse::<i32>().is_err() {
        return envelope_response(StatusCode::OK, 202, "训练ID无效", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match train_exists(pool, train_table, train_id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 202, "未查询到训练", "未查询到训练"),
        Err(error) => return db_error(error, "find online train"),
    }
    match can_read(pool, train_table, member_table, train_id, &user).await {
        Ok(true) => envelope_json(
            StatusCode::OK,
            200,
            Some(Value::Array(
                ws::notify::general_online_users(kind, train_id).await,
            )),
            "ok",
            "",
        ),
        Ok(false) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Err(error) => db_error(error, "authorize online users"),
    }
}

pub(super) async fn key_online(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<TrainQuery>,
) -> Response {
    let Some(train_id) = query.train_id.filter(|id| !id.is_empty()) else {
        return envelope_response(
            StatusCode::OK,
            202,
            "id to load is required for loading",
            "id to load is required for loading",
        );
    };
    let train_id = match train_id.parse::<i32>() {
        Ok(id) => id,
        Err(_) => return StatusCode::NOT_FOUND.into_response(),
    };
    online_users(
        state,
        user,
        train_id.to_string(),
        "key",
        "general_key_pat",
        "general_key_pat_user",
    )
    .await
}
pub(super) async fn ticker_online(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<TrainQuery>,
) -> Response {
    let Some(train_id) = query.train_id else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    online_users(
        state,
        user,
        train_id,
        "ticker",
        "general_ticker_pat",
        "general_ticker_pat_train_user",
    )
    .await
}
pub(super) async fn ticker_train_info(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let allowed = if user.is_admin {
        None
    } else {
        match sqlx::query("SELECT CAST(t.id AS CHAR) AS train_id FROM general_ticker_pat t WHERE t.create_user=? OR EXISTS(SELECT 1 FROM general_ticker_pat_train_user m WHERE m.train_id=t.id AND m.user_id=?)")
            .bind(&user.id)
            .bind(&user.id)
            .fetch_all(pool)
            .await
        {
            Ok(rows) => Some(rows.into_iter().map(|row| row.get::<String, _>("train_id")).collect::<HashSet<_>>()),
            Err(error) => return db_error(error, "authorize ticker train info"),
        }
    };
    let mut rooms = ws::notify::general_online_rooms("ticker").await;
    if let Some(allowed) = allowed {
        rooms.retain(|train_id, _| allowed.contains(train_id));
    }
    envelope_json(StatusCode::OK, 200, Some(json!(rooms)), "ok", "")
}

pub(super) async fn ticker_user(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<UserQuery>,
) -> Response {
    let Some(train_id) = query.train_id.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    let Some(user_id) = query.user_id.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "用户ID不能为空", "");
    };
    let train_number = match train_id.parse::<i32>() {
        Ok(value) => value,
        Err(_) => return envelope_response(StatusCode::OK, 202, "训练ID无效", ""),
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match train_exists(pool, "general_ticker_pat", &train_id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 202, "未查询到训练", ""),
        Err(error) => return db_error(error, "find ticker train"),
    }
    let allowed = if user.is_admin || user_id == user.id {
        true
    } else {
        match can_manage(pool, &train_id, &user).await {
            Ok(allowed) => allowed,
            Err(error) => return db_error(error, "authorize ticker user"),
        }
    };
    if !allowed {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let row = match sqlx::query("SELECT p.user_id,p.role,u.user_name,u.user_img FROM general_ticker_pat_train_user p LEFT JOIN t_user u ON u.id=p.user_id WHERE p.train_id=? AND p.user_id=? LIMIT 1")
        .bind(&train_id)
        .bind(&user_id)
        .fetch_optional(pool)
        .await
    {
        Ok(row) => row,
        Err(error) => return db_error(error, "load ticker user"),
    };
    let Some(row) = row else {
        return envelope_response(StatusCode::OK, 202, "未查询到人员信息", "");
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({
            "id": row.get::<Option<String>, _>("user_id"),
            "userName": row.get::<Option<String>, _>("user_name"),
            "userImg": row.get::<Option<String>, _>("user_img"),
            "role": row.get::<Option<i32>, _>("role"),
            "status": ws::notify::general_ticker_connection_status(train_number, &user_id).await
        })),
        "ok",
        "",
    )
}

pub(super) async fn ticker_update_status(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(train_id) = body.get("trainId").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    crate::domain::general_train::ticker::ticker_socket_update(
        state,
        user,
        train_id,
        body.get("status").and_then(Value::as_i64),
    )
    .await
}
