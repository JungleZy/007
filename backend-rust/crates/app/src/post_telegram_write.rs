use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::json;
use sqlx::Row;
#[derive(serde::Deserialize)]
pub(crate) struct AddRequest {
    name: Option<String>,
    #[serde(rename = "isCable")]
    is_cable: Option<i32>,
    #[serde(rename = "type")]
    train_type: Option<i32>,
    #[serde(rename = "codeSort")]
    code_sort: Option<bool>,
    #[serde(rename = "isRandom")]
    is_random: Option<bool>,
    #[serde(rename = "messageNumber")]
    message_number: Option<i32>,
}
#[derive(serde::Deserialize)]
pub(crate) struct ActionRequest {
    id: Option<String>,
    attempt: Option<i32>,
}
fn err(e: sqlx::Error, op: &'static str) -> Response {
    tracing::warn!(error=%e,operation=op,"post telegram write failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}
pub(crate) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<AddRequest>,
) -> Response {
    let Some(message_number) = body.message_number.filter(|v| *v > 0) else {
        return envelope_response(StatusCode::OK, 202, "报文数量不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let id = uuid::Uuid::new_v4().to_string();
    match sqlx::query("INSERT INTO t_post_telegram_train (id,name,is_cable,type,code_sort,is_random,message_number,status,create_user,create_time,error_number,accuracy,protocol_version,attempt,full_score,active_millis) VALUES (?,?,?,?,?,?,?,0,?,NOW(),0,'0.00',1,0,0,0)").bind(&id).bind(body.name).bind(body.is_cable.unwrap_or(0)).bind(body.train_type.unwrap_or(0)).bind(body.code_sort.unwrap_or(false) as i32).bind(body.is_random.unwrap_or(false) as i32).bind(message_number).bind(&user.id).execute(pool).await{Ok(_)=>envelope_json(StatusCode::OK,200,Some(json!({"id":id,"status":0,"protocolVersion":1,"attempt":0,"messageNumber":message_number})),"ok",""),Err(e)=>err(e,"add")}
}
pub(crate) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    action(state, user, body, 1).await
}
pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    action(state, user, body, 3).await
}
pub(crate) async fn stop(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    let Some(id) = body.id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE t_post_telegram_train SET status=2,attempt=attempt+1,active_millis=0 WHERE id=? AND create_user=? AND status<>3").bind(id).bind(&user.id).execute(pool).await{Ok(_)=>envelope_response(StatusCode::OK,200,"ok",""),Err(e)=>err(e,"stop")}
}
async fn action(state: AppState, user: AuthUser, body: ActionRequest, status: i32) -> Response {
    let Some(id) = body.id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = if status == 1 {
        "UPDATE t_post_telegram_train SET status=1,start_time=COALESCE(start_time,NOW(6)) WHERE id=? AND create_user=? AND status IN (0,2)"
    } else {
        "UPDATE t_post_telegram_train SET status=3,end_time=NOW(6),active_millis=COALESCE(active_millis,0) WHERE id=? AND create_user=? AND status IN (1,2)"
    };
    match sqlx::query(sql).bind(id).bind(&user.id).execute(pool).await {
        Ok(r) if r.rows_affected() == 1 => envelope_response(StatusCode::OK, 200, "ok", ""),
        Ok(_) => envelope_response(StatusCode::OK, 208, "训练状态不可执行", ""),
        Err(e) => err(e, "action"),
    }
}
pub(crate) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    let Some(id) = body.id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("DELETE FROM t_post_telegram_train WHERE id=? AND create_user=?")
        .bind(id)
        .bind(&user.id)
        .execute(pool)
        .await
    {
        Ok(_) => envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", ""),
        Err(e) => err(e, "delete"),
    }
}
#[derive(serde::Deserialize)]
pub(crate) struct ContentQuery {
    id: Option<String>,
    #[serde(rename = "floorNumber")]
    floor_number: Option<i32>,
}
pub(crate) async fn content(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ContentQuery>,
) -> Response {
    let Some(train_id) = body.id.filter(|v| !v.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let floor = body.floor_number.unwrap_or(1);
    match sqlx::query("SELECT c.mores_key,c.mores_value,c.mores_time,c.pat_keys,t.protocol_version,t.attempt FROM t_post_telegram_train_floor_content c JOIN t_post_telegram_train t ON t.id=c.train_id WHERE c.train_id=? AND c.floor_number=? AND t.create_user=? ORDER BY c.sort").bind(&train_id).bind(floor).bind(&user.id).fetch_all(pool).await {
        Ok(rows) => envelope_json(StatusCode::OK, 200, Some(json!({"protocolVersion":rows.first().map(|r|r.get::<Option<i32>,_>("protocol_version")).flatten().unwrap_or(0),"attempt":rows.first().map(|r|r.get::<Option<i32>,_>("attempt")).flatten().unwrap_or(0),"messageBody":rows.iter().map(|r|r.get::<Option<String>,_>("mores_value").unwrap_or_else(||"[]".into())).collect::<Vec<_>>(),"messageKey":rows.iter().map(|r|json!({"moresKey":r.get::<Option<String>,_>("mores_key"),"moresValue":r.get::<Option<String>,_>("mores_value"),"moresTime":r.get::<Option<String>,_>("mores_time"),"patKeys":r.get::<Option<String>,_>("pat_keys")})).collect::<Vec<_>>() })), "ok", ""),
        Err(e) => err(e, "content"),
    }
}
#[derive(serde::Deserialize)]
pub(crate) struct ContentUpdate {
    id: Option<String>,
    #[serde(rename = "moresValue")]
    mores_value: Option<String>,
    #[serde(rename = "moresTime")]
    mores_time: Option<String>,
}
pub(crate) async fn save_content(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ContentUpdate>,
) -> Response {
    let Some(id) = body.id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE t_post_telegram_train_floor_content c JOIN t_post_telegram_train t ON t.id=c.train_id AND t.create_user=? SET c.mores_value=?,c.mores_time=? WHERE c.id=?").bind(&user.id).bind(body.mores_value.unwrap_or_else(||"[]".into())).bind(body.mores_time.unwrap_or_else(||"[]".into())).bind(id).execute(pool).await {
        Ok(r) if r.rows_affected()==1 => envelope_response(StatusCode::OK, 200, "ok", ""),
        Ok(_) => envelope_response(StatusCode::OK, 207, "无权限或内容不存在", ""),
        Err(e) => err(e, "save content"),
    }
}
