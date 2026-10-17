use super::classic::{current_tx, json_int};
use super::manager::MANAGER;
use super::{scalar, scalar_or_json, union_response};
use super::{Family, Outbound, Prepared, UnionMember, UnionRoom};
use crate::platform::state::AppState;
use serde_json::json;
use serde_json::Value;
use tokio::sync::mpsc;
use uuid::Uuid;

pub(super) async fn union_message(
    _state: &AppState,
    prepared: &Prepared,
    session_id: &str,
    text: &str,
) {
    let value: Value = match serde_json::from_str(text) {
        Ok(value) => value,
        Err(_) => {
            send_union_error(session_id, "消息格式不正确").await;
            return;
        }
    };
    let Some(object) = value.as_object() else {
        send_union_error(session_id, "消息格式不正确").await;
        return;
    };
    let Some(code) = json_int(object.get("code")).and_then(|code| i32::try_from(code).ok()) else {
        send_union_error(session_id, "消息格式不正确").await;
        return;
    };
    let send_user = scalar(object.get("sendUser")).unwrap_or_default();
    let receive_user = scalar(object.get("receiveUser")).unwrap_or_default();
    let data = scalar_or_json(object.get("data")).unwrap_or_default();
    if !matches!(
        code,
        0 | 1 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 20 | 112 | 182
    ) {
        // UnionConstants.getByCode maps unknown codes to UNKNOWN, which dispatch() ignores.
        return;
    }
    let tx = current_tx(prepared, session_id).await;
    let Some(tx) = tx else {
        return;
    };
    match code {
        0 => {
            let manager = MANAGER.lock().await;
            let users = serde_json::to_string(&manager.union_users()).unwrap_or_default();
            let rooms = serde_json::to_string(
                &manager
                    .union_rooms
                    .values()
                    .map(UnionRoom::json)
                    .collect::<Vec<_>>(),
            )
            .unwrap_or_default();
            let _ = tx.send(Outbound::Text(union_response(10, None, None, Some(&users))));
            let _ = tx.send(Outbound::Text(union_response(11, None, None, Some(&rooms))));
        }
        1 => {
            let manager = MANAGER.lock().await;
            let room = manager.union_room_json(&data);
            let _ = tx.send(Outbound::Text(union_response(
                1,
                None,
                None,
                room.map(|v| v.to_string()).as_deref(),
            )));
        }
        12 => {
            let Some(mut room) = parse_union_room(&data) else {
                let _ = tx.send(Outbound::Text(union_response(122, None, None, None)));
                return;
            };
            room.id = Uuid::new_v4().to_string();
            room.admin = prepared.user.id.clone();
            room.users = vec![prepared.user.clone().into()];
            let room_json = room.json().to_string();
            let mut manager = MANAGER.lock().await;
            manager.union_rooms.insert(room.id.clone(), room);
            let _ = tx.send(Outbound::Text(union_response(
                120,
                None,
                None,
                Some(&room_json),
            )));
            let update = union_response(121, None, None, Some(&room_json));
            for recipient in manager.union_recipients() {
                let _ = recipient.send(Outbound::Text(update.clone()));
            }
        }
        16 => {
            let Some(update) = parse_union_room(&data) else {
                let _ = tx.send(Outbound::Text(union_response(122, None, None, None)));
                return;
            };
            let mut manager = MANAGER.lock().await;
            let room_json = {
                let Some(room) = manager.union_rooms.get_mut(&update.id) else {
                    let _ = tx.send(Outbound::Text(union_response(122, None, None, None)));
                    return;
                };
                if room.admin != prepared.user.id {
                    let _ = tx.send(Outbound::Text(union_response(
                        -1,
                        None,
                        None,
                        Some("无权限"),
                    )));
                    return;
                }
                room.name = update.name;
                room.nnt = update.nnt;
                room.password = update.password;
                room.room_type = update.room_type;
                room.json().to_string()
            };
            for recipient in manager.union_recipients() {
                let _ = recipient.send(Outbound::Text(union_response(
                    121,
                    None,
                    None,
                    Some(&room_json),
                )));
            }
        }
        17 => {
            let room_id = send_user.clone();
            let Some(user_type) = data.parse::<i32>().ok() else {
                let _ = tx.send(Outbound::Text(union_response(
                    -1,
                    None,
                    None,
                    Some("更新房间用户信息处理失败"),
                )));
                return;
            };
            let mut manager = MANAGER.lock().await;
            let (room_json, users) = {
                let Some(room) = manager.union_rooms.get_mut(&room_id) else {
                    return;
                };
                if room.admin != prepared.user.id && receive_user != prepared.user.id {
                    let _ = tx.send(Outbound::Text(union_response(
                        -1,
                        None,
                        None,
                        Some("无权限"),
                    )));
                    return;
                }
                let Some(member) = room.users.iter_mut().find(|user| user.id == receive_user)
                else {
                    let _ = tx.send(Outbound::Text(union_response(
                        -1,
                        None,
                        None,
                        Some("房间成员不存在"),
                    )));
                    return;
                };
                member.user_type = user_type;
                (
                    room.json().to_string(),
                    room.users
                        .iter()
                        .map(|user| user.id.clone())
                        .collect::<Vec<_>>(),
                )
            };
            let payload = union_response(171, None, None, Some(&room_json));
            for user in users {
                if let Some(conn) = manager
                    .sessions
                    .values()
                    .find(|conn| conn.family == Family::Union && conn.user.id == user)
                {
                    let _ = conn.tx.send(Outbound::Text(payload.clone()));
                }
            }
        }
        13 => union_join(&tx, prepared, &data).await,
        14 => union_exit(prepared, &data).await,
        15 => union_remove(&tx, prepared, &data).await,
        20 => union_room_message(&tx, prepared, &send_user, &receive_user, &data).await,
        18 => union_seat_inspect(&tx, prepared, &send_user, &data).await,
        182 => union_seat_reply(&tx, prepared, &receive_user, &send_user, &data).await,
        112 => union_status_change(&tx, prepared, &receive_user, &data).await,
        _ => {}
    }
}

async fn send_union_error(session_id: &str, message: &str) {
    let manager = MANAGER.lock().await;
    if let Some(conn) = manager.sessions.get(session_id) {
        let _ = conn.tx.send(Outbound::Text(union_response(
            -1,
            None,
            None,
            Some(message),
        )));
    }
}

fn parse_union_room(data: &str) -> Option<UnionRoom> {
    let value: Value = serde_json::from_str(data).ok()?;
    let object = value.as_object()?;
    Some(UnionRoom {
        id: object
            .get("id")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .to_owned(),
        name: object
            .get("name")
            .and_then(Value::as_str)
            .map(ToOwned::to_owned),
        room_type: object.get("type").and_then(Value::as_i64).unwrap_or(0) as i32,
        status: object.get("status").and_then(Value::as_i64).unwrap_or(0) as i32,
        password: object
            .get("password")
            .and_then(Value::as_str)
            .map(ToOwned::to_owned),
        nnt: object.get("nnt").and_then(Value::as_i64).unwrap_or(8) as i32,
        admin: object
            .get("admin")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .to_owned(),
        users: Vec::new(),
    })
}

async fn union_join(tx: &mpsc::UnboundedSender<Outbound>, prepared: &Prepared, room_id: &str) {
    let mut manager = MANAGER.lock().await;
    let (room_json, recipients, event) = {
        let Some(room) = manager.union_rooms.get_mut(room_id) else {
            let _ = tx.send(Outbound::Text(union_response(
                131,
                None,
                None,
                Some("房间不存在"),
            )));
            return;
        };
        if room.users.iter().any(|user| user.id == prepared.user.id) {
            return;
        }
        let member = UnionMember::from(prepared.user.clone());
        let event = json!({"type":"join","user":&member}).to_string();
        room.users.push(member);
        (
            room.json().to_string(),
            room.users
                .iter()
                .filter(|user| user.id.as_str() != prepared.user.id.as_str())
                .map(|user| user.id.clone())
                .collect::<Vec<_>>(),
            event,
        )
    };
    let _ = tx.send(Outbound::Text(union_response(
        130,
        None,
        None,
        Some(&room_json),
    )));
    for recipient in recipients {
        if let Some(conn) = manager
            .sessions
            .values()
            .find(|conn| conn.family == Family::Union && conn.user.id == recipient)
        {
            let _ = conn.tx.send(Outbound::Text(union_response(
                111,
                None,
                None,
                Some(&event),
            )));
        }
    }
    for conn in manager.union_recipients() {
        let _ = conn.send(Outbound::Text(union_response(
            121,
            None,
            None,
            Some(&room_json),
        )));
    }
}

async fn union_exit(prepared: &Prepared, room_id: &str) {
    let mut manager = MANAGER.lock().await;
    let (removed, room_json, recipients, empty) = {
        let Some(room) = manager.union_rooms.get_mut(room_id) else {
            return;
        };
        let Some(index) = room
            .users
            .iter()
            .position(|user| user.id == prepared.user.id)
        else {
            return;
        };
        let removed = room.users.remove(index);
        (
            removed,
            room.json().to_string(),
            room.users
                .iter()
                .map(|user| user.id.clone())
                .collect::<Vec<_>>(),
            room.users.is_empty(),
        )
    };
    if empty {
        manager.union_rooms.remove(room_id);
    }
    for conn in manager.union_recipients() {
        let _ = conn.send(Outbound::Text(union_response(
            121,
            None,
            None,
            Some(&room_json),
        )));
    }
    let event = json!({"type":"exit","user":removed}).to_string();
    for recipient in recipients {
        if let Some(conn) = manager
            .sessions
            .values()
            .find(|conn| conn.family == Family::Union && conn.user.id == recipient)
        {
            let _ = conn.tx.send(Outbound::Text(union_response(
                111,
                None,
                None,
                Some(&event),
            )));
        }
    }
}

async fn union_remove(tx: &mpsc::UnboundedSender<Outbound>, prepared: &Prepared, room_id: &str) {
    let mut manager = MANAGER.lock().await;
    let (room_json, members) = {
        let Some(room) = manager.union_rooms.get(room_id) else {
            return;
        };
        if room.admin != prepared.user.id {
            let _ = tx.send(Outbound::Text(union_response(
                151,
                None,
                None,
                Some("无权限"),
            )));
            return;
        }
        (
            room.json().to_string(),
            room.users
                .iter()
                .map(|user| user.id.clone())
                .collect::<Vec<_>>(),
        )
    };
    manager.union_rooms.remove(room_id);
    for member in members {
        if let Some(conn) = manager
            .sessions
            .values()
            .find(|conn| conn.family == Family::Union && conn.user.id == member)
        {
            let _ = conn.tx.send(Outbound::Text(union_response(
                152,
                None,
                None,
                Some(&room_json),
            )));
        }
    }
    for conn in manager.union_recipients() {
        let rooms = serde_json::to_string(
            &manager
                .union_rooms
                .values()
                .map(UnionRoom::json)
                .collect::<Vec<_>>(),
        )
        .unwrap_or_default();
        let _ = conn.send(Outbound::Text(union_response(11, None, None, Some(&rooms))));
    }
}

async fn union_room_message(
    tx: &mpsc::UnboundedSender<Outbound>,
    prepared: &Prepared,
    sender: &str,
    room_id: &str,
    data: &str,
) {
    if sender != prepared.user.id.as_str() {
        let _ = tx.send(Outbound::Text(union_response(
            -1,
            None,
            None,
            Some("无权限"),
        )));
        return;
    }
    let manager = MANAGER.lock().await;
    let Some(room) = manager.union_rooms.get(room_id) else {
        return;
    };
    if !room
        .users
        .iter()
        .any(|member| member.id == prepared.user.id)
    {
        let _ = tx.send(Outbound::Text(union_response(
            -1,
            None,
            None,
            Some("无权限"),
        )));
        return;
    }
    let payload =
        json!({"data":data,"userName":prepared.user.name,"userImg":prepared.user.user_img})
            .to_string();
    for user in &room.users {
        if user.id != prepared.user.id {
            if let Some(conn) = manager
                .sessions
                .values()
                .find(|conn| conn.family == Family::Union && conn.user.id == user.id)
            {
                let _ = conn.tx.send(Outbound::Text(union_response(
                    20,
                    Some(sender),
                    Some(room_id),
                    Some(&payload),
                )));
            }
        }
    }
}

async fn union_seat_inspect(
    tx: &mpsc::UnboundedSender<Outbound>,
    prepared: &Prepared,
    room_id: &str,
    data: &str,
) {
    let mut manager = MANAGER.lock().await;
    let (room_json, recipients) = {
        let Some(room) = manager.union_rooms.get_mut(room_id) else {
            return;
        };
        if room.admin != prepared.user.id {
            let _ = tx.send(Outbound::Text(union_response(
                -1,
                None,
                None,
                Some("无权限"),
            )));
            return;
        }
        let Ok(status) = data.parse::<i32>() else {
            let _ = tx.send(Outbound::Text(union_response(
                -1,
                None,
                None,
                Some("发起席位状态检测处理失败"),
            )));
            return;
        };
        room.status = status;
        for member in &mut room.users {
            member.status = 0;
        }
        (
            room.json().to_string(),
            room.users
                .iter()
                .map(|user| user.id.clone())
                .collect::<Vec<_>>(),
        )
    };
    for user in recipients {
        if let Some(conn) = manager
            .sessions
            .values()
            .find(|conn| conn.family == Family::Union && conn.user.id == user)
        {
            let _ = conn.tx.send(Outbound::Text(union_response(
                171,
                None,
                None,
                Some(&room_json),
            )));
            let _ = conn
                .tx
                .send(Outbound::Text(union_response(181, None, None, Some(data))));
        }
    }
}

async fn union_seat_reply(
    tx: &mpsc::UnboundedSender<Outbound>,
    prepared: &Prepared,
    room_id: &str,
    sender: &str,
    data: &str,
) {
    if sender != prepared.user.id.as_str() {
        let _ = tx.send(Outbound::Text(union_response(
            -1,
            None,
            None,
            Some("无权限"),
        )));
        return;
    }
    let Ok(status) = data.parse::<i32>() else {
        let _ = tx.send(Outbound::Text(union_response(
            -1,
            None,
            None,
            Some("席位状态回执处理失败"),
        )));
        return;
    };
    let mut manager = MANAGER.lock().await;
    let Some(room) = manager.union_rooms.get_mut(room_id) else {
        return;
    };
    let Some(member) = room
        .users
        .iter_mut()
        .find(|member| member.id == prepared.user.id)
    else {
        let _ = tx.send(Outbound::Text(union_response(
            -1,
            None,
            None,
            Some("无权限"),
        )));
        return;
    };
    member.status = status;
    let event = json!({"userId":sender,"status":data}).to_string();
    let recipients = room
        .users
        .iter()
        .map(|user| user.id.clone())
        .collect::<Vec<_>>();
    for user in recipients {
        if let Some(conn) = manager
            .sessions
            .values()
            .find(|conn| conn.family == Family::Union && conn.user.id == user)
        {
            let _ = conn.tx.send(Outbound::Text(union_response(
                183,
                None,
                None,
                Some(&event),
            )));
        }
    }
}

async fn union_status_change(
    tx: &mpsc::UnboundedSender<Outbound>,
    prepared: &Prepared,
    room_id: &str,
    data: &str,
) {
    let mut manager = MANAGER.lock().await;
    let room_json = {
        let Some(room) = manager.union_rooms.get_mut(room_id) else {
            return;
        };
        if room.admin != prepared.user.id {
            let _ = tx.send(Outbound::Text(union_response(
                -1,
                None,
                None,
                Some("无权限"),
            )));
            return;
        }
        let Ok(status) = data.parse::<i32>() else {
            let _ = tx.send(Outbound::Text(union_response(
                -1,
                None,
                None,
                Some("改变房间状态处理失败"),
            )));
            return;
        };
        room.status = status;
        room.json().to_string()
    };
    for conn in manager.union_recipients() {
        let _ = conn.send(Outbound::Text(union_response(
            121,
            None,
            None,
            Some(&room_json),
        )));
    }
}
