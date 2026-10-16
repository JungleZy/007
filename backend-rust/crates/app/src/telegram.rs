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

use crate::platform::{
    auth::AuthUser,
    envelope::{envelope_json, envelope_response},
    state::AppState,
};

#[derive(Debug, Deserialize)]
pub(crate) struct TrainIdRequest {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
}

pub(crate) const TRAIN_COLUMNS: &str = "id,title,start_time,pause_time,end_time,sustain_time,type,status,total_knock_number,total_number,error_number,accuracy,speed,rate_dot_min_ms,rate_dot_max_ms,rate_line_min_ms,rate_line_max_ms,rate_interval_min_ms,rate_interval_max_ms,big_interval_min_ms,big_interval_max_ms,create_user_id,create_time,now_floor_id,protocol_version,active_since,accumulated_active_millis";

pub(crate) fn train_json(row: &sqlx::mysql::MySqlRow) -> Value {
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
    let train = match sqlx::query(&format!(
        "SELECT {TRAIN_COLUMNS} FROM t_telegram_train WHERE id=? LIMIT 1"
    ))
    .bind(&train_id)
    .fetch_optional(pool)
    .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询该训练", ""),
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if train.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
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
        Some(json!({"train": train_value, "trainFloors": floor_values, "trainUser": null})),
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

#[derive(Debug, Deserialize)]
pub(crate) struct FloorPageRequest {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
    #[serde(rename = "pageNumber")]
    page_number: Option<i32>,
}

fn decimal_value(value: Option<rust_decimal::Decimal>) -> Value {
    value
        .and_then(|value| value.to_string().parse::<serde_json::Number>().ok())
        .map(Value::Number)
        .unwrap_or(Value::Null)
}

fn log_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "telegramTrainId": row.get::<Option<String>, _>("telegram_train_id"),
        "type": row.get::<Option<i32>, _>("type"),
        "value": row.get::<Option<String>, _>("value"),
        "creatTime": row.get::<Option<String>, _>("creat_time")
    })
}

fn setting_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "type": row.get::<Option<i32>, _>("type"),
        "key": row.get::<Option<String>, _>("key"),
        "value": row.get::<Option<String>, _>("value")
    })
}

fn statistical_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "type": row.get::<Option<i32>, _>("type"),
        "totalCount": row.get::<Option<i32>, _>("total_count"),
        "totalTime": row.get::<Option<String>, _>("total_time"),
        "avgSpeed": decimal_value(row.get::<Option<rust_decimal::Decimal>, _>("avg_speed")),
        "userId": row.get::<Option<String>, _>("user_id")
    })
}

// Classic WebSocket payloads use Gson, which omits null entity fields.
fn floor_content_ws_json(row: &sqlx::mysql::MySqlRow) -> Value {
    let mut content = serde_json::Map::new();
    for (field, column) in [
        ("id", "id"),
        ("floorId", "floor_id"),
        ("moresKey", "mores_key"),
        ("moresValue", "mores_value"),
        ("moresTime", "mores_time"),
    ] {
        if let Some(value) = row.get::<Option<String>, _>(column) {
            content.insert(field.to_owned(), Value::String(value));
        }
    }
    if let Some(sort) = row.get::<Option<i32>, _>("sort") {
        content.insert("sort".to_owned(), json!(sort));
    }
    Value::Object(content)
}

pub(crate) async fn floor_content_by_floor(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<FloorPageRequest>,
) -> Response {
    let Ok(pool) = pool_or_error(&state).await else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let floor = match sqlx::query("SELECT f.id,t.create_user_id FROM t_telegram_train_floor f JOIN t_telegram_train t ON t.id=f.train_id WHERE f.train_id=? AND f.sort=? LIMIT 1")
        .bind(body.train_id.as_deref()).bind(body.page_number).fetch_optional(pool).await {
        Ok(Some(row)) => row,
        Ok(None) => {
            let page = body.page_number.map(|page| page.to_string()).unwrap_or_else(|| "null".to_owned());
            let message = format!("未查询该训练的第 {page} 页报底！");
            return envelope_response(StatusCode::OK, 202, &message, &message);
        }
        Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    };
    if floor.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let floor_id = floor.get::<String, _>("id");
    let rows = match sqlx::query("SELECT id,floor_id,sort,mores_key,mores_value,mores_time FROM t_telegram_train_floor_content WHERE floor_id=? ORDER BY sort")
        .bind(&floor_id).fetch_all(pool).await {
        Ok(rows) => rows,
        Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    };
    let contents = rows.iter().map(floor_content_ws_json).collect::<Vec<_>>();
    let mut page = serde_json::Map::new();
    page.insert(floor_id, Value::Array(contents));
    super::ws::send_classic(&user.id, 1000, Some(Value::Object(page).to_string())).await;
    envelope_response(StatusCode::OK, 200, "ok", "")
}

pub(crate) async fn floor_content_by_floor_id_async(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(train_id) = body.get("trainId").and_then(Value::as_str) else {
        return envelope_response(
            StatusCode::OK,
            202,
            "id to load is required for loading",
            "id to load is required for loading",
        );
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let train = match sqlx::query("SELECT create_user_id FROM t_telegram_train WHERE id=? LIMIT 1")
        .bind(train_id)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => {
            return envelope_response(StatusCode::OK, 202, "未查询该训练！", "未查询该训练！")
        }
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if train.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let floors =
        match sqlx::query("SELECT id FROM t_telegram_train_floor WHERE train_id=? ORDER BY sort")
            .bind(train_id)
            .fetch_all(pool)
            .await
        {
            Ok(rows) => rows,
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        };
    for batch in floors.chunks(5) {
        let mut content_sql = String::from("SELECT id,floor_id,sort,mores_key,mores_value,mores_time FROM t_telegram_train_floor_content WHERE floor_id IN (?");
        for _ in 1..batch.len() {
            content_sql.push_str(",?");
        }
        content_sql.push(')');
        let mut query = sqlx::query(&content_sql);
        for floor in batch {
            query = query.bind(floor.get::<&str, _>("id"));
        }
        let rows = match query.fetch_all(pool).await {
            Ok(rows) => rows,
            Err(error) => {
                tracing::error!(%error, train_id, "classic floor-content batch query failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
        let mut contents = serde_json::Map::new();
        for row in &rows {
            contents
                .entry(row.get::<String, _>("floor_id"))
                .or_insert_with(|| Value::Array(Vec::new()))
                .as_array_mut()
                .expect("floor contents are an array")
                .push(floor_content_ws_json(row));
        }
        super::ws::send_classic(&user.id, 1000, Some(Value::Object(contents).to_string())).await;
    }
    super::ws::send_classic(&user.id, 1001, None).await;
    envelope_response(StatusCode::OK, 200, "ok", "")
}

pub(crate) async fn get_telegram_train_log(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(train_id) = body
        .get("id")
        .or_else(|| body.get("trainId"))
        .and_then(Value::as_str)
        .filter(|id| !id.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Ok(pool) = pool_or_error(&state).await else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let train = match sqlx::query("SELECT create_user_id FROM t_telegram_train WHERE id=? LIMIT 1")
        .bind(train_id)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询该训练", ""),
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if train.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    match sqlx::query("SELECT id,telegram_train_id,type,value,creat_time FROM t_telegram_train_log WHERE telegram_train_id=? ORDER BY creat_time ASC")
        .bind(train_id).fetch_all(pool).await {
        Ok(rows) => envelope_json(StatusCode::OK, 200, Some(Value::Array(rows.iter().map(log_json).collect())), "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn get_setting(State(state): State<AppState>) -> Response {
    let Ok(pool) = pool_or_error(&state).await else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,`key`,value FROM t_telegram_train_setting ORDER BY id")
        .fetch_all(pool)
        .await
    {
        Ok(rows) => envelope_json(
            StatusCode::OK,
            200,
            Some(Value::Array(rows.iter().map(setting_json).collect())),
            "ok",
            "",
        ),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn statistical_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Ok(pool) = pool_or_error(&state).await else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    match crate::statistical::lock_user(&mut tx, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
    let rows = match sqlx::query("SELECT id,type,total_count,total_time,avg_speed,user_id FROM t_telegram_train_statistical WHERE user_id=? ORDER BY type")
        .bind(&user.id).fetch_all(&mut *tx).await {
        Ok(rows) => rows,
        Err(_) => return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    };
    let mut result = rows.iter().map(statistical_json).collect::<Vec<_>>();
    for kind in 0..=2 {
        if result
            .iter()
            .any(|row| row.get("type").and_then(Value::as_i64) == Some(kind))
        {
            continue;
        }
        let id = uuid::Uuid::new_v4().to_string();
        if sqlx::query("INSERT INTO t_telegram_train_statistical (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,?,0,'0',0,?)")
            .bind(&id).bind(kind as i32).bind(&user.id).execute(&mut *tx).await.is_err() {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
        result.push(json!({"id":id,"type":kind,"totalCount":0,"totalTime":"0","avgSpeed":0,"userId":user.id.clone()}));
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    result.sort_by_key(|row| row.get("type").and_then(Value::as_i64).unwrap_or(i64::MAX));
    envelope_json(StatusCode::OK, 200, Some(Value::Array(result)), "ok", "")
}

pub(crate) async fn last_train(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let train_type = body
        .get("type")
        .and_then(Value::as_i64)
        .map(|value| value as i32);
    let Ok(pool) = pool_or_error(&state).await else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let row = match train_type {
        Some(kind) => sqlx::query(&format!("SELECT {TRAIN_COLUMNS} FROM t_telegram_train WHERE create_user_id=? AND type=? ORDER BY create_time DESC LIMIT 1"))
            .bind(&user.id).bind(kind).fetch_optional(pool).await,
        None => sqlx::query(&format!("SELECT {TRAIN_COLUMNS} FROM t_telegram_train WHERE create_user_id=? AND type IS NULL ORDER BY create_time DESC LIMIT 1"))
            .bind(&user.id).fetch_optional(pool).await,
    };
    match row {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(train_json(&row)), "ok", ""),
        Ok(None) => envelope_json(StatusCode::OK, 200, Some(Value::Null), "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::{
        platform::auth::{authenticate, require_admin},
        telegram_write,
    };
    use axum::{middleware, routing::post, Router};

    let mut router = Router::new();
    let read = Router::new()
        .route("/getAll", post(list))
        .route("/getById", post(by_id))
        .route("/getFloorContentByFloorId", post(floor_content_by_floor_id))
        .route(
            "/getFloorContentByFloorIdAsync",
            post(floor_content_by_floor_id_async),
        )
        .route("/getFloorContentByFloor", post(floor_content_by_floor))
        .route("/getTelegramTrainLog", post(get_telegram_train_log))
        .route("/getSetting", post(get_setting))
        .route("/statisticalPage", post(statistical_page))
        .route("/lastTrain", post(last_train))
        .route("/saveTelegramTrain", post(telegram_write::save))
        .route(
            "/saveFloorContent",
            post(telegram_write::save_floor_content),
        )
        .route("/saveBaseTrain", post(telegram_write::save_base_train))
        .route("/startTelegramTrain", post(telegram_write::start))
        .route("/pauseTelegramTrain", post(telegram_write::pause))
        .route("/endTelegramTrain", post(telegram_write::end))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin = Router::new()
        .route("/saveSetting", post(telegram_write::save_setting))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    router = router.nest("/api/telegramTrain", read.merge(admin));
    router
}
