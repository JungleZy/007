use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::{MySql, Row, Transaction};
use uuid::Uuid;

use crate::platform::{
    auth::AuthUser,
    envelope::{envelope_json, envelope_response},
    state::AppState,
};

fn database_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "masthead database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

fn view(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "trainId": row.get::<Option<String>, _>("train_id"),
        "content": row.get::<Option<String>, _>("content")
    })
}

async fn lock_owned_train(
    transaction: &mut Transaction<'_, MySql>,
    user_id: &str,
    train_id: &str,
) -> Result<Option<bool>, sqlx::Error> {
    Ok(
        sqlx::query("SELECT user_id FROM t_post_ticker_tape_train WHERE id=? LIMIT 1 FOR UPDATE")
            .bind(train_id)
            .fetch_optional(&mut **transaction)
            .await?
            .map(|row| row.get::<Option<String>, _>("user_id").as_deref() == Some(user_id)),
    )
}

pub(crate) async fn save(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(train_id) = body
        .get("trainId")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    let content = body.get("content").cloned().unwrap_or(Value::Null);
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start save transaction"),
    };
    match lock_owned_train(&mut transaction, &user.id, train_id).await {
        Ok(Some(true)) => {}
        Ok(Some(false)) => return envelope_response(StatusCode::OK, 207, "无权限", ""),
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该训练", ""),
        Err(error) => return database_error(error, "lock train for masthead"),
    }

    let requested_id = body
        .get("id")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty());
    let existing_by_id = match requested_id {
        Some(id) => {
            match sqlx::query("SELECT id,train_id FROM t_masthead WHERE id=? LIMIT 1 FOR UPDATE")
                .bind(id)
                .fetch_optional(&mut *transaction)
                .await
            {
                Ok(Some(row)) => Some(row),
                Ok(None) => return envelope_response(StatusCode::OK, 202, "报头不存在", ""),
                Err(error) => return database_error(error, "load masthead by id"),
            }
        }
        None => None,
    };
    if let Some(row) = existing_by_id.as_ref() {
        if row.get::<Option<String>, _>("train_id").as_deref() != Some(train_id) {
            return envelope_response(StatusCode::OK, 207, "报头编号与训练不匹配", "");
        }
    }
    let existing_by_train =
        match sqlx::query("SELECT id,train_id,content FROM t_masthead WHERE train_id=? LIMIT 1")
            .bind(train_id)
            .fetch_optional(&mut *transaction)
            .await
        {
            Ok(row) => row,
            Err(error) => return database_error(error, "load masthead by train"),
        };
    if let (Some(by_id), Some(by_train)) = (existing_by_id.as_ref(), existing_by_train.as_ref()) {
        if by_id.get::<String, _>("id") != by_train.get::<String, _>("id") {
            return envelope_response(StatusCode::OK, 207, "该训练已有其他报头", "");
        }
    }

    let masthead_id = existing_by_train
        .as_ref()
        .map(|row| row.get::<String, _>("id"))
        .or_else(|| {
            existing_by_id
                .as_ref()
                .map(|row| row.get::<String, _>("id"))
        })
        .unwrap_or_else(|| Uuid::new_v4().to_string());
    let content = match content {
        Value::Null => None,
        Value::String(value) => Some(value),
        value => Some(value.to_string()),
    };
    let result = if existing_by_train.is_some() || existing_by_id.is_some() {
        sqlx::query("UPDATE t_masthead SET train_id=?,content=? WHERE id=?")
            .bind(train_id)
            .bind(&content)
            .bind(&masthead_id)
            .execute(&mut *transaction)
            .await
    } else {
        sqlx::query("INSERT INTO t_masthead (id,train_id,content) VALUES (?,?,?)")
            .bind(&masthead_id)
            .bind(train_id)
            .bind(&content)
            .execute(&mut *transaction)
            .await
    };
    if let Err(error) = result {
        return database_error(error, "save masthead");
    }
    let row = match sqlx::query("SELECT id,train_id,content FROM t_masthead WHERE id=? LIMIT 1")
        .bind(&masthead_id)
        .fetch_one(&mut *transaction)
        .await
    {
        Ok(row) => row,
        Err(error) => return database_error(error, "reload masthead"),
    };
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit masthead save");
    }
    envelope_json(StatusCode::OK, 200, Some(view(&row)), "ok", "")
}

pub(crate) async fn find_by_train_id(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<TrainQuery>,
) -> Response {
    let Some(train_id) = query.train_id.filter(|value| !value.trim().is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let owned = match sqlx::query("SELECT user_id FROM t_post_ticker_tape_train WHERE id=? LIMIT 1")
        .bind(&train_id)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => {
            row.get::<Option<String>, _>("user_id").as_deref() == Some(user.id.as_str())
        }
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该训练", ""),
        Err(error) => return database_error(error, "load train for masthead query"),
    };
    if !owned {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    match sqlx::query("SELECT id,train_id,content FROM t_masthead WHERE train_id=? LIMIT 1")
        .bind(train_id)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(view(&row)), "ok", ""),
        Ok(None) => envelope_json(StatusCode::OK, 200, None, "ok", ""),
        Err(error) => database_error(error, "find masthead"),
    }
}

#[derive(Debug, serde::Deserialize)]
pub(crate) struct TrainQuery {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
}
