use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::{MySqlPool, Row};
use std::collections::HashSet;

use super::{envelope_json, envelope_response, AppState, AuthUser};

#[derive(Debug, Deserialize)]
pub(crate) struct TrainIdRequest {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
}

const TRAIN_COLUMNS: &str = "id,title,start_time,pause_time,end_time,sustain_time,type,status,total_knock_number,total_number,error_number,accuracy,speed,rate_dot_min_ms,rate_dot_max_ms,rate_line_min_ms,rate_line_max_ms,rate_interval_min_ms,rate_interval_max_ms,big_interval_min_ms,big_interval_max_ms,create_user_id,create_time,now_floor_id,protocol_version,active_since,accumulated_active_millis";

fn train_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "title": row.get::<Option<String>, _>("title"),
        "startTime": row.get::<Option<String>, _>("start_time"),
        "pauseTime": row.get::<Option<String>, _>("pause_time"),
        "endTime": row.get::<Option<String>, _>("end_time"),
        "sustainTime": row.get::<Option<String>, _>("sustain_time"),
        "type": row.get::<Option<i32>, _>("type"),
        "status": row.get::<Option<i32>, _>("status"),
        "totalKnockNumber": row.get::<Option<i32>, _>("total_knock_number"),
        "totalNumber": row.get::<Option<i32>, _>("total_number"),
        "errorNumber": row.get::<Option<i32>, _>("error_number"),
        "accuracy": row.get::<Option<String>, _>("accuracy"),
        "speed": row.get::<Option<String>, _>("speed"),
        "rateDotMinMs": row.get::<Option<i32>, _>("rate_dot_min_ms"),
        "rateDotMaxMs": row.get::<Option<i32>, _>("rate_dot_max_ms"),
        "rateLineMinMs": row.get::<Option<i32>, _>("rate_line_min_ms"),
        "rateLineMaxMs": row.get::<Option<i32>, _>("rate_line_max_ms"),
        "rateIntervalMinMs": row.get::<Option<i32>, _>("rate_interval_min_ms"),
        "rateIntervalMaxMs": row.get::<Option<i32>, _>("rate_interval_max_ms"),
        "bigIntervalMinMs": row.get::<Option<i32>, _>("big_interval_min_ms"),
        "bigIntervalMaxMs": row.get::<Option<i32>, _>("big_interval_max_ms"),
        "createUserId": row.get::<Option<String>, _>("create_user_id"),
        "createTime": row.get::<Option<String>, _>("create_time"),
        "nowFloorId": row.get::<Option<String>, _>("now_floor_id"),
        "protocolVersion": row.get::<Option<i32>, _>("protocol_version"),
        "activeSince": row.get::<Option<i64>, _>("active_since"),
        "accumulatedActiveMillis": row.get::<Option<i64>, _>("accumulated_active_millis")
    })
}

fn floor_json(row: &sqlx::mysql::MySqlRow, contents: Vec<Value>) -> Value {
    json!({
        "floor": {
            "id": row.get::<String, _>("id"),
            "trainId": row.get::<Option<String>, _>("train_id"),
            "sort": row.get::<Option<i32>, _>("sort"),
            "type": row.get::<Option<i32>, _>("type"),
            "numberType": row.get::<Option<i32>, _>("number_type"),
            "contentNumber": row.get::<Option<i32>, _>("content_number")
        },
        "floorContents": contents
    })
}

fn content_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "floorId": row.get::<Option<String>, _>("floor_id"),
        "sort": row.get::<Option<i32>, _>("sort"),
        "moresKey": row.get::<Option<String>, _>("mores_key"),
        "moresValue": row.get::<Option<String>, _>("mores_value"),
        "moresTime": row.get::<Option<String>, _>("mores_time")
    })
}

async fn pool_or_error(state: &AppState) -> Result<&MySqlPool, Response> {
    state
        .pool
        .as_ref()
        .ok_or_else(|| envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""))
}

pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Ok(pool) = pool_or_error(&state).await else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = format!("SELECT {TRAIN_COLUMNS} FROM t_telegram_train WHERE create_user_id=? ORDER BY create_time DESC");
    match sqlx::query(&sql).bind(&user.id).fetch_all(pool).await {
        Ok(rows) => envelope_json(
            StatusCode::OK,
            200,
            Some(Value::Array(rows.iter().map(train_json).collect())),
            "ok",
            "",
        ),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn by_id(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TrainIdRequest>,
) -> Response {
    let Some(train_id) = body.train_id.filter(|id| !id.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Ok(pool) = pool_or_error(&state).await else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = format!(
        "SELECT {TRAIN_COLUMNS} FROM t_telegram_train WHERE id=? AND create_user_id=? LIMIT 1"
    );
    let Some(train) = (match sqlx::query(&sql)
        .bind(&train_id)
        .bind(&user.id)
        .fetch_optional(pool)
        .await
    {
        Ok(row) => row,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }) else {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    };
    let train_value = train_json(&train);
    let floors = match sqlx::query("SELECT id,train_id,sort,type,number_type,content_number FROM t_telegram_train_floor WHERE train_id=? ORDER BY sort")
        .bind(&train_id).fetch_all(pool).await {
        Ok(rows) => rows,
        Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    };
    let selected_floor = train
        .get::<Option<String>, _>("now_floor_id")
        .or_else(|| floors.first().map(|row| row.get::<String, _>("id")));
    let mut floor_values = Vec::with_capacity(floors.len());
    for floor in &floors {
        let floor_id = floor.get::<String, _>("id");
        let contents = if selected_floor.as_deref() == Some(floor_id.as_str()) {
            match sqlx::query("SELECT id,floor_id,sort,mores_key,mores_value,mores_time FROM t_telegram_train_floor_content WHERE floor_id=? ORDER BY sort")
                .bind(&floor_id).fetch_all(pool).await {
                Ok(rows) => rows.iter().map(content_json).collect(),
                Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
            }
        } else {
            Vec::new()
        };
        floor_values.push(floor_json(floor, contents));
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"train": train_value, "trainFloors": floor_values})),
        "ok",
        "",
    )
}
pub(crate) async fn floor_content_by_floor_id(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(ids): Json<Vec<String>>,
) -> Response {
    if ids.is_empty() || ids.iter().any(|id| id.is_empty()) {
        return envelope_response(StatusCode::OK, 202, "报底不能为空", "");
    }
    let Ok(pool) = pool_or_error(&state).await else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };

    // Resolve every requested floor and its train owner before reading content.
    // This mirrors requireFloorOwner(List): a missing or foreign floor rejects the
    // whole request rather than returning a partial map.
    let placeholders = std::iter::repeat("?")
        .take(ids.len())
        .collect::<Vec<_>>()
        .join(",");
    let floor_sql = format!(
        "SELECT f.id,t.create_user_id FROM t_telegram_train_floor f LEFT JOIN t_telegram_train t ON t.id=f.train_id WHERE f.id IN ({placeholders})"
    );
    let mut floor_query = sqlx::query(&floor_sql);
    for id in &ids {
        floor_query = floor_query.bind(id);
    }
    let floors = match floor_query.fetch_all(pool).await {
        Ok(rows) => rows,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let requested_ids: HashSet<&str> = ids.iter().map(String::as_str).collect();
    if floors.len() != requested_ids.len() {
        return envelope_response(StatusCode::OK, 202, "未查询报底", "");
    }
    if floors.iter().any(|row| {
        row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str())
    }) {
        return envelope_response(StatusCode::OK, 207, "无权访问他人训练", "");
    }

    let content_sql = format!(
        "SELECT id,floor_id,sort,mores_key,mores_value,mores_time FROM t_telegram_train_floor_content WHERE floor_id IN ({placeholders}) ORDER BY sort"
    );
    let mut content_query = sqlx::query(&content_sql);
    for id in &ids {
        content_query = content_query.bind(id);
    }
    let rows = match content_query.fetch_all(pool).await {
        Ok(rows) => rows,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let mut result = serde_json::Map::new();
    for row in &rows {
        if let Some(floor_id) = row.get::<Option<String>, _>("floor_id") {
            result
                .entry(floor_id)
                .or_insert_with(|| Value::Array(Vec::new()))
                .as_array_mut()
                .expect("map value is an array")
                .push(content_json(row));
        }
    }
    envelope_json(StatusCode::OK, 200, Some(Value::Object(result)), "ok", "")
}
