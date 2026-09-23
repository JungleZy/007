use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;
fn train_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id":row.get::<String,_>("id"),"name":row.get::<Option<String>,_>("name"),"isCable":row.get::<Option<i32>,_>("is_cable"),"type":row.get::<Option<i32>,_>("type"),"codeSort":row.get::<Option<i32>,_>("code_sort"),"isRandom":row.get::<Option<i32>,_>("is_random"),"messageNumber":row.get::<Option<i32>,_>("message_number"),"validTime":row.get::<Option<i64>,_>("valid_time"),"speed":row.get::<Option<String>,_>("speed"),"status":row.get::<Option<i32>,_>("status"),"createUser":row.get::<Option<String>,_>("create_user"),"errorNumber":row.get::<Option<i32>,_>("error_number"),"accuracy":row.get::<Option<String>,_>("accuracy"),"floorNow":row.get::<Option<i32>,_>("floor_now"),"attempt":row.get::<Option<i32>,_>("attempt"),"protocolVersion":row.get::<Option<i32>,_>("protocol_version"),"fullScore":row.get::<Option<i32>,_>("full_score"),"activeMillis":row.get::<Option<i64>,_>("active_millis")})
}
pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,name,is_cable,type,code_sort,is_random,message_number,valid_time,speed,status,create_user,error_number,accuracy,floor_now,attempt,protocol_version,full_score,active_millis FROM t_post_telegram_train WHERE create_user=? ORDER BY create_time DESC").bind(&user.id).fetch_all(pool).await{Ok(rows)=>envelope_json(StatusCode::OK,200,Some(Value::Array(rows.iter().map(train_json).collect())),"ok",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
pub(crate) async fn detail(
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
    match sqlx::query("SELECT id,name,is_cable,type,code_sort,is_random,message_number,valid_time,speed,status,create_user,error_number,accuracy,floor_now,attempt,protocol_version,full_score,active_millis FROM t_post_telegram_train WHERE id=? LIMIT 1").bind(id).fetch_optional(pool).await{Ok(Some(row)) if row.get::<Option<String>,_>("create_user").as_deref()==Some(user.id.as_str())=>envelope_json(StatusCode::OK,200,Some(train_json(&row)),"ok",""),Ok(Some(_))=>envelope_response(StatusCode::OK,207,"无权限",""),Ok(None)=>envelope_response(StatusCode::OK,202,"未查询到该训练",""),Err(_)=>envelope_response(StatusCode::INTERNAL_SERVER_ERROR,500,"服务器错误","")}
}
