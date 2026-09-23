use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Path, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rust_decimal::Decimal;
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::{MySql, Row, Transaction};

#[derive(Debug, Deserialize)]
pub(crate) struct TypeRequest {
    #[serde(rename = "type")]
    train_type: Option<i32>,
}

#[derive(Debug, Deserialize)]
pub(crate) struct BaseSaveRequest {
    count: Option<i32>,
    mistake: Option<i32>,
    #[serde(rename = "type")]
    train_type: Option<i32>,
    duration: Option<Value>,
}

fn database_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "telex database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

fn duration(value: Option<&Value>) -> Option<i64> {
    match value? {
        Value::Number(number) => number.as_i64(),
        Value::String(value) => value.parse().ok(),
        _ => None,
    }
}

fn base_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "userId": row.get::<Option<String>, _>("user_id"),
        "count": row.get::<Option<i32>, _>("count"),
        "mistake": row.get::<Option<i32>, _>("mistake"),
        "duration": row.get::<Option<i64>, _>("duration"),
        "type": row.get::<Option<i32>, _>("type")
    })
}

async fn lock_user(
    transaction: &mut Transaction<'_, MySql>,
    user_id: &str,
) -> Result<bool, sqlx::Error> {
    sqlx::query("SELECT id FROM t_user WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(user_id)
        .fetch_optional(&mut **transaction)
        .await
        .map(|row| row.is_some())
}

pub(crate) async fn find_base(
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
    match sqlx::query("SELECT id,user_id,count,mistake,duration,type FROM t_telex_pat WHERE user_id=? AND type=? LIMIT 1")
        .bind(&user.id)
        .bind(train_type)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(base_json(&row)), "ok", ""),
        Ok(None) => envelope_json(StatusCode::OK, 200, None, "ok", ""),
        Err(error) => database_error(error, "find base training"),
    }
}

pub(crate) async fn save_base(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<BaseSaveRequest>,
) -> Response {
    let (Some(count), Some(mistake), Some(train_type), Some(duration)) = (
        body.count,
        body.mistake,
        body.train_type,
        duration(body.duration.as_ref()),
    ) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start base save transaction"),
    };
    match lock_user(&mut transaction, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(error) => return database_error(error, "lock user for base save"),
    }
    let existing = match sqlx::query(
        "SELECT id FROM t_telex_pat WHERE user_id=? AND type=? LIMIT 1 FOR UPDATE",
    )
    .bind(&user.id)
    .bind(train_type)
    .fetch_optional(&mut *transaction)
    .await
    {
        Ok(row) => row,
        Err(error) => return database_error(error, "load base training for save"),
    };
    let id = if let Some(row) = existing {
        let id = row.get::<String, _>("id");
        if let Err(error) =
            sqlx::query("UPDATE t_telex_pat SET count=?,mistake=?,duration=? WHERE id=?")
                .bind(count)
                .bind(mistake)
                .bind(duration)
                .bind(&id)
                .execute(&mut *transaction)
                .await
        {
            return database_error(error, "update base training");
        }
        id
    } else {
        let id = uuid::Uuid::new_v4().to_string();
        if let Err(error) = sqlx::query(
            "INSERT INTO t_telex_pat (id,user_id,count,mistake,duration,type) VALUES (?,?,?,?,?,?)",
        )
        .bind(&id)
        .bind(&user.id)
        .bind(count)
        .bind(mistake)
        .bind(duration)
        .bind(train_type)
        .execute(&mut *transaction)
        .await
        {
            return database_error(error, "insert base training");
        }
        id
    };

    let total_time = match sqlx::query("SELECT CAST(COALESCE(SUM(duration),0) AS SIGNED) AS total_time FROM t_telex_pat WHERE user_id=? AND type=0")
        .bind(&user.id)
        .fetch_one(&mut *transaction)
        .await
    {
        Ok(row) => row.get::<i64, _>("total_time"),
        Err(error) => return database_error(error, "sum base duration"),
    };
    let statistical = match sqlx::query("SELECT id,total_count FROM t_telex_pat_train_statistical WHERE user_id=? AND type=0 LIMIT 1 FOR UPDATE")
        .bind(&user.id)
        .fetch_optional(&mut *transaction)
        .await
    {
        Ok(row) => row,
        Err(error) => return database_error(error, "load base statistics"),
    };
    let statistical_result = if let Some(row) = statistical {
        sqlx::query(
            "UPDATE t_telex_pat_train_statistical SET total_time=?,total_count=? WHERE id=?",
        )
        .bind(total_time.to_string())
        .bind(row.get::<Option<i32>, _>("total_count").unwrap_or(0) + 1)
        .bind(row.get::<String, _>("id"))
        .execute(&mut *transaction)
        .await
    } else {
        sqlx::query("INSERT INTO t_telex_pat_train_statistical (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,0,1,?,NULL,?)")
            .bind(uuid::Uuid::new_v4().to_string())
            .bind(total_time.to_string())
            .bind(&user.id)
            .execute(&mut *transaction)
            .await
    };
    if let Err(error) = statistical_result {
        return database_error(error, "save base statistics");
    }
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit base save transaction");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({
            "id": id,
            "userId": user.id,
            "count": count,
            "mistake": mistake,
            "duration": duration,
            "type": train_type
        })),
        "ok",
        "",
    )
}

pub(crate) async fn delete_base(
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
        Err(error) => return database_error(error, "start base delete transaction"),
    };
    match lock_user(&mut transaction, &user.id).await {
        Ok(true) => {}
        Ok(false) => return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(error) => return database_error(error, "lock user for base delete"),
    }
    if let Err(error) = sqlx::query("DELETE FROM t_telex_pat WHERE user_id=? AND type=?")
        .bind(&user.id)
        .bind(train_type)
        .execute(&mut *transaction)
        .await
    {
        return database_error(error, "delete base training");
    }
    if let Err(error) = sqlx::query("UPDATE t_telex_pat_train_statistical SET total_time='0',total_count=0,avg_speed=0 WHERE user_id=? AND type=?")
        .bind(&user.id)
        .bind(train_type)
        .execute(&mut *transaction)
        .await
    {
        return database_error(error, "clear base statistics");
    }
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit base delete transaction");
    }
    envelope_response(StatusCode::OK, 200, "ok", "")
}

pub(crate) async fn statistical_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let rows = match sqlx::query("SELECT id,type,total_count,total_time,avg_speed,user_id FROM t_telex_pat_train_statistical WHERE user_id=? ORDER BY type")
        .bind(&user.id)
        .fetch_all(pool)
        .await
    {
        Ok(rows) => rows,
        Err(error) => return database_error(error, "list statistics"),
    };
    let mut by_type = std::collections::HashMap::new();
    for row in rows {
        let kind = row.get::<Option<i32>, _>("type").unwrap_or(0);
        by_type.insert(
            kind,
            json!({
                "id": row.get::<String, _>("id"),
                "type": kind,
                "totalCount": row.get::<Option<i32>, _>("total_count").unwrap_or(0),
                "totalTime": row.get::<Option<String>, _>("total_time").unwrap_or_else(|| "0".to_owned()),
                "avgSpeed": row.get::<Option<Decimal>, _>("avg_speed").map(|value| value.to_string()).unwrap_or_else(|| "0".to_owned()),
                "userId": row.get::<Option<String>, _>("user_id")
            }),
        );
    }
    let result = (0..4)
        .map(|kind| {
            by_type.remove(&kind).unwrap_or_else(|| {
                json!({
                    "id": null,
                    "type": kind,
                    "totalCount": 0,
                    "totalTime": "0",
                    "avgSpeed": "0",
                    "userId": user.id
                })
            })
        })
        .collect::<Vec<_>>();
    envelope_json(StatusCode::OK, 200, Some(Value::Array(result)), "ok", "")
}

pub(crate) async fn last_train(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TypeRequest>,
) -> Response {
    let Some(kind) = body.train_type else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,numbs,title,status,duration,total_number,error_number,accuracy,speed,content FROM t_telex_pat_train WHERE create_user_id=? AND type=? ORDER BY create_time DESC LIMIT 1")
        .bind(&user.id)
        .bind(kind)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(
            StatusCode::OK,
            200,
            Some(json!({
                "id": row.get::<String, _>("id"),
                "type": row.get::<Option<i32>, _>("type"),
                "numbs": row.get::<Option<i32>, _>("numbs"),
                "title": row.get::<Option<String>, _>("title"),
                "status": row.get::<Option<i32>, _>("status"),
                "duration": row.get::<Option<String>, _>("duration"),
                "totalNumber": row.get::<Option<i32>, _>("total_number"),
                "errorNumber": row.get::<Option<i32>, _>("error_number"),
                "accuracy": row.get::<Option<i32>, _>("accuracy"),
                "speed": row.get::<Option<String>, _>("speed"),
                "content": row.get::<Option<String>, _>("content")
            })),
            "ok",
            "",
        ),
        Ok(None) => envelope_json(StatusCode::OK, 200, None, "ok", ""),
        Err(error) => database_error(error, "find last train"),
    }
}
#[derive(Debug, Deserialize)]
pub(crate) struct TrainRequest {
    id: Option<String>,
    #[serde(rename = "type")]
    train_type: Option<i32>,
    status: Option<i32>,
    numbs: Option<i32>,
    title: Option<String>,
    duration: Option<String>,
    #[serde(rename = "totalNumber")]
    total_number: Option<i32>,
    #[serde(rename = "errorNumber")]
    error_number: Option<i32>,
    accuracy: Option<i32>,
    speed: Option<String>,
    content: Option<String>,
}
fn train_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error=%error,operation,"telex training write failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}
fn train_value(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id":row.get::<String,_>("id"),"type":row.get::<Option<i32>,_>("type"),"numbs":row.get::<Option<i32>,_>("numbs"),"title":row.get::<Option<String>,_>("title"),"status":row.get::<Option<i32>,_>("status"),"duration":row.get::<Option<String>,_>("duration"),"totalNumber":row.get::<Option<i32>,_>("total_number"),"errorNumber":row.get::<Option<i32>,_>("error_number"),"accuracy":row.get::<Option<i32>,_>("accuracy"),"speed":row.get::<Option<String>,_>("speed"),"content":row.get::<Option<String>,_>("content"),"protocolVersion":row.get::<Option<i32>,_>("protocol_version"),"activeSince":row.get::<Option<i64>,_>("active_since"),"accumulatedActiveMillis":row.get::<Option<i64>,_>("accumulated_active_millis")})
}
pub(crate) async fn train_list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql="SELECT id,type,numbs,title,status,duration,total_number,error_number,accuracy,speed,content,protocol_version,active_since,accumulated_active_millis FROM t_telex_pat_train WHERE create_user_id=? ORDER BY create_time DESC";
    match sqlx::query(sql).bind(&user.id).fetch_all(pool).await {
        Ok(rows) => envelope_json(
            StatusCode::OK,
            200,
            Some(Value::Array(rows.iter().map(train_value).collect())),
            "ok",
            "",
        ),
        Err(e) => train_error(e, "list train"),
    }
}
pub(crate) async fn train_detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TrainRequest>,
) -> Response {
    let Some(id) = body.id.filter(|v| !v.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,numbs,title,status,duration,total_number,error_number,accuracy,speed,content,create_user_id,protocol_version,active_since,accumulated_active_millis FROM t_telex_pat_train WHERE id=? LIMIT 1").bind(id).fetch_optional(pool).await{Ok(Some(row)) if row.get::<Option<String>,_>("create_user_id").as_deref()==Some(user.id.as_str())=>envelope_json(StatusCode::OK,200,Some(train_value(&row)),"ok",""),Ok(Some(_))=>envelope_response(StatusCode::OK,207,"无权限",""),Ok(None)=>envelope_response(StatusCode::OK,202,"未查询到训练",""),Err(e)=>train_error(e,"detail train")}
}
pub(crate) async fn train_save(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<TrainRequest>,
) -> Response {
    let Some(kind) = body.train_type else {
        return envelope_response(StatusCode::OK, 202, "训练类型无效", "");
    };
    if !(0..=2).contains(&kind) {
        return envelope_response(StatusCode::OK, 202, "训练类型无效", "");
    }
    let requested = body.status.unwrap_or(0);
    if !(0..=3).contains(&requested) {
        return envelope_response(StatusCode::OK, 202, "训练状态无效", "");
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = body
        .id
        .filter(|v| !v.is_empty())
        .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    let result=sqlx::query("INSERT INTO t_telex_pat_train (id,type,numbs,title,status,duration,total_number,error_number,accuracy,speed,content,create_user_id,protocol_version,accumulated_active_millis) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,1,0) ON DUPLICATE KEY UPDATE status=VALUES(status),numbs=VALUES(numbs),title=VALUES(title),duration=VALUES(duration),total_number=VALUES(total_number),error_number=VALUES(error_number),accuracy=VALUES(accuracy),speed=VALUES(speed),content=VALUES(content)").bind(&id).bind(kind).bind(body.numbs).bind(body.title).bind(requested).bind(body.duration).bind(body.total_number).bind(body.error_number).bind(body.accuracy).bind(body.speed).bind(body.content).bind(&user.id).execute(pool).await;
    match result {
        Ok(_) => envelope_json(StatusCode::OK, 200, Some(json!({"id":id})), "ok", ""),
        Err(e) => train_error(e, "save train"),
    }
}
pub(crate) async fn train_delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Path(id): Path<String>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("DELETE FROM t_telex_pat_train WHERE id=? AND create_user_id=?")
        .bind(id)
        .bind(&user.id)
        .execute(pool)
        .await
    {
        Ok(_) => envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", ""),
        Err(e) => train_error(e, "delete train"),
    }
}
