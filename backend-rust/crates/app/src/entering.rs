use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
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
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,type,child_type,total_count,total_time,avg_speed,user_id FROM t_entering_statistical WHERE user_id=? ORDER BY type,child_type").bind(&user.id).fetch_all(pool).await {Ok(rows)=>envelope_json(StatusCode::OK,200,Some(json!(rows.iter().map(|row|json!({"id":row.get::<String,_>("id"),"type":row.get::<Option<i32>,_>("child_type"),"totalCount":row.get::<Option<i32>,_>("total_count"),"totalTime":row.get::<Option<String>,_>("total_time"),"avgSpeed":row.get::<Option<String>,_>("avg_speed"),"userId":row.get::<Option<String>,_>("user_id")})).collect::<Vec<_>>())),"ok",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
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
