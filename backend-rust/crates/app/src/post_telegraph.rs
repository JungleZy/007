use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::Row;
const COLUMNS:&str="id,title,is_cable,status,duration,total_number,error_number,accuracy,speed,create_user_id,attempt,protocol_version,full_score,content,message_type";
fn row_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id":row.get::<String,_>("id"),"title":row.get::<Option<String>,_>("title"),"isCable":row.get::<Option<i32>,_>("is_cable"),"status":row.get::<Option<i32>,_>("status"),"duration":row.get::<Option<String>,_>("duration"),"totalNumber":row.get::<Option<i32>,_>("total_number"),"errorNumber":row.get::<Option<i32>,_>("error_number"),"accuracy":row.get::<Option<f64>,_>("accuracy"),"speed":row.get::<Option<String>,_>("speed"),"attempt":row.get::<Option<i32>,_>("attempt"),"protocolVersion":row.get::<Option<i32>,_>("protocol_version"),"fullScore":row.get::<Option<String>,_>("full_score"),"content":row.get::<Option<String>,_>("content"),"messageType":row.get::<Option<i32>,_>("message_type")})
}
#[derive(serde::Deserialize)]
pub(crate) struct IdRequest {
    pub id: Option<String>,
}
pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql=format!("SELECT {COLUMNS} FROM t_post_telegraph_key_pat_train WHERE create_user_id=? ORDER BY create_time DESC");
    match sqlx::query(&sql).bind(&user.id).fetch_all(pool).await {
        Ok(rows) => envelope_json(
            StatusCode::OK,
            200,
            Some(Value::Array(rows.iter().map(row_json).collect())),
            "ok",
            "",
        ),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
pub(crate) async fn detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<IdRequest>,
) -> Response {
    let Some(id) = body.id.filter(|v| !v.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = format!("SELECT {COLUMNS} FROM t_post_telegraph_key_pat_train WHERE id=? LIMIT 1");
    match sqlx::query(&sql).bind(id).fetch_optional(pool).await {
        Ok(Some(row))
            if row.get::<Option<String>, _>("create_user_id").as_deref()
                == Some(user.id.as_str()) =>
        {
            envelope_json(StatusCode::OK, 200, Some(row_json(&row)), "ok", "")
        }
        Ok(Some(_)) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Ok(None) => envelope_response(StatusCode::OK, 202, "未查询到训练", ""),
        Err(_) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
    }
}
