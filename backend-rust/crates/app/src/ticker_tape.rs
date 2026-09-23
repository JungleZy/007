use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rust_decimal::Decimal;
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, Row};

use super::{envelope_json, envelope_response, AppState, AuthUser};

#[derive(Debug, Deserialize)]
pub(crate) struct PageRequest {
    page: Option<i64>,
    rows: Option<i64>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct IdRequest {
    id: Option<String>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct TypeRequest {
    #[serde(rename = "type")]
    train_type: Option<i32>,
}

const TRAIN_COLUMNS: &str = "id,name,rate,type,code_message_body,code_short,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_time_text,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') AS start_time_text,DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s') AS end_time_text,valid_time,status,user_id,mark,schedule,is_low_rate";

fn parse_message_body(raw: Option<String>) -> Result<Value, serde_json::Error> {
    match raw {
        Some(value) => serde_json::from_str(&value),
        None => Ok(Value::Null),
    }
}

fn train_json(row: &MySqlRow) -> Result<Value, serde_json::Error> {
    Ok(json!({
        "id": row.get::<String, _>("id"),
        "name": row.get::<Option<String>, _>("name"),
        "trainId": null,
        "type": row.get::<Option<i32>, _>("type"),
        "codeShort": row.get::<Option<i32>, _>("code_short"),
        "createTime": row.get::<Option<String>, _>("create_time_text"),
        "startTime": row.get::<Option<String>, _>("start_time_text"),
        "endTime": row.get::<Option<String>, _>("end_time_text"),
        "validTime": row.get::<Option<String>, _>("valid_time"),
        "status": row.get::<Option<i32>, _>("status"),
        "rate": row.get::<Option<i32>, _>("rate"),
        "codeMessageBody": parse_message_body(row.get::<Option<String>, _>("code_message_body"))?,
        "mark": row.get::<Option<String>, _>("mark"),
        "schedule": row.get::<Option<i32>, _>("schedule"),
        "isLowRate": row.get::<Option<i32>, _>("is_low_rate")
    }))
}

fn empty_train() -> Value {
    json!({
        "id": null,
        "name": null,
        "trainId": null,
        "type": null,
        "codeShort": null,
        "createTime": null,
        "startTime": null,
        "endTime": null,
        "validTime": null,
        "status": null,
        "rate": null,
        "codeMessageBody": null,
        "mark": null,
        "schedule": null,
        "isLowRate": null
    })
}

pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<PageRequest>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let page = body.page.unwrap_or(1).max(1);
    let rows = body.rows.unwrap_or(20).clamp(1, 200);
    let total =
        match sqlx::query("SELECT COUNT(*) AS total FROM t_ticker_tape_train WHERE user_id=?")
            .bind(&user.id)
            .fetch_one(pool)
            .await
        {
            Ok(row) => row.get::<i64, _>("total"),
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        };
    let sql = format!(
        "SELECT {TRAIN_COLUMNS} FROM t_ticker_tape_train WHERE user_id=? ORDER BY create_time DESC LIMIT ? OFFSET ?"
    );
    let train_rows = match sqlx::query(&sql)
        .bind(&user.id)
        .bind(rows)
        .bind((page - 1) * rows)
        .fetch_all(pool)
        .await
    {
        Ok(rows) => rows,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let data = match train_rows
        .iter()
        .map(train_json)
        .collect::<Result<Vec<_>, _>>()
    {
        Ok(data) => data,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let total_page = if total == 0 {
        0
    } else {
        (total + rows - 1) / rows
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({
            "totalPage": total_page,
            "totalNumber": total,
            "currentPage": page,
            "pageSize": rows,
            "data": data
        })),
        "ok",
        "",
    )
}

pub(crate) async fn by_id(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<IdRequest>,
) -> Response {
    let Some(id) = body.id.filter(|id| !id.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = format!("SELECT {TRAIN_COLUMNS} FROM t_ticker_tape_train WHERE id=? LIMIT 1");
    let row = match sqlx::query(&sql).bind(&id).fetch_optional(pool).await {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "请求参数错误", ""),
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if row.get::<Option<String>, _>("user_id").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    match train_json(&row) {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn last_train(
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
    let sql = format!(
        "SELECT {TRAIN_COLUMNS} FROM t_ticker_tape_train WHERE user_id=? AND type=? ORDER BY create_time DESC LIMIT 1"
    );
    match sqlx::query(&sql)
        .bind(&user.id)
        .bind(train_type)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => match train_json(&row) {
            Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
            Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
        },
        Ok(None) => envelope_json(StatusCode::OK, 200, Some(empty_train()), "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn statistics(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let rows = match sqlx::query("SELECT id,type,total_count,total_time,avg_speed,user_id FROM t_ticker_tape_train_statistical WHERE user_id=? ORDER BY type")
        .bind(&user.id)
        .fetch_all(pool)
        .await
    {
        Ok(rows) => rows,
        Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    };
    let mut by_type = std::collections::HashMap::new();
    for row in rows {
        let train_type = row.get::<Option<i32>, _>("type").unwrap_or(0);
        by_type.insert(
            train_type,
            json!({
                "id": row.get::<String, _>("id"),
                "type": train_type,
                "totalCount": row.get::<Option<i32>, _>("total_count").unwrap_or(0),
                "totalTime": row.get::<Option<String>, _>("total_time").unwrap_or_else(|| "0".to_owned()),
                "avgSpeed": row.get::<Option<Decimal>, _>("avg_speed").unwrap_or(Decimal::ZERO),
                "userId": row.get::<Option<String>, _>("user_id")
            }),
        );
    }
    let data = (0..3)
        .map(|train_type| {
            by_type.remove(&train_type).unwrap_or_else(|| {
                json!({
                    "id": null,
                    "type": train_type,
                    "totalCount": 0,
                    "totalTime": "0",
                    "avgSpeed": Decimal::ZERO,
                    "userId": user.id
                })
            })
        })
        .collect::<Vec<_>>();
    envelope_json(StatusCode::OK, 200, Some(Value::Array(data)), "ok", "")
}

pub(crate) async fn settings(State(state): State<AppState>) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,rate,text,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS create_time_text FROM t_ticker_tape_train_setting")
        .fetch_all(pool)
        .await
    {
        Ok(rows) => envelope_json(
            StatusCode::OK,
            200,
            Some(json!(rows.iter().map(|row| json!({
                "id": row.get::<String, _>("id"),
                "type": row.get::<Option<String>, _>("type"),
                "rate": row.get::<Option<i32>, _>("rate"),
                "text": row.get::<Option<String>, _>("text"),
                "createTime": row.get::<Option<String>, _>("create_time_text")
            })).collect::<Vec<_>>())),
            "ok",
            "",
        ),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
