use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Path, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rand::{seq::SliceRandom, Rng};
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, MySql, MySqlPool, Row, Transaction};
use std::collections::BTreeMap;

#[derive(Debug)]
enum Error {
    Business(i32, &'static str),
    Database(sqlx::Error),
    StoredJson(serde_json::Error),
    Unavailable,
}
impl From<sqlx::Error> for Error {
    fn from(error: sqlx::Error) -> Self {
        Self::Database(error)
    }
}
impl From<serde_json::Error> for Error {
    fn from(error: serde_json::Error) -> Self {
        Self::StoredJson(error)
    }
}
fn respond(result: Result<Option<Value>, Error>) -> Response {
    match result {
        Ok(data) => return envelope_json(StatusCode::OK, 200, data, "ok", ""),
        Err(Error::Business(code, message)) => {
            return envelope_response(StatusCode::OK, code, message, "");
        }
        Err(Error::Database(error)) => {
            tracing::warn!(%error, "simulation database operation failed");
        }
        Err(Error::StoredJson(error)) => {
            tracing::warn!(%error, "simulation stored JSON invalid");
        }
        Err(Error::Unavailable) => tracing::warn!("simulation database unavailable"),
    }
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}
fn pool(state: &AppState) -> Result<&MySqlPool, Error> {
    state.pool.as_ref().ok_or(Error::Unavailable)
}
async fn complete(
    tx: Transaction<'_, MySql>,
    result: Result<Option<Value>, Error>,
) -> Result<Option<Value>, Error> {
    match result {
        Ok(value) => {
            tx.commit().await?;
            Ok(value)
        }
        Err(error) => {
            tx.rollback().await?;
            Err(error)
        }
    }
}
fn required_id(id: Option<i32>) -> Result<i32, Error> {
    id.filter(|id| *id > 0)
        .ok_or(Error::Business(202, "房间编号不正确"))
}
fn int(body: &Value, name: &str) -> Option<i32> {
    body.get(name)
        .and_then(Value::as_i64)
        .and_then(|n| i32::try_from(n).ok())
}
fn body_room(body: &Value) -> Result<i32, Error> {
    required_id(int(body, "roomId"))
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct RoomQuery {
    room_id: Option<i32>,
}
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ChannelRequest {
    room_id: Option<i32>,
    user_id: Option<String>,
    channel: Option<i32>,
}
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PageQuery {
    room_id: Option<i32>,
    page_number: Option<i32>,
    user_id: Option<String>,
}
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PageValue {
    room_id: Option<i32>,
    page_number: Option<i32>,
    value: Option<String>,
}

// Every child-table mutation locks its parent first, including lazy standard-page creation.
async fn lock_room(tx: &mut Transaction<'_, MySql>, id: i32) -> Result<MySqlRow, Error> {
    sqlx::query("SELECT r.*,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') create_time_text,DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s') start_time_text,CASE WHEN stats=1 AND start_time IS NOT NULL THEN TIMESTAMPDIFF(SECOND,start_time,NOW()) ELSE total_time END elapsed_time FROM simulation_router_room r WHERE id=? FOR UPDATE")
        .bind(id).fetch_optional(&mut **tx).await?.ok_or(Error::Business(202, "未查询到房间信息"))
}
async fn member(
    tx: &mut Transaction<'_, MySql>,
    room: i32,
    user: &str,
) -> Result<Option<MySqlRow>, Error> {
    Ok(sqlx::query("SELECT id,user_id,user_type,channel,content_value,user_status FROM simulation_router_room_user WHERE room_id=? AND user_id=? ORDER BY id LIMIT 1 FOR UPDATE")
        .bind(room).bind(user).fetch_optional(&mut **tx).await?)
}
fn manages(room: &MySqlRow, user: &AuthUser, member: Option<&MySqlRow>) -> bool {
    user.is_admin
        || room.get::<Option<String>, _>("create_user_Id").as_deref() == Some(user.id.as_str())
        || (room.get::<Option<i32>, _>("room_type") != Some(0)
            && member.is_some_and(|m| m.get::<Option<i32>, _>("user_type") == Some(0)))
}
async fn access(
    tx: &mut Transaction<'_, MySql>,
    room: &MySqlRow,
    user: &AuthUser,
) -> Result<(bool, Option<MySqlRow>), Error> {
    let actor = member(tx, room.get("id"), &user.id).await?;
    let teacher = manages(room, user, actor.as_ref());
    if !teacher && actor.is_none() {
        return Err(Error::Business(207, "无权查看该训练"));
    }
    Ok((teacher, actor))
}
fn control(teacher: bool) -> Result<(), Error> {
    if teacher {
        Ok(())
    } else {
        Err(Error::Business(207, "无权限"))
    }
}
fn mutable(room: &MySqlRow) -> Result<(), Error> {
    if room.get::<Option<i32>, _>("stats").is_some_and(|s| s >= 2) {
        Err(Error::Business(208, "训练已结束"))
    } else {
        Ok(())
    }
}
fn room_json(row: &MySqlRow) -> Value {
    json!({"id":row.get::<i32,_>("id"),"name":row.get::<Option<String>,_>("name"),
        "isCable":row.get::<Option<i32>,_>("is_cable"),"createUserId":row.get::<Option<String>,_>("create_user_Id"),
        "stats":row.get::<Option<i32>,_>("stats"),"roomType":row.get::<Option<i32>,_>("room_type"),
        "createTime":row.get::<Option<String>,_>("create_time_text"),"totalTime":row.get::<Option<i32>,_>("total_time"),
        "playStatus":row.get::<Option<i32>,_>("play_status"),"setting":row.get::<Option<String>,_>("setting"),
        "startTime":row.get::<Option<String>,_>("start_time_text"),"isStartSign":row.get::<Option<i32>,_>("is_start_sign")})
}
async fn content(tx: &mut Transaction<'_, MySql>, room: i32) -> Result<MySqlRow, Error> {
    sqlx::query("SELECT * FROM simulation_router_room_content WHERE room_id=? ORDER BY id LIMIT 1 FOR UPDATE")
        .bind(room).fetch_optional(&mut **tx).await?.ok_or(Error::Business(202, "未找到房间报底"))
}
fn content_json(out: &mut Value, row: &MySqlRow) {
    for (key, column) in [
        ("bdType", "bd_type"),
        ("bwType", "bw_type"),
        ("bwCount", "bw_count"),
    ] {
        out[key] = json!(row.get::<Option<i32>, _>(column));
    }
    for (key, column) in [
        ("content", "content"),
        ("mainSignal", "main_signal"),
        ("interferenceSignal", "interference_signal"),
    ] {
        out[key] = json!(row.get::<Option<String>, _>(column));
    }
    out["pageCount"] = Value::Null;
}
fn response_shape(mut value: Value, kind: i32, listing: bool) -> Value {
    let fields: &[&str] = match kind {
        0 => &[
            "id",
            "name",
            "isCable",
            "createUserId",
            "stats",
            "createTime",
            "bdType",
            "bwType",
            "bwCount",
            "pageCount",
            "content",
            "currentUserChannel",
            "currentUserStatus",
            "currentUserId",
            "startTime",
            "totalTime",
            "existPageNumber",
        ],
        1 if listing => &[
            "id",
            "name",
            "isCable",
            "createUserId",
            "userName",
            "userImg",
            "stats",
            "createTime",
            "bdType",
            "bwType",
            "bwCount",
            "pageCount",
        ],
        1 => &[
            "teacher",
            "id",
            "isCable",
            "name",
            "userName",
            "userImg",
            "stats",
            "createTime",
            "bdType",
            "bwType",
            "bwCount",
            "pageCount",
            "content",
            "mainSignal",
            "interferenceSignal",
            "contentValue",
            "totalTime",
            "setting",
            "existPageNumber",
        ],
        _ => &[
            "teacher",
            "id",
            "isCable",
            "userName",
            "userImg",
            "name",
            "createUserId",
            "stats",
            "createTime",
            "bdType",
            "bwType",
            "bwCount",
            "pageCount",
            "content",
            "channel",
            "receiveUser",
            "totalTime",
            "mainSignal",
            "playStatus",
            "existPageNumber",
            "isStartSign",
        ],
    };
    let object = value.as_object_mut().expect("room responses are objects");
    object.retain(|key, _| fields.contains(&key.as_str()));
    for field in fields {
        object.entry(*field).or_insert(Value::Null);
    }
    value
}
async fn answer_count(
    tx: &mut Transaction<'_, MySql>,
    room: i32,
    user: &str,
) -> Result<i64, Error> {
    Ok(sqlx::query_scalar(
        "SELECT COUNT(*) FROM simulation_router_room_page_value WHERE room_id=? AND user_id=?",
    )
    .bind(room)
    .bind(user)
    .fetch_one(&mut **tx)
    .await?)
}
async fn member_rows(tx: &mut Transaction<'_, MySql>, room: i32) -> Result<Vec<MySqlRow>, Error> {
    Ok(sqlx::query("SELECT m.*,u.user_name,u.user_img,(SELECT COUNT(*) FROM simulation_router_room_page_value v WHERE v.room_id=m.room_id AND v.user_id=m.user_id) answer_count FROM simulation_router_room_user m JOIN t_user u ON u.id=m.user_id WHERE m.room_id=? ORDER BY m.id")
        .bind(room).fetch_all(&mut **tx).await?)
}
fn user_json(row: &MySqlRow) -> Value {
    json!({"id":row.get::<Option<String>,_>("user_id"),"userName":row.get::<Option<String>,_>("user_name"),
        "userImg":row.get::<Option<String>,_>("user_img"),"existPageNumber":row.get::<i64,_>("answer_count")})
}
async fn users_json(tx: &mut Transaction<'_, MySql>, room: i32) -> Result<Value, Error> {
    let mut send = Vec::new();
    let mut receive = Vec::new();
    for row in member_rows(tx, room).await? {
        let mut item = user_json(&row);
        item["channel"] = json!(row.get::<Option<i32>, _>("channel"));
        item["status"] = json!(row.get::<Option<i32>, _>("user_status"));
        item["socketStatus"] = json!(super::ws::simulation_connection_status(
            room,
            row.get::<String, _>("user_id").as_str()
        )
        .await
        .unwrap_or(0));
        if row.get::<Option<i32>, _>("user_type") == Some(0) {
            send.push(item);
        } else {
            receive.push(item);
        }
    }
    Ok(json!({"sendUserList":send,"receiveUserList":receive}))
}
async fn detail_json(
    tx: &mut Transaction<'_, MySql>,
    room: &MySqlRow,
    user: &AuthUser,
    content_view: bool,
) -> Result<Value, Error> {
    let id = room.get::<i32, _>("id");
    let (teacher, actor) = access(tx, room, user).await?;
    let body = content(tx, id).await?;
    let kind = if content_view {
        1
    } else {
        room.get::<Option<i32>, _>("room_type")
            .ok_or(Error::Business(202, "房间类型缺失"))?
    };
    let mut out = room_json(room);
    content_json(&mut out, &body);
    out["existPageNumber"] = json!(answer_count(tx, id, &user.id).await?);
    if kind == 0 {
        out["id"] = json!(body.get::<i32, _>("id"));
        out["currentUserId"] = json!(user.id);
        let owner =
            room.get::<Option<String>, _>("create_user_Id").as_deref() == Some(user.id.as_str());
        out["currentUserChannel"] = json!(if owner {
            None
        } else {
            actor
                .as_ref()
                .and_then(|m| m.get::<Option<i32>, _>("channel"))
        });
        out["currentUserStatus"] = json!(if owner {
            None
        } else {
            actor
                .as_ref()
                .and_then(|m| m.get::<Option<i32>, _>("user_status"))
        });
        out["totalTime"] = json!(room.get::<Option<i64>, _>("elapsed_time"));
        out["startTime"] = json!(room
            .get::<Option<String>, _>("start_time_text")
            .unwrap_or_default());
    } else {
        out["teacher"] = json!(teacher);
        let creator: Option<String> = room.get("create_user_Id");
        let creator_row = sqlx::query("SELECT id,user_name,user_img FROM t_user WHERE id=?")
            .bind(&creator)
            .fetch_optional(&mut **tx)
            .await?;
        out["userName"] = json!(creator_row
            .as_ref()
            .and_then(|r| r.get::<Option<String>, _>("user_name")));
        out["userImg"] = json!(creator_row
            .as_ref()
            .and_then(|r| r.get::<Option<String>, _>("user_img")));
        if kind == 1 {
            out["id"] = json!(creator_row.as_ref().map(|r| r.get::<String, _>("id")));
            out["contentValue"] = json!(actor
                .as_ref()
                .and_then(|m| m.get::<Option<String>, _>("content_value")));
        } else {
            out["id"] = json!(body.get::<i32, _>("id"));
            out["channel"] = Value::Null;
            let mut receivers = Vec::new();
            for row in member_rows(tx, id).await? {
                if row.get::<Option<i32>, _>("channel") != Some(1) {
                    continue;
                }
                let uid: String = row.get("user_id");
                let mut item = user_json(&row);
                item["status"] = json!(super::ws::simulation_connection_status(id, &uid)
                    .await
                    .unwrap_or(0));
                item["userStatus"] = json!(row.get::<Option<i32>, _>("user_status"));
                item["contentValue"] = json!(if teacher || uid == user.id {
                    row.get::<Option<String>, _>("content_value")
                } else {
                    None
                });
                receivers.push(item);
            }
            out["receiveUser"] = json!(receivers);
        }
    }
    if room.get::<Option<i32>, _>("is_cable") == Some(1) {
        let counts = sqlx::query("SELECT MAX(page_number) max_page,COUNT(*) groups_count FROM simulation_router_room_page WHERE room_id=?")
            .bind(id).fetch_one(&mut **tx).await?;
        out["pageCount"] = json!(counts.get::<Option<i32>, _>("max_page"));
        out["bwCount"] = json!(counts.get::<i64, _>("groups_count"));
    }
    Ok(response_shape(out, kind, false))
}

// Match SimulationMessageGenerator, including numeric average blocks and unique symbols per group.
fn generated_groups(
    count: usize,
    page: i32,
    kind: i32,
    average: bool,
    random: bool,
) -> Vec<String> {
    let alphabet: &[u8] = match kind {
        3 => b"ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        4 => b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        _ => b"0123456789",
    };
    let mut rng = rand::thread_rng();
    let start = if kind == 3 || kind == 4 {
        ((i64::from(page) - 1) * 400) as usize % alphabet.len()
    } else {
        0
    };
    let average_digits =
        average && (alphabet.len() == 10 || (kind == 4 && start + count * 4 <= 10));
    let mut shuffled = Vec::new();
    if average && random && !average_digits {
        shuffled = (0..count * 4)
            .map(|i| alphabet[(start + i) % alphabet.len()])
            .collect();
        shuffled.shuffle(&mut rng);
    }
    let mut digits = *b"0123456789";
    (0..count)
        .map(|index| {
            if average_digits && index % 10 == 0 {
                digits = *b"0123456789";
                if random {
                    digits.shuffle(&mut rng);
                }
            }
            let mut group = [0u8; 4];
            for (offset, symbol) in group.iter_mut().enumerate() {
                *symbol = if average_digits {
                    digits[(index % 10 + offset) % 10]
                } else if !shuffled.is_empty() {
                    shuffled[index * 4 + offset]
                } else if random && !average {
                    alphabet[rng.gen_range(0..alphabet.len())]
                } else {
                    alphabet[(start + index * 4 + offset) % alphabet.len()]
                };
            }
            let replacement: &[u8] = if group.iter().all(u8::is_ascii_digit) {
                b"0123456789"
            } else if group.iter().all(u8::is_ascii_alphabetic) {
                b"ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            } else {
                b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            };
            for offset in 0..4 {
                if group[..offset].contains(&group[offset]) {
                    group[offset] = *replacement
                        .iter()
                        .find(|s| !group[..offset].contains(s))
                        .expect("alphabet has at least four symbols");
                }
            }
            group.into_iter().map(char::from).collect()
        })
        .collect()
}
async fn insert_groups(
    tx: &mut Transaction<'_, MySql>,
    room: i32,
    page: i32,
    groups: &[String],
) -> Result<(), Error> {
    for (index, key) in groups.iter().enumerate() {
        sqlx::query("INSERT INTO simulation_router_room_page (id,room_id,page_number,sort,`key`) VALUES (?,?,?,?,?)")
            .bind(uuid::Uuid::new_v4().to_string()).bind(room).bind(page + (index / 100) as i32).bind((index % 100) as i32).bind(key)
            .execute(&mut **tx).await?;
    }
    Ok(())
}
async fn add_room(
    state: &AppState,
    user: &AuthUser,
    body: Value,
    kind: i32,
) -> Result<Option<Value>, Error> {
    let name = body
        .get("roomName")
        .and_then(Value::as_str)
        .filter(|v| !v.is_empty())
        .ok_or(Error::Business(202, "房间名称不能为空"))?;
    let cable = match body.get("isCable") {
        None => 0,
        Some(_) => int(&body, "isCable").ok_or(Error::Business(202, "是否使用电缆报底不能为空"))?,
    };
    let count = int(&body, "bwCount")
        .filter(|v| *v > 0)
        .ok_or(Error::Business(202, "报文组数不能为空"))?;
    let kind_code = int(&body, "bwType");
    if cable == 0 && kind_code.is_none() {
        return Err(Error::Business(202, "报文类型不能为空"));
    }
    let mut tx = pool(state)?.begin().await?;
    let result = async {
        let room = sqlx::query("INSERT INTO simulation_router_room (name,is_cable,create_user_Id,stats,create_time,room_type,is_start_sign) VALUES (?,?,?,0,NOW(),?,?)")
            .bind(name).bind(cable).bind(&user.id).bind(kind).bind(if kind == 3 { int(&body,"isStartSign").unwrap_or(1) } else { 1 })
            .execute(&mut *tx).await?.last_insert_id() as i32;
        sqlx::query("INSERT INTO simulation_router_room_content (room_id,content,main_signal,interference_signal,bd_type,bw_type,bw_count,is_random) VALUES (?,?,?,?,?,?,?,?)")
            .bind(room).bind(body.get("content").and_then(Value::as_str)).bind(body.get("mainSignal").and_then(Value::as_str))
            .bind(body.get("interferenceSignal").and_then(Value::as_str)).bind(int(&body,"bdType")).bind(kind_code).bind(count).bind(int(&body,"isRandom"))
            .execute(&mut *tx).await?;
        if cable == 0 {
            let average = int(&body,"bdType").ok_or(Error::Business(202,"报底类型不能为空"))? == 1;
            let random = int(&body,"isRandom").ok_or(Error::Business(202,"随机配置不能为空"))? == 1;
            let groups = generated_groups(count.min(200) as usize, 1, kind_code.expect("validated generated type"), average, random);
            insert_groups(&mut tx, room, 1, &groups).await?;
        } else {
            let cable_id = body.get("cableId").and_then(Value::as_str).ok_or(Error::Business(202,"固定报编号不能为空"))?;
            let rows = sqlx::query("SELECT floor_number,mores_key FROM t_cable_floor WHERE cable_id=? ORDER BY floor_number,sort FOR UPDATE")
                .bind(cable_id).fetch_all(&mut *tx).await?;
            let mut floors = BTreeMap::<i32,Vec<String>>::new();
            for row in rows {
                let number = row.get::<Option<i32>,_>("floor_number").ok_or(Error::Business(202,"固定报页码缺失"))?;
                let encoded: String = row.try_get("mores_key")?;
                let codes: Vec<String> = serde_json::from_str(&encoded)?;
                floors.entry(number).or_default().push(codes.concat());
            }
            let start = int(&body,"startPage").unwrap_or(1).max(1) as usize - 1;
            let total = (count / 100) as usize;
            if total == 0 { return Err(Error::Business(202,"报文组数不足一页，无法建立房间")); }
            if start >= floors.len() || total > floors.len() - start { return Err(Error::Business(202,"所选电缆可用楼层不足")); }
            for (page, groups) in floors.values().skip(start).take(total).enumerate() {
                // Cable floors retain their original group count and never spill into another floor.
                for (sort, key) in groups.iter().enumerate() {
                    sqlx::query("INSERT INTO simulation_router_room_page (id,room_id,page_number,sort,`key`) VALUES (?,?,?,?,?)")
                        .bind(uuid::Uuid::new_v4().to_string()).bind(room).bind((page + 1) as i32).bind(sort as i32).bind(key).execute(&mut *tx).await?;
                }
            }
        }
        if kind == 1 {
            sqlx::query("INSERT INTO simulation_router_room_user (room_id,user_id,user_type,channel,user_status) VALUES (?,?,0,-1,0)")
                .bind(room).bind(&user.id).execute(&mut *tx).await?;
        } else {
            for (key, user_type) in [("sendUserList",0),("receiveUserList",1)] {
                let users = body.get(key).and_then(Value::as_array).ok_or(Error::Business(202,"参训人员不能为空"))?;
                for (index, uid) in users.iter().enumerate() {
                    let uid = uid.as_str().filter(|id| !id.is_empty()).ok_or(Error::Business(202,"参训人员不正确"))?;
                    sqlx::query("INSERT INTO simulation_router_room_user (room_id,user_id,user_type,channel,user_status) VALUES (?,?,?,?,0)")
                        .bind(room).bind(uid).bind(user_type).bind(if kind == 0 { index as i32 } else { user_type }).execute(&mut *tx).await?;
                }
            }
        }
        Ok(match kind {
            0 => None,
            1 => Some(json!(room)),
            _ => Some(room_json(&lock_room(&mut tx, room).await?)),
        })
    }.await;
    complete(tx, result).await
}
pub(crate) async fn add_router(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    respond(add_room(&s, &u, b, 0).await)
}
pub(crate) async fn add_disturb(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    respond(add_room(&s, &u, b, 1).await)
}
pub(crate) async fn add_report(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    respond(add_room(&s, &u, b, 2).await)
}
pub(crate) async fn add_recept(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    respond(add_room(&s, &u, b, 3).await)
}

async fn list_type(state: &AppState, user: &AuthUser, kind: i32) -> Result<Option<Value>, Error> {
    let rows = sqlx::query("SELECT r.*,DATE_FORMAT(r.create_time,'%Y-%m-%d %H:%i:%s') create_time_text,DATE_FORMAT(r.start_time,'%Y-%m-%d %H:%i:%s') start_time_text,c.bd_type,c.bw_type,c.bw_count,c.content,c.main_signal,c.interference_signal,u.user_name,u.user_img FROM simulation_router_room r LEFT JOIN simulation_router_room_content c ON c.room_id=r.id LEFT JOIN t_user u ON u.id=r.create_user_Id WHERE r.room_type=? AND (?=1 OR EXISTS(SELECT 1 FROM simulation_router_room_user m WHERE m.room_id=r.id AND m.user_id=?) OR (?=0 AND r.create_user_Id=?)) ORDER BY r.create_time DESC,r.stats")
        .bind(kind).bind(kind).bind(&user.id).bind(kind).bind(&user.id).fetch_all(pool(state)?).await?;
    let items: Vec<Value> = rows
        .iter()
        .map(|r| {
            let mut item = room_json(r);
            content_json(&mut item, r);
            item["userName"] = json!(r.get::<Option<String>, _>("user_name"));
            item["userImg"] = json!(r.get::<Option<String>, _>("user_img"));
            item["existPageNumber"] = json!(0);
            if kind >= 2 {
                item["teacher"] = json!(false);
            }
            response_shape(item, kind, true)
        })
        .collect();
    Ok(Some(json!(items)))
}
pub(crate) async fn list_router(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
) -> Response {
    respond(list_type(&s, &u, 0).await)
}
pub(crate) async fn list_disturb(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
) -> Response {
    respond(list_type(&s, &u, 1).await)
}
pub(crate) async fn list_report(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
) -> Response {
    respond(list_type(&s, &u, 2).await)
}
pub(crate) async fn list_recept(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
) -> Response {
    respond(list_type(&s, &u, 3).await)
}

async fn get_detail(
    state: &AppState,
    user: &AuthUser,
    id: Option<i32>,
    content_view: bool,
) -> Result<Option<Value>, Error> {
    let id = required_id(id)?;
    let mut tx = pool(state)?.begin().await?;
    let result = async {
        let room = lock_room(&mut tx, id).await?;
        Ok(Some(detail_json(&mut tx, &room, user, content_view).await?))
    }
    .await;
    complete(tx, result).await
}
pub(crate) async fn detail(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<RoomQuery>,
) -> Response {
    respond(get_detail(&s, &u, q.room_id, false).await)
}
pub(crate) async fn detail_body(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    respond(get_detail(&s, &u, int(&b, "roomId"), true).await)
}
pub(crate) async fn users(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Path(id): Path<i32>,
) -> Response {
    respond(
        async {
            let id = required_id(Some(id))?;
            let mut tx = pool(&state)?.begin().await?;
            let result = async {
                let room = lock_room(&mut tx, id).await?;
                access(&mut tx, &room, &user).await?;
                Ok(Some(users_json(&mut tx, id).await?))
            }
            .await;
            complete(tx, result).await
        }
        .await,
    )
}
pub(crate) async fn users_body(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    respond(
        async {
            let id = body_room(&body)?;
            let mut tx = pool(&state)?.begin().await?;
            let result = async {
                let room = lock_room(&mut tx, id).await?;
                let (teacher, _) = access(&mut tx, &room, &user).await?;
                let mut items = Vec::new();
                for row in member_rows(&mut tx, id).await? {
                    if row.get::<Option<i32>, _>("user_type") != Some(1) {
                        continue;
                    }
                    let mut item = user_json(&row);
                    item["channel"] = json!(row.get::<Option<i32>, _>("channel"));
                    item["userStatus"] = json!(row.get::<Option<i32>, _>("user_status"));
                    item["contentValue"] =
                        json!(if teacher || row.get::<String, _>("user_id") == user.id {
                            row.get::<Option<String>, _>("content_value")
                        } else {
                            None
                        });
                    items.push(item);
                }
                Ok(Some(json!(items)))
            }
            .await;
            complete(tx, result).await
        }
        .await,
    )
}
pub(crate) async fn add_student(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    respond(async {
        let id = body_room(&body)?;
        let mut tx = pool(&state)?.begin().await?;
        let result = async {
            let room = lock_room(&mut tx,id).await?;
            if member(&mut tx,id,&user.id).await?.is_some() { return Ok(None); }
            mutable(&room)?;
            let row = sqlx::query("INSERT INTO simulation_router_room_user (room_id,user_id,user_type,channel,user_status) VALUES (?,?,1,?,0)")
                .bind(id).bind(&user.id).bind(if room.get::<Option<i32>,_>("room_type") == Some(1) { Some(-1) } else { None }).execute(&mut *tx).await?;
            Ok(Some(json!(row.last_insert_id())))
        }.await;
        complete(tx,result).await
    }.await)
}
pub(crate) async fn channels(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<RoomQuery>,
) -> Response {
    respond(async {
        let id = required_id(query.room_id)?;
        let mut tx = pool(&state)?.begin().await?;
        let result = async {
            let room = lock_room(&mut tx,id).await?;
            access(&mut tx,&room,&user).await?;
            let values: Vec<Option<i32>> = sqlx::query_scalar("SELECT channel FROM simulation_router_room_user WHERE room_id=? AND user_type=0 ORDER BY id")
                .bind(id).fetch_all(&mut *tx).await?;
            Ok(Some(json!(values)))
        }.await;
        complete(tx,result).await
    }.await)
}
pub(crate) async fn change_channel(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ChannelRequest>,
) -> Response {
    respond(
        async {
            let id = required_id(body.room_id)?;
            let uid = body
                .user_id
                .as_deref()
                .filter(|v| !v.is_empty())
                .ok_or(Error::Business(202, "请求参数错误"))?;
            let channel = body.channel.ok_or(Error::Business(202, "请求参数错误"))?;
            let mut tx = pool(&state)?.begin().await?;
            let result = async {
                let room = lock_room(&mut tx, id).await?;
                let target = member(&mut tx, id, uid)
                    .await?
                    .ok_or(Error::Business(202, "该人员不属于此训练"))?;
                let (teacher, _) = access(&mut tx, &room, &user).await?;
                let own_receiver = room.get::<Option<i32>, _>("room_type") == Some(0)
                    && uid == user.id
                    && target.get::<Option<i32>, _>("user_type") == Some(1);
                control(teacher || own_receiver)?;
                mutable(&room)?;
                sqlx::query("UPDATE simulation_router_room_user SET channel=? WHERE id=?")
                    .bind(channel)
                    .bind(target.get::<i32, _>("id"))
                    .execute(&mut *tx)
                    .await?;
                Ok(Some(users_json(&mut tx, id).await?))
            }
            .await;
            let result = complete(tx, result).await?;
            super::ws::set_simulation_channel(id, uid, channel).await;
            Ok(result)
        }
        .await,
    )
}
pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<RoomQuery>,
) -> Response {
    respond(async {
        let id = required_id(query.room_id)?;
        let mut tx = pool(&state)?.begin().await?;
        let result = async {
            let room = lock_room(&mut tx,id).await?;
            let actor = member(&mut tx,id,&user.id).await?.ok_or(Error::Business(207,"该用户不是发报人员"))?;
            if actor.get::<Option<i32>,_>("user_type") != Some(0) { return Err(Error::Business(207,"该用户不是发报人员")); }
            mutable(&room)?;
            sqlx::query("UPDATE simulation_router_room_user SET user_status=1 WHERE room_id=? AND (id=? OR (user_type=1 AND channel <=> ?))")
                .bind(id).bind(actor.get::<i32,_>("id")).bind(actor.get::<Option<i32>,_>("channel")).execute(&mut *tx).await?;
            Ok(Some(detail_json(&mut tx,&room,&user,false).await?))
        }.await;
        complete(tx,result).await
    }.await)
}
pub(crate) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<RoomQuery>,
) -> Response {
    respond(
        async {
            let id = required_id(query.room_id)?;
            let mut tx = pool(&state)?.begin().await?;
            let result = async {
                let room = lock_room(&mut tx, id).await?;
                let (teacher, _) = access(&mut tx, &room, &user).await?;
                control(teacher)?;
                for sql in [
                    "DELETE FROM simulation_router_room_page_value WHERE room_id=?",
                    "DELETE FROM simulation_router_room_page WHERE room_id=?",
                    "DELETE FROM simulation_router_room_user WHERE room_id=?",
                    "DELETE FROM simulation_router_room_content WHERE room_id=?",
                    "DELETE FROM simulation_router_room WHERE id=?",
                ] {
                    sqlx::query(sql).bind(id).execute(&mut *tx).await?;
                }
                Ok(Some(json!(true)))
            }
            .await;
            let result = complete(tx, result).await?;
            super::ws::close_simulation_room(id).await;
            Ok(result)
        }
        .await,
    )
}
pub(crate) async fn save_setting(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    respond(
        async {
            let id = body_room(&body)?;
            let setting = match body.get("setting") {
                None | Some(Value::Null) => None,
                Some(Value::String(value)) => Some(value.as_str()),
                _ => return Err(Error::Business(202, "配置必须为字符串")),
            };
            let mut tx = pool(&state)?.begin().await?;
            let result = async {
                let room = lock_room(&mut tx, id).await?;
                let (teacher, _) = access(&mut tx, &room, &user).await?;
                control(teacher)?;
                mutable(&room)?;
                sqlx::query("UPDATE simulation_router_room SET setting=? WHERE id=?")
                    .bind(setting)
                    .bind(id)
                    .execute(&mut *tx)
                    .await?;
                Ok(Some(json!({"roomId":id,"setting":setting})))
            }
            .await;
            complete(tx, result).await
        }
        .await,
    )
}
fn validate_answer(value: &str) -> Result<(), Error> {
    serde_json::from_str::<Vec<String>>(value)
        .map(|_| ())
        .map_err(|_| Error::Business(202, "答案页必须是字符串数组"))
}
pub(crate) async fn upload_result(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    respond(async {
        let id = body_room(&body)?;
        let pages = body.get("contentValue").and_then(Value::as_array).ok_or(Error::Business(202,"请提交完整答案数组"))?;
        for page in pages {
            validate_answer(page.as_str().ok_or(Error::Business(202,"答案页必须是字符串数组"))?)?;
        }
        let mut tx = pool(&state)?.begin().await?;
        let mut recipients = Vec::new();
        let result = async {
            let room = lock_room(&mut tx,id).await?;
            let actor = member(&mut tx,id,&user.id).await?.ok_or(Error::Business(207,"仅参训收报人员可提交答案"))?;
            if actor.get::<Option<i32>,_>("user_type") != Some(1) { return Err(Error::Business(207,"仅参训收报人员可提交答案")); }
            // Java intentionally accepts complete answer retries after playback has finished (stats=2).
            match room.get::<Option<i32>,_>("stats") {
                Some(1 | 2) => (),
                Some(status) if status > 2 => return Err(Error::Business(208,"训练已结束")),
                _ => return Err(Error::Business(202,"训练尚未开始，不能提交答案")),
            }
            let kind = room.get::<Option<i32>,_>("room_type").ok_or(Error::Business(202,"房间类型缺失"))?;
            if kind != 0 {
                recipients = sqlx::query_scalar::<_, String>("SELECT DISTINCT user_id FROM simulation_router_room_user WHERE room_id=? AND user_type=0 AND user_id IS NOT NULL")
                    .bind(id).fetch_all(&mut *tx).await?;
            }
            if let Some(creator) = room.get::<Option<String>,_>("create_user_Id") {
                if !recipients.contains(&creator) { recipients.push(creator); }
            }
            sqlx::query("DELETE FROM simulation_router_room_page_value WHERE room_id=? AND user_id=?").bind(id).bind(&user.id).execute(&mut *tx).await?;
            for (index,page) in pages.iter().enumerate() {
                sqlx::query("INSERT INTO simulation_router_room_page_value (page_number,room_id,value,user_id) VALUES (?,?,?,?)")
                    .bind((index + 1) as i32).bind(id).bind(page.as_str().expect("validated answer page")).bind(&user.id).execute(&mut *tx).await?;
            }
            sqlx::query("UPDATE simulation_router_room_user SET user_status=1 WHERE id=?").bind(actor.get::<i32,_>("id")).execute(&mut *tx).await?;
            Ok(Some(detail_json(&mut tx,&room,&user,true).await?))
        }.await;
        let result = complete(tx,result).await?;
        super::ws::notify_simulation_result(id,&user.id,&recipients).await;
        Ok(result)
    }.await)
}
pub(crate) async fn find_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<PageQuery>,
) -> Response {
    respond(async {
        let id = required_id(query.room_id)?;
        let page = query.page_number.unwrap_or(1);
        if page <= 0 { return Err(Error::Business(202,"页码不正确")); }
        let target = query.user_id.as_deref().unwrap_or(&user.id);
        let mut tx = pool(&state)?.begin().await?;
        let result = async {
            let room = lock_room(&mut tx,id).await?;
            let (teacher,_) = access(&mut tx,&room,&user).await?;
            if target != user.id && !teacher { return Err(Error::Business(207,"无权查看其他学员答案")); }
            if query.user_id.is_some() && room.get::<Option<String>,_>("create_user_Id").as_deref() != Some(target) && member(&mut tx,id,target).await?.is_none() {
                return Err(Error::Business(202,"该人员不属于此训练"));
            }
            let body = content(&mut tx,id).await?;
            let mut rows = sqlx::query("SELECT id,room_id,page_number,sort,`key` FROM simulation_router_room_page WHERE room_id=? AND page_number=? ORDER BY sort FOR UPDATE")
                .bind(id).bind(page).fetch_all(&mut *tx).await?;
            let total = body.get::<Option<i32>,_>("bw_count").ok_or(Error::Business(202,"报文组数缺失"))?;
            let remaining = i64::from(total) - (i64::from(page) - 1) * 100;
            if rows.is_empty() && remaining > 0 && room.get::<Option<i32>,_>("is_cable") == Some(0) {
                let kind = body.get::<Option<i32>,_>("bw_type").ok_or(Error::Business(202,"报文类型缺失"))?;
                let average = body.get::<Option<i32>,_>("bd_type").ok_or(Error::Business(202,"报底类型缺失"))? == 1;
                let random = body.get::<Option<i32>,_>("is_random").ok_or(Error::Business(202,"随机配置缺失"))? == 1;
                insert_groups(&mut tx,id,page,&generated_groups(remaining.min(100) as usize,page,kind,average,random)).await?;
                rows = sqlx::query("SELECT id,room_id,page_number,sort,`key` FROM simulation_router_room_page WHERE room_id=? AND page_number=? ORDER BY sort FOR UPDATE")
                    .bind(id).bind(page).fetch_all(&mut *tx).await?;
            }
            let saved: Option<Option<String>> = sqlx::query_scalar("SELECT value FROM simulation_router_room_page_value WHERE room_id=? AND page_number=? AND user_id=? FOR UPDATE")
                .bind(id).bind(page).bind(target).fetch_optional(&mut *tx).await?;
            let values = match saved.flatten() { Some(value) => serde_json::from_str::<Vec<String>>(&value)?, None => Vec::new() };
            let pages: Vec<Value> = rows.iter().map(|r| json!({"id":r.get::<String,_>("id"),"roomId":r.get::<i32,_>("room_id"),"pageNumber":r.get::<i32,_>("page_number"),"sort":r.get::<i32,_>("sort"),"key":r.get::<Option<String>,_>("key")})).collect();
            Ok(Some(json!({"pageVos":pages,"value":values})))
        }.await;
        complete(tx,result).await
    }.await)
}
pub(crate) async fn save_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<PageValue>,
) -> Response {
    respond(async {
        let id = required_id(body.room_id)?;
        let page = body.page_number.filter(|p| *p > 0).ok_or(Error::Business(202,"页码不正确"))?;
        let value = body.value.as_deref().ok_or(Error::Business(202,"答案页必须是字符串数组"))?;
        validate_answer(value)?;
        let mut tx = pool(&state)?.begin().await?;
        let result = async {
            let room = lock_room(&mut tx,id).await?;
            let actor = member(&mut tx,id,&user.id).await?.ok_or(Error::Business(207,"无权限"))?;
            if actor.get::<Option<i32>,_>("user_type") != Some(1) { return Err(Error::Business(207,"仅参训收报人员可保存答案")); }
            mutable(&room)?;
            if room.get::<Option<i32>,_>("stats") != Some(1) { return Err(Error::Business(202,"训练尚未开始，不能提交答案")); }
            if actor.get::<Option<i32>,_>("user_status") == Some(1) { return Err(Error::Business(208,"训练已完成")); }
            sqlx::query("INSERT INTO simulation_router_room_page_value (page_number,room_id,value,user_id) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE value=VALUES(value)")
                .bind(page).bind(id).bind(value).bind(&user.id).execute(&mut *tx).await?;
            Ok(None)
        }.await;
        complete(tx,result).await
    }.await)
}
