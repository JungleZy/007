use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Path, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::Row;

#[derive(Debug, Deserialize)]
pub(crate) struct RoomQuery {
    #[serde(rename = "roomId")]
    room_id: Option<i32>,
}
#[derive(Debug, Deserialize)]
pub(crate) struct ChannelRequest {
    #[serde(rename = "roomId")]
    room_id: Option<i32>,
    #[serde(rename = "userId")]
    user_id: Option<String>,
    channel: Option<i32>,
}
fn db_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error=%error,operation,"simulation database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}
fn room_json(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({"id":row.get::<i32,_>("id"),"name":row.get::<Option<String>,_>("name"),"isCable":row.get::<Option<i32>,_>("is_cable"),"stats":row.get::<Option<i32>,_>("stats"),"roomType":row.get::<Option<i32>,_>("room_type"),"totalTime":row.get::<Option<i32>,_>("total_time"),"playStatus":row.get::<Option<i32>,_>("play_status"),"setting":row.get::<Option<String>,_>("setting"),"isStartSign":row.get::<Option<i32>,_>("is_start_sign")})
}
async fn add_room(state: AppState, user: AuthUser, body: Value, room_type: i32) -> Response {
    let Some(name) = body
        .get("roomName")
        .and_then(Value::as_str)
        .filter(|v| !v.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "房间名称不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return db_error(e, "start add room"),
    };
    let room=match sqlx::query("INSERT INTO simulation_router_room (name,is_cable,create_user_Id,stats,create_time,room_type,total_time,play_status,setting,is_start_sign) VALUES (?,?,?,0,NOW(),?,0,0,?,?)").bind(name).bind(body.get("isCable").and_then(Value::as_i64).unwrap_or(0)).bind(&user.id).bind(room_type).bind(body.get("setting").map(Value::to_string)).bind(body.get("isStartSign").and_then(Value::as_i64).unwrap_or(1)).execute(&mut *tx).await{Ok(r)=>r.last_insert_id() as i32,Err(e)=>return db_error(e,"insert room")};
    if let Some(content) = body.get("content").and_then(Value::as_str) {
        if let Err(e)=sqlx::query("INSERT INTO simulation_router_room_content (room_id,content,main_signal,interference_signal,bd_type,bw_type,bw_count,is_random) VALUES (?,?,?,?,?,?,?,?)").bind(room).bind(content).bind(body.get("mainSignal").and_then(Value::as_str)).bind(body.get("interferenceSignal").and_then(Value::as_str)).bind(body.get("bdType").and_then(Value::as_i64)).bind(body.get("bwType").and_then(Value::as_i64)).bind(body.get("bwCount").and_then(Value::as_i64)).bind(body.get("isRandom").and_then(Value::as_i64)).execute(&mut *tx).await{return db_error(e,"insert room content");}
    }
    for (key, user_type) in [("sendUserList", 0), ("receiveUserList", 1)] {
        if let Some(users) = body.get(key).and_then(Value::as_array) {
            for uid in users.iter().filter_map(Value::as_str) {
                if let Err(e)=sqlx::query("INSERT INTO simulation_router_room_user (room_id,user_id,user_type,user_status) VALUES (?,?,?,0)").bind(room).bind(uid).bind(user_type).execute(&mut *tx).await{return db_error(e,"insert room member");}
            }
        }
    }
    if let Err(e) = tx.commit().await {
        return db_error(e, "commit add room");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"id":room,"name":name,"roomType":room_type,"stats":0})),
        "ok",
        "",
    )
}
pub(crate) async fn add_router(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    add_room(s, u, b, 0).await
}
pub(crate) async fn add_disturb(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    add_room(s, u, b, 1).await
}
pub(crate) async fn add_report(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    add_room(s, u, b, 2).await
}
pub(crate) async fn add_recept(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    add_room(s, u, b, 3).await
}
async fn list_type(state: AppState, user: AuthUser, room_type: i32) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,name,is_cable,stats,room_type,total_time,play_status,setting,is_start_sign FROM simulation_router_room r WHERE room_type=? AND (create_user_Id=? OR EXISTS(SELECT 1 FROM simulation_router_room_user u WHERE u.room_id=r.id AND u.user_id=?)) ORDER BY create_time DESC").bind(room_type).bind(&user.id).bind(&user.id).fetch_all(pool).await{Ok(rows)=>envelope_json(StatusCode::OK,200,Some(Value::Array(rows.iter().map(room_json).collect())),"ok",""),Err(e)=>db_error(e,"list rooms")}
}
pub(crate) async fn list_router(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
) -> Response {
    list_type(s, u, 0).await
}
pub(crate) async fn list_disturb(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
) -> Response {
    list_type(s, u, 1).await
}
pub(crate) async fn list_report(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
) -> Response {
    list_type(s, u, 2).await
}
pub(crate) async fn list_recept(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
) -> Response {
    list_type(s, u, 3).await
}
pub(crate) async fn detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<RoomQuery>,
) -> Response {
    let Some(id) = query.room_id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT id,name,is_cable,stats,room_type,total_time,play_status,setting,is_start_sign FROM simulation_router_room r WHERE id=? AND (create_user_Id=? OR EXISTS(SELECT 1 FROM simulation_router_room_user u WHERE u.room_id=r.id AND u.user_id=?)) LIMIT 1").bind(id).bind(&user.id).bind(&user.id).fetch_optional(pool).await{Ok(Some(row))=>envelope_json(StatusCode::OK,200,Some(room_json(&row)),"ok",""),Ok(None)=>envelope_response(StatusCode::OK,207,"无权限或房间不存在",""),Err(e)=>db_error(e,"room detail")}
}
pub(crate) async fn users(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Path(id): Path<i32>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let allowed=sqlx::query("SELECT id FROM simulation_router_room r WHERE id=? AND (create_user_Id=? OR EXISTS(SELECT 1 FROM simulation_router_room_user u WHERE u.room_id=r.id AND u.user_id=?))").bind(id).bind(&user.id).bind(&user.id).fetch_optional(pool).await;
    if !matches!(allowed, Ok(Some(_))) {
        return envelope_response(StatusCode::OK, 207, "无权限或房间不存在", "");
    }
    match sqlx::query("SELECT id,room_id,user_id,user_type,channel,content_value,user_status FROM simulation_router_room_user WHERE room_id=? ORDER BY id").bind(id).fetch_all(pool).await{Ok(rows)=>envelope_json(StatusCode::OK,200,Some(json!(rows.iter().map(|r|json!({"id":r.get::<i32,_>("id"),"roomId":r.get::<Option<i32>,_>("room_id"),"userId":r.get::<Option<String>,_>("user_id"),"userType":r.get::<Option<i32>,_>("user_type"),"channel":r.get::<Option<i32>,_>("channel"),"contentValue":r.get::<Option<String>,_>("content_value"),"userStatus":r.get::<Option<i32>,_>("user_status")})).collect::<Vec<_>>())),"ok",""),Err(e)=>db_error(e,"room users")}
}
pub(crate) async fn change_channel(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ChannelRequest>,
) -> Response {
    let (Some(room), Some(uid), Some(channel)) = (body.room_id, body.user_id, body.channel) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE simulation_router_room_user u JOIN simulation_router_room r ON r.id=u.room_id SET u.channel=? WHERE u.room_id=? AND u.user_id=? AND (r.create_user_Id=? OR EXISTS(SELECT 1 FROM simulation_router_room_user a WHERE a.room_id=r.id AND a.user_id=? AND a.user_type=0))").bind(channel).bind(room).bind(uid).bind(&user.id).bind(&user.id).execute(pool).await{Ok(r) if r.rows_affected()==1=>envelope_response(StatusCode::OK,200,"ok",""),Ok(_)=>envelope_response(StatusCode::OK,207,"无权限",""),Err(e)=>db_error(e,"change channel")}
}
pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<RoomQuery>,
) -> Response {
    let Some(id) = query.room_id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE simulation_router_room SET stats=3 WHERE id=? AND create_user_Id=?")
        .bind(id)
        .bind(&user.id)
        .execute(pool)
        .await
    {
        Ok(r) if r.rows_affected() == 1 => envelope_response(StatusCode::OK, 200, "ok", ""),
        Ok(_) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Err(e) => db_error(e, "finish room"),
    }
}
pub(crate) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<RoomQuery>,
) -> Response {
    let Some(id) = query.room_id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return db_error(e, "start delete room"),
    };
    let owned = match sqlx::query(
        "SELECT id FROM simulation_router_room WHERE id=? AND create_user_Id=? FOR UPDATE",
    )
    .bind(id)
    .bind(&user.id)
    .fetch_optional(&mut *tx)
    .await
    {
        Ok(Some(_)) => true,
        Ok(None) => false,
        Err(e) => return db_error(e, "lock delete room"),
    };
    if !owned {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    for sql in [
        "DELETE FROM simulation_router_room_page_value WHERE room_id=?",
        "DELETE FROM simulation_router_room_page WHERE room_id=?",
        "DELETE FROM simulation_router_room_user WHERE room_id=?",
        "DELETE FROM simulation_router_room_content WHERE room_id=?",
        "DELETE FROM simulation_router_room WHERE id=?",
    ] {
        if let Err(e) = sqlx::query(sql).bind(id).execute(&mut *tx).await {
            return db_error(e, "delete room graph");
        }
    }
    if let Err(e) = tx.commit().await {
        return db_error(e, "commit delete room");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", "")
}
pub(crate) async fn detail_body(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let query = RoomQuery {
        room_id: body.get("roomId").and_then(Value::as_i64).map(|v| v as i32),
    };
    detail(State(state), Extension(user), Query(query)).await
}
pub(crate) async fn users_body(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body.get("roomId").and_then(Value::as_i64).map(|v| v as i32) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    users(State(state), Extension(user), Path(id)).await
}
pub(crate) async fn channels(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<RoomQuery>,
) -> Response {
    let Some(id) = query.room_id else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let allowed=sqlx::query("SELECT id FROM simulation_router_room r WHERE id=? AND (create_user_Id=? OR EXISTS(SELECT 1 FROM simulation_router_room_user u WHERE u.room_id=r.id AND u.user_id=?))").bind(id).bind(&user.id).bind(&user.id).fetch_optional(pool).await;
    if !matches!(allowed, Ok(Some(_))) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    match sqlx::query("SELECT DISTINCT channel FROM simulation_router_room_user WHERE room_id=? AND channel IS NOT NULL ORDER BY channel").bind(id).fetch_all(pool).await{Ok(rows)=>envelope_json(StatusCode::OK,200,Some(json!(rows.iter().map(|r|r.get::<i32,_>("channel")).collect::<Vec<_>>())),"ok",""),Err(e)=>db_error(e,"room channels")}
}
pub(crate) async fn save_setting(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body.get("roomId").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE simulation_router_room SET setting=? WHERE id=? AND create_user_Id=?")
        .bind(body.to_string())
        .bind(id)
        .bind(&user.id)
        .execute(pool)
        .await
    {
        Ok(r) if r.rows_affected() == 1 => envelope_json(StatusCode::OK, 200, Some(body), "ok", ""),
        Ok(_) => envelope_response(StatusCode::OK, 207, "无权限", ""),
        Err(e) => db_error(e, "save setting"),
    }
}
pub(crate) async fn upload_result(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(room) = body.get("roomId").and_then(Value::as_i64) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE simulation_router_room_user SET content_value=?,user_status=1 WHERE room_id=? AND user_id=?").bind(body.to_string()).bind(room).bind(&user.id).execute(pool).await{Ok(r) if r.rows_affected()==1=>detail_body(State(state),Extension(user),Json(json!({"roomId":room}))).await,Ok(_)=>envelope_response(StatusCode::OK,207,"无权限","") ,Err(e)=>db_error(e,"upload result")}
}
#[derive(Debug, Deserialize)]
pub(crate) struct PageQuery {
    #[serde(rename = "roomId")]
    room_id: Option<i32>,
    #[serde(rename = "pageNumber")]
    page_number: Option<i32>,
    #[serde(rename = "userId")]
    user_id: Option<String>,
}
pub(crate) async fn find_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<PageQuery>,
) -> Response {
    let (Some(room), Some(page)) = (query.room_id, query.page_number) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let target = query.user_id.unwrap_or_else(|| user.id.clone());
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let allowed=sqlx::query("SELECT id FROM simulation_router_room r WHERE r.id=? AND (r.create_user_Id=? OR EXISTS(SELECT 1 FROM simulation_router_room_user u WHERE u.room_id=r.id AND u.user_id=?))").bind(room).bind(&user.id).bind(&user.id).fetch_optional(pool).await;
    if !matches!(allowed, Ok(Some(_))) {
        return envelope_response(StatusCode::OK, 207, "无权限或房间不存在", "");
    }
    match sqlx::query("SELECT p.id,p.page_number,p.sort,p.key,v.value,v.user_id FROM simulation_router_room_page p LEFT JOIN simulation_router_room_page_value v ON v.room_id=p.room_id AND v.page_number=p.page_number AND v.user_id=? WHERE p.room_id=? AND p.page_number=? ORDER BY p.sort").bind(&target).bind(room).bind(page).fetch_all(pool).await{Ok(rows)=>envelope_json(StatusCode::OK,200,Some(json!({"roomId":room,"pageNumber":page,"userId":target,"content":rows.iter().map(|r|json!({"id":r.get::<String,_>("id"),"sort":r.get::<Option<i32>,_>("sort"),"key":r.get::<Option<String>,_>("key"),"value":r.get::<Option<String>,_>("value"),"userId":r.get::<Option<String>,_>("user_id")})).collect::<Vec<_>>() })),"ok",""),Err(e)=>db_error(e,"find simulation page")}
}
#[derive(Debug, Deserialize)]
pub(crate) struct PageValue {
    #[serde(rename = "roomId")]
    room_id: Option<i32>,
    #[serde(rename = "pageNumber")]
    page_number: Option<i32>,
    value: Option<String>,
}
pub(crate) async fn save_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<PageValue>,
) -> Response {
    let (Some(room), Some(page), Some(value)) = (body.room_id, body.page_number, body.value) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let allowed =
        sqlx::query("SELECT id FROM simulation_router_room_user WHERE room_id=? AND user_id=?")
            .bind(room)
            .bind(&user.id)
            .fetch_optional(pool)
            .await;
    if !matches!(allowed, Ok(Some(_))) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    match sqlx::query("INSERT INTO simulation_router_room_page_value (page_number,room_id,value,user_id) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE value=VALUES(value)").bind(page).bind(room).bind(value).bind(&user.id).execute(pool).await{Ok(_)=>envelope_response(StatusCode::OK,200,"ok",""),Err(e)=>db_error(e,"save simulation page")}
}
