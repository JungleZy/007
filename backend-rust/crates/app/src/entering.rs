use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use rust_decimal::Decimal;
use serde_json::{json, Value};
use sqlx::Row;
use uuid::Uuid;

fn exercise_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id":row.get::<String,_>("id"),"type":row.get::<Option<i32>,_>("type"),"startTime":null,"endTime":null,"createTime":null,"accuracy":row.get::<Option<f64>,_>("accuracy"),"speed":row.get::<Option<i32>,_>("speed"),"content":row.get::<Option<String>,_>("content"),"duration":row.get::<Option<i32>,_>("duration"),"status":row.get::<Option<i32>,_>("status"),"name":row.get::<Option<String>,_>("name"),"correctNum":row.get::<Option<i32>,_>("correct_num"),"errorNum":row.get::<Option<i32>,_>("error_num"),"protocolVersion":row.get::<Option<i32>,_>("protocol_version")})
}
pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let kind = body.get("type").and_then(Value::as_i64).unwrap_or(0);
    match sqlx::query("SELECT id,type,accuracy,speed,content,duration,status,name,correct_num,error_num,protocol_version FROM t_entering_exercise WHERE create_user_id=? AND (type<=? OR type>?) ORDER BY create_time").bind(&user.id).bind(kind).bind(kind).fetch_all(pool).await { Ok(rows)=>envelope_json(StatusCode::OK,200,Some(json!(rows.iter().map(exercise_json).collect::<Vec<_>>())) ,"ok",""), Err(error)=>{tracing::warn!(error=%error,"entering list query failed");envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")} }
}
pub(crate) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(kind) = body.get("type").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "训练类型不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = Uuid::new_v4().to_string();
    match sqlx::query("INSERT INTO t_entering_exercise (id,type,create_user_id,status,speed,accuracy,duration,correct_num,error_num,protocol_version,content,source_content) VALUES (?,?,?,0,0,0,0,0,0,1,'[]','[]')").bind(&id).bind(kind).bind(&user.id).execute(pool).await{Ok(_)=>envelope_json(StatusCode::OK,200,Some(json!({"id":id,"type":kind,"status":0,"speed":0,"accuracy":0,"duration":0,"correctNum":0,"errorNum":0,"protocolVersion":1})),"ok",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
pub(crate) async fn by_id(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body.get("id").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,accuracy,speed,content,duration,status,name,correct_num,error_num,protocol_version FROM t_entering_exercise WHERE id=? AND create_user_id=? LIMIT 1")
        .bind(id)
        .bind(&user.id)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(exercise_json(&row)), "ok", ""),
        Ok(None) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn go_to(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    begin(State(state), Extension(user), Json(body)).await
}

pub(crate) async fn last_train(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(kind) = body.get("type").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,accuracy,speed,content,duration,status,name,correct_num,error_num,protocol_version FROM t_entering_exercise WHERE create_user_id=? AND type=? ORDER BY create_time DESC LIMIT 1")
        .bind(&user.id)
        .bind(kind)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(exercise_json(&row)), "ok", ""),
        Ok(None) => envelope_json(StatusCode::OK, 200, None, "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}

pub(crate) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body.get("id").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE t_entering_exercise SET status=1 WHERE id=? AND create_user_id=? AND status IN (0,3)").bind(id).bind(&user.id).execute(pool).await {Ok(r) if r.rows_affected()==1=>envelope_json(StatusCode::OK,200,None,"ok",""),Ok(_)=>envelope_response(StatusCode::OK,207,"无权限",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
pub(crate) async fn pause(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    transition(State(state), Extension(user), body, 3).await
}
pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    transition(State(state), Extension(user), body, 2).await
}
async fn transition(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    body: Value,
    status: i32,
) -> Response {
    let Some(id) = body.get("id").and_then(Value::as_str) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE t_entering_exercise SET status=?,content=? WHERE id=? AND create_user_id=? AND status IN (1,3)").bind(status).bind(body.get("content").and_then(Value::as_str)).bind(id).bind(&user.id).execute(pool).await {Ok(r) if r.rows_affected()==1=>envelope_json(StatusCode::OK,200,None,"ok",""),Ok(_)=>envelope_response(StatusCode::OK,207,"无权限或训练状态不可变更",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
pub(crate) async fn telex_find(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(kind) = body.get("type").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,create_user_id,type,total_time,total_num,total_error,message_name FROM t_entering_telex_pat WHERE create_user_id=? AND type=? LIMIT 1").bind(&user.id).bind(kind).fetch_optional(pool).await {Ok(Some(row))=>envelope_json(StatusCode::OK,200,Some(json!({"id":row.get::<String,_>("id"),"createUserId":row.get::<Option<String>,_>("create_user_id"),"type":row.get::<Option<i32>,_>("type"),"totalTime":row.get::<Option<i32>,_>("total_time"),"totalNum":row.get::<Option<i32>,_>("total_num"),"totalError":row.get::<Option<i32>,_>("total_error"),"messageName":row.get::<Option<String>,_>("message_name")})),"ok","") ,Ok(None)=>envelope_json(StatusCode::OK,200,Some(json!({"type":kind,"totalTime":0,"totalNum":0,"totalError":0})),"ok",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
pub(crate) async fn statistics(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    statistics_for(&state, &user, 0).await
}

pub(crate) async fn telex_statistics(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    statistics_for(&state, &user, 1).await
}

async fn statistics_for(state: &AppState, user: &AuthUser, kind: i32) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<Value, sqlx::Error> = async {
        let mut transaction = pool.begin().await?;
        // Lock the user even when no statistics exist yet, serializing lazy creation.
        sqlx::query("SELECT id FROM t_user WHERE id=? FOR UPDATE")
            .bind(&user.id)
            .fetch_one(&mut *transaction)
            .await?;
        let existing = sqlx::query("SELECT child_type FROM t_entering_statistical WHERE user_id=? AND type=? FOR UPDATE")
            .bind(&user.id)
            .bind(kind)
            .fetch_all(&mut *transaction)
            .await?;
        let child_types = existing.iter()
            .map(|row| row.try_get::<i32, _>("child_type"))
            .collect::<Result<Vec<_>, _>>()?;
        for child_type in 0..if kind == 0 { 3 } else { 4 } {
            if !child_types.contains(&child_type) {
                sqlx::query("INSERT INTO t_entering_statistical (id,type,child_type,total_count,total_time,avg_speed,user_id) VALUES (?,?,?,0,'0',0,?)")
                    .bind(Uuid::new_v4().to_string())
                    .bind(kind)
                    .bind(child_type)
                    .bind(&user.id)
                    .execute(&mut *transaction)
                    .await?;
            }
        }
        let rows = sqlx::query("SELECT id,child_type,total_count,total_time,avg_speed,user_id FROM t_entering_statistical WHERE user_id=? AND type=? ORDER BY child_type FOR UPDATE")
            .bind(&user.id)
            .bind(kind)
            .fetch_all(&mut *transaction)
            .await?;
        let mut values = Vec::with_capacity(rows.len());
        for row in rows {
            let avg_speed = row.try_get::<Option<Decimal>, _>("avg_speed")?
                .map(|value| value.to_string().parse::<serde_json::Number>())
                .transpose()
                .map_err(|error| sqlx::Error::Decode(Box::new(error)))?;
            values.push(json!({
                "id": row.try_get::<String, _>("id")?,
                "type": row.try_get::<i32, _>("child_type")?,
                "totalCount": row.try_get::<Option<i32>, _>("total_count")?,
                "totalTime": row.try_get::<Option<String>, _>("total_time")?,
                "avgSpeed": avg_speed,
                "userId": row.try_get::<Option<String>, _>("user_id")?,
            }));
        }
        transaction.commit().await?;
        Ok(Value::Array(values))
    }.await;
    match result {
        Ok(value) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Err(error) => {
            tracing::error!(%error, kind, "entering statistics failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}
pub(crate) async fn keypoints(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(kind) = body.get("type").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,content FROM t_entering_key_points WHERE type=? LIMIT 1")
        .bind(kind)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(
            StatusCode::OK,
            200,
            Some(
                json!({"id":row.get::<String,_>("id"),"type":row.get::<Option<i32>,_>("type"),"content":row.get::<Option<String>,_>("content")}),
            ),
            "ok",
            "",
        ),
        Ok(None) => envelope_json(StatusCode::OK, 200, None, "ok", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
pub(crate) async fn telex_save(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(kind) = body.get("type").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = sqlx::query(
        "SELECT id FROM t_entering_telex_pat WHERE create_user_id=? AND type=? LIMIT 1",
    )
    .bind(&user.id)
    .bind(kind)
    .fetch_optional(pool)
    .await
    .ok()
    .flatten()
    .map(|r| r.get::<String, _>("id"))
    .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
    match sqlx::query("INSERT INTO t_entering_telex_pat (id,create_user_id,type,total_time,total_num,total_error,message_name) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE total_time=VALUES(total_time),total_num=VALUES(total_num),total_error=VALUES(total_error),message_name=VALUES(message_name)").bind(&id).bind(&user.id).bind(kind).bind(body.get("totalTime").and_then(Value::as_i64)).bind(body.get("totalNum").and_then(Value::as_i64)).bind(body.get("totalError").and_then(Value::as_i64)).bind(body.get("messageName").and_then(Value::as_str)).execute(pool).await{Ok(_)=>envelope_json(StatusCode::OK,200,Some(json!({"id":id,"type":kind})),"ok",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
pub(crate) async fn telex_clear(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(kind) = body.get("type").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE t_entering_telex_pat SET total_time=0,total_num=0,total_error=0 WHERE create_user_id=? AND type=?").bind(&user.id).bind(kind).execute(pool).await{Ok(_)=>telex_find(State(state),Extension(user),Json(json!({"type":kind}))).await,Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}

fn request_integer(value: Option<&Value>) -> Result<Option<i32>, Response> {
    match value {
        None | Some(Value::Null) => Ok(None),
        Some(Value::Number(value)) => {
            let integer = value
                .as_i64()
                .and_then(|value| i32::try_from(value).ok())
                .or_else(|| {
                    value
                        .as_f64()
                        .filter(|value| *value >= i32::MIN as f64 && *value <= i32::MAX as f64)
                        .map(|value| value as i32)
                });
            integer
                .map(Some)
                .ok_or_else(|| StatusCode::BAD_REQUEST.into_response())
        }
        Some(Value::String(value)) => {
            let value = value.trim();
            if value.is_empty() || value == "null" {
                Ok(None)
            } else {
                value
                    .parse()
                    .map(Some)
                    .map_err(|_| StatusCode::BAD_REQUEST.into_response())
            }
        }
        Some(_) => Err(StatusCode::BAD_REQUEST.into_response()),
    }
}

pub(crate) async fn get_word_stock(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    if !body.is_object() && !body.is_null() {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let kind = match request_integer(body.get("type")) {
        Ok(Some(9)) => 2,
        Ok(Some(kind)) => kind,
        // Java compares the nullable DTO type before querying the stock table.
        Ok(None) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
        Err(response) => return response,
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<Value, sqlx::Error> = async {
        let row = sqlx::query(
            "SELECT id,type,content FROM t_entering_exercise_word_stock WHERE type=? LIMIT 1",
        )
        .bind(kind)
        .fetch_optional(pool)
        .await?;
        match row {
            Some(row) => Ok(json!({
                "id": row.try_get::<i32, _>("id")?,
                "type": row.try_get::<Option<i32>, _>("type")?,
                "content": row.try_get::<Option<String>, _>("content")?,
            })),
            None => Ok(json!({"id": null, "type": null, "content": null})),
        }
    }
    .await;
    match result {
        Ok(value) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Err(error) => {
            tracing::error!(%error, "entering word stock query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

fn request_string(value: Option<&Value>) -> Result<Option<std::borrow::Cow<'_, str>>, Response> {
    match value {
        None | Some(Value::Null) => Ok(None),
        Some(Value::String(value)) => Ok(Some(std::borrow::Cow::Borrowed(value.as_str()))),
        Some(Value::Number(value)) => Ok(Some(std::borrow::Cow::Owned(value.to_string()))),
        Some(Value::Bool(value)) => Ok(Some(std::borrow::Cow::Borrowed(if *value {
            "true"
        } else {
            "false"
        }))),
        Some(_) => Err(StatusCode::BAD_REQUEST.into_response()),
    }
}

pub(crate) async fn save_key_points(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    if body.is_null() {
        return envelope_response(
            StatusCode::OK,
            202,
            "Entity must not be null.",
            "Entity must not be null.",
        );
    }
    if !body.is_object() {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let kind = match request_integer(body.get("type")) {
        Ok(kind) => kind,
        Err(response) => return response,
    };
    let id = match request_string(body.get("id")) {
        Ok(id) => id,
        Err(response) => return response,
    };
    let content = match request_string(body.get("content")) {
        Ok(content) => content,
        Err(response) => return response,
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<Value, sqlx::Error> = async {
        let mut transaction = pool.begin().await?;
        let id = match id {
            Some(id) => {
                // Generated identifiers merge only existing entities; an unknown ID is not an insert.
                sqlx::query("SELECT id FROM t_entering_key_points WHERE id=? FOR UPDATE")
                    .bind(id.as_ref())
                    .fetch_one(&mut *transaction)
                    .await?;
                sqlx::query("UPDATE t_entering_key_points SET type=?,content=? WHERE id=?")
                    .bind(kind)
                    .bind(content.as_deref())
                    .bind(id.as_ref())
                    .execute(&mut *transaction)
                    .await?;
                id
            }
            None => {
                let id = Uuid::new_v4().to_string();
                sqlx::query("INSERT INTO t_entering_key_points (id,type,content) VALUES (?,?,?)")
                    .bind(&id)
                    .bind(kind)
                    .bind(content.as_deref())
                    .execute(&mut *transaction)
                    .await?;
                std::borrow::Cow::Owned(id)
            }
        };
        transaction.commit().await?;
        Ok(json!({"id": id, "type": kind, "content": content}))
    }
    .await;
    match result {
        Ok(value) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Err(error) => {
            tracing::error!(%error, "entering key points save failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}
