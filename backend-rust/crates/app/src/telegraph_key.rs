use crate::statistical::lock_user;
use crate::{
    platform::{
        auth::AuthUser,
        envelope::{envelope_json, envelope_response},
        state::AppState,
    },
    scoring,
};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde::Deserialize;
use serde_json::json;
use sqlx::Row;

#[derive(Debug, Deserialize)]
pub(crate) struct TypeRequest {
    #[serde(rename = "type")]
    train_type: Option<i32>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct SaveRequest {
    id: Option<String>,
    #[serde(rename = "type")]
    train_type: Option<i32>,
    #[serde(rename = "totalTime")]
    total_time: Option<i32>,
    #[serde(rename = "totalNum")]
    total_num: Option<i32>,
    #[serde(rename = "totalError")]
    total_error: Option<i32>,
}

fn database_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "telegraph key database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

pub(crate) async fn find_by_type(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TypeRequest>,
) -> Response {
    let Some(train_type) = body.train_type else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,total_time,total_num,total_error FROM t_telegraph_key_pat_train WHERE create_user_id=? AND type=? LIMIT 1")
        .bind(&user.id)
        .bind(train_type)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(
            StatusCode::OK,
            200,
            Some(json!({
                "id": row.get::<String, _>("id"),
                "type": row.get::<Option<i32>, _>("type"),
                "totalTime": row.get::<Option<i32>, _>("total_time"),
                "totalNum": row.get::<Option<i32>, _>("total_num"),
                "totalError": row.get::<Option<i32>, _>("total_error")
            })),
            "ok",
            "",
        ),
        Ok(None) => envelope_json(
            StatusCode::OK,
            200,
            Some(json!({
                "id": null,
                "type": train_type,
                "totalTime": 0,
                "totalNum": 0,
                "totalError": 0
            })),
            "ok",
            "",
        ),
        Err(error) => database_error(error, "find by type"),
    }
}

pub(crate) async fn save(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<SaveRequest>,
) -> Response {
    let (Some(train_type), Some(total_time), Some(total_num), Some(total_error)) = (
        body.train_type,
        body.total_time,
        body.total_num,
        body.total_error,
    ) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start save transaction"),
    };
    match lock_user(&mut transaction, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(error) => return database_error(error, "lock user for save"),
    }

    let train_id = match body.id.filter(|id| !id.is_empty()) {
        Some(id) => {
            let row = match sqlx::query(
                "SELECT create_user_id,type FROM t_telegraph_key_pat_train WHERE id=? LIMIT 1 FOR UPDATE",
            )
            .bind(&id)
            .fetch_optional(&mut *transaction)
            .await
            {
                Ok(Some(row)) => row,
                Ok(None) => {
                    return envelope_response(StatusCode::OK, 202, "未查询到该训练", "")
                }
                Err(error) => return database_error(error, "load train for update"),
            };
            if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
                return envelope_response(StatusCode::OK, 207, "无权修改他人电子键训练", "");
            }
            if let Err(error) = sqlx::query(
                "UPDATE t_telegraph_key_pat_train SET total_time=?,total_num=?,total_error=? WHERE id=?",
            )
            .bind(total_time)
            .bind(total_num)
            .bind(total_error)
            .bind(&id)
            .execute(&mut *transaction)
            .await
            {
                return database_error(error, "update train");
            }
            id
        }
        None => {
            match sqlx::query("SELECT id FROM t_telegraph_key_pat_train WHERE create_user_id=? AND type=? LIMIT 1")
                .bind(&user.id)
                .bind(train_type)
                .fetch_optional(&mut *transaction)
                .await
            {
                Ok(Some(_)) => {
                    return envelope_response(StatusCode::OK, 202, "相同类型训练已存在", "")
                }
                Ok(None) => {}
                Err(error) => return database_error(error, "check duplicate train"),
            }
            let id = uuid::Uuid::new_v4().to_string();
            if let Err(error) = sqlx::query("INSERT INTO t_telegraph_key_pat_train (id,create_user_id,type,total_time,total_num,total_error,create_time) VALUES (?,?,?,?,?,?,NOW())")
                .bind(&id)
                .bind(&user.id)
                .bind(train_type)
                .bind(total_time)
                .bind(total_num)
                .bind(total_error)
                .execute(&mut *transaction)
                .await
            {
                return database_error(error, "insert train");
            }
            id
        }
    };

    let average_speed = scoring::rate_per_minute(i64::from(total_num), i64::from(total_time));
    let statistical = match sqlx::query("SELECT id,total_count FROM t_telegraph_key_train_statistical WHERE user_id=? AND type=? LIMIT 1")
        .bind(&user.id)
        .bind(train_type)
        .fetch_optional(&mut *transaction)
        .await
    {
        Ok(row) => row,
        Err(error) => return database_error(error, "load statistics for update"),
    };
    let statistical_result = if let Some(row) = statistical {
        sqlx::query("UPDATE t_telegraph_key_train_statistical SET avg_speed=?,total_count=?,total_time=? WHERE id=?")
            .bind(average_speed)
            .bind(row.get::<Option<i32>, _>("total_count").unwrap_or(0) + 1)
            .bind(total_time.to_string())
            .bind(row.get::<String, _>("id"))
            .execute(&mut *transaction)
            .await
    } else {
        sqlx::query("INSERT INTO t_telegraph_key_train_statistical (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,?,?,?,?,?)")
            .bind(uuid::Uuid::new_v4().to_string())
            .bind(train_type)
            .bind(1)
            .bind(total_time.to_string())
            .bind(average_speed)
            .bind(&user.id)
            .execute(&mut *transaction)
            .await
    };
    if let Err(error) = statistical_result {
        return database_error(error, "save statistics");
    }
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit save transaction");
    }
    tracing::debug!(train_id, user_id = %user.id, train_type, "telegraph key training saved");
    envelope_response(StatusCode::OK, 200, "ok", "")
}

pub(crate) async fn clear(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TypeRequest>,
) -> Response {
    let Some(train_type) = body.train_type else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start clear transaction"),
    };
    match lock_user(&mut transaction, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(error) => return database_error(error, "lock user for clear"),
    }
    let existing = match sqlx::query(
        "SELECT id FROM t_telegraph_key_pat_train WHERE create_user_id=? AND type=? LIMIT 1",
    )
    .bind(&user.id)
    .bind(train_type)
    .fetch_optional(&mut *transaction)
    .await
    {
        Ok(row) => row,
        Err(error) => return database_error(error, "load train for clear"),
    };
    let train_id = if let Some(row) = existing {
        let id = row.get::<String, _>("id");
        if let Err(error) = sqlx::query("UPDATE t_telegraph_key_pat_train SET total_time=0,total_num=0,total_error=0 WHERE id=?")
            .bind(&id)
            .execute(&mut *transaction)
            .await
        {
            return database_error(error, "clear train");
        }
        id
    } else {
        let id = uuid::Uuid::new_v4().to_string();
        if let Err(error) = sqlx::query("INSERT INTO t_telegraph_key_pat_train (id,create_user_id,type,total_time,total_num,total_error,create_time) VALUES (?,?,?,0,0,0,NOW())")
            .bind(&id)
            .bind(&user.id)
            .bind(train_type)
            .execute(&mut *transaction)
            .await
        {
            return database_error(error, "insert cleared train");
        }
        id
    };
    let ids: Vec<String> = match sqlx::query_scalar(
        "SELECT id FROM t_telegraph_key_train_statistical WHERE user_id=? AND type=? ORDER BY id",
    )
    .bind(&user.id)
    .bind(train_type)
    .fetch_all(&mut *transaction)
    .await
    {
        Ok(ids) => ids,
        Err(error) => return database_error(error, "discover statistics for clear"),
    };
    for id in ids {
        if let Err(error) = sqlx::query("UPDATE t_telegraph_key_train_statistical SET total_count=0,total_time='0',avg_speed=0 WHERE id=?")
            .bind(id)
            .execute(&mut *transaction)
            .await
        {
            return database_error(error, "clear statistics");
        }
    }
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit clear transaction");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({
            "id": train_id,
            "type": train_type,
            "totalTime": 0,
            "totalNum": 0,
            "totalError": 0
        })),
        "ok",
        "",
    )
}
