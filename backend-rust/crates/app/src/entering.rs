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
