use super::manager::{WsManager, MANAGER};
use super::{
    general_notification_response, sim_response, Connection, Family, GeneralKind, GeneralRoomKey,
    Outbound,
};
use crate::platform::envelope::envelope_json;
use axum::http::StatusCode;
use axum::response::Response;
use serde_json::json;
use serde_json::Map;
use serde_json::Value;
use std::collections::HashMap;

pub(crate) async fn send_classic(user_id: &str, code: i32, data: Option<String>) {
    // Gson omits the unset ResponseModel fields, including data on completion.
    let mut message = Map::new();
    message.insert("code".to_owned(), json!(code));
    if let Some(data) = data {
        message.insert("data".to_owned(), Value::String(data));
    }
    let message = Value::Object(message).to_string();
    let key = format!("classic:{user_id}");
    let manager = MANAGER.lock().await;
    let Some(connection) = manager
        .current_by_key
        .get(&key)
        .and_then(|session_id| manager.sessions.get(session_id))
    else {
        return;
    };
    if let Err(error) = connection.tx.send(Outbound::Text(message)) {
        tracing::warn!(%error, user_id, code, "classic websocket outbound queue is closed");
    }
}

pub(crate) async fn send_classic_map(user_id: &str, code: i32, mut map: Value) {
    // Gson omits null object fields, including entity fields inside ResponseModel.map.
    fn omit_null_fields(value: &mut Value) {
        match value {
            Value::Object(object) => object.retain(|_, value| {
                if value.is_null() {
                    return false;
                }
                omit_null_fields(value);
                true
            }),
            Value::Array(items) => items.iter_mut().for_each(omit_null_fields),
            _ => {}
        }
    }
    omit_null_fields(&mut map);
    let mut message = Map::new();
    message.insert("code".to_owned(), json!(code));
    message.insert("map".to_owned(), map);
    let message = Value::Object(message).to_string();
    let key = format!("classic:{user_id}");
    let manager = MANAGER.lock().await;
    let Some(connection) = manager
        .current_by_key
        .get(&key)
        .and_then(|session_id| manager.sessions.get(session_id))
    else {
        tracing::debug!(user_id, code, "classic websocket recipient is offline");
        return;
    };
    if let Err(error) = connection.tx.send(Outbound::Text(message)) {
        tracing::warn!(%error, user_id, code, "classic websocket map outbound queue is closed");
    }
}

pub(super) fn parse_general_kind(value: &str) -> Option<GeneralKind> {
    match value {
        "key" => Some(GeneralKind::Key),
        "telex" => Some(GeneralKind::Telex),
        "ticker" => Some(GeneralKind::Ticker),
        _ => None,
    }
}

/// Returns the current protocol status for a connected general-training user.
/// A missing connection is deliberately represented as `None`; REST callers can
/// map it to their existing offline/default status without inspecting session state.
pub(crate) async fn general_connection_status(
    kind: &str,
    train_id: &str,
    user_id: &str,
) -> Option<i32> {
    let kind = parse_general_kind(kind)?;
    let manager = MANAGER.lock().await;
    let key = GeneralRoomKey {
        kind,
        train_id: train_id.to_owned(),
    };
    manager
        .general_rooms
        .get(&key)?
        .iter()
        .find_map(|session_id| {
            manager
                .sessions
                .get(session_id)
                .filter(|conn| conn.user.id == user_id)
                .map(|conn| conn.status)
        })
}

pub(crate) async fn general_ticker_connection_status(train_id: i32, user_id: &str) -> i32 {
    general_connection_status("ticker", &train_id.to_string(), user_id)
        .await
        .unwrap_or(0)
}
pub(crate) async fn general_online_users(kind: &str, train_id: &str) -> Vec<Value> {
    let Some(kind) = parse_general_kind(kind) else {
        return Vec::new();
    };
    let manager = MANAGER.lock().await;
    let key = GeneralRoomKey {
        kind,
        train_id: train_id.to_owned(),
    };
    general_users_for_key(&manager, &key)
}
pub(crate) async fn general_online_rooms(kind: &str) -> HashMap<String, Vec<Value>> {
    let Some(kind) = parse_general_kind(kind) else {
        return HashMap::new();
    };
    let manager = MANAGER.lock().await;
    manager
        .general_rooms
        .iter()
        .filter(|(key, _)| key.kind == kind)
        .map(|(key, _)| (key.train_id.clone(), general_users_for_key(&manager, key)))
        .collect()
}

fn general_users_for_key(manager: &WsManager, key: &GeneralRoomKey) -> Vec<Value> {
    let mut connections: Vec<&Connection> = manager
        .general_rooms
        .get(key)
        .into_iter()
        .flat_map(|session_ids| session_ids.iter())
        .filter_map(|session_id| manager.sessions.get(session_id))
        .collect();
    if matches!(key.kind, GeneralKind::Key | GeneralKind::Telex) {
        // Java `getOneline` (electronic key and telex): students in join order, the organizer last.
        connections.sort_by_key(|connection| connection.role == Some(1));
    }
    connections
        .into_iter()
        .map(|connection| {
            json!({
                "id": connection.user.id,
                "userName": connection.user.name,
                "userImg": connection.user.user_img,
                "role": connection.role,
                "status": connection.status
            })
        })
        .collect()
}

pub(crate) async fn simulation_connection_status(room_id: i32, user_id: &str) -> Option<i32> {
    let manager = MANAGER.lock().await;
    let room = manager.simulation_rooms.get(&room_id)?;
    room.members.iter().find_map(|session_id| {
        manager
            .sessions
            .get(session_id)
            .filter(|connection| connection.user.id == user_id)
            .and_then(|connection| connection.sim_member.as_ref().map(|member| member.status))
    })
}
pub(crate) async fn simulation_all_room_info() -> Response {
    let manager = MANAGER.lock().await;
    let mut router_rooms = Vec::new();
    let mut disturb_rooms = Vec::new();
    let mut report_rooms = Vec::new();
    for (room_id, room) in &manager.simulation_rooms {
        let users = room
            .members
            .iter()
            .filter_map(|session_id| {
                manager.sessions.get(session_id).and_then(|connection| {
                    connection.sim_member.as_ref().map(|member| {
                        json!({
                            "id": connection.user.id,
                            "name": connection.user.name,
                            "userImg": connection.user.user_img,
                            "channel": member.channel,
                            "userType": member.user_type,
                            "status": member.status
                        })
                    })
                })
            })
            .collect::<Vec<_>>();
        let mut entry = serde_json::Map::new();
        entry.insert(room_id.to_string(), Value::Array(users));
        let entry = Value::Object(entry);
        match room.room_type {
            0 => router_rooms.push(entry),
            1 => disturb_rooms.push(entry),
            2 | 3 => report_rooms.push(entry),
            _ => {}
        }
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"线路通报":router_rooms,"快速/干扰通报":disturb_rooms,"通报教学":report_rooms}),
        ),
        "ok",
        "",
    )
}

pub(crate) async fn set_simulation_channel(room_id: i32, user_id: &str, channel: i32) {
    let mut manager = MANAGER.lock().await;
    let WsManager {
        simulation_rooms,
        sessions,
        ..
    } = &mut *manager;
    let Some(room) = simulation_rooms.get(&room_id) else {
        return;
    };
    for session_id in &room.members {
        let Some(connection) = sessions.get_mut(session_id) else {
            continue;
        };
        if connection.user.id != user_id {
            continue;
        }
        if let Some(member) = connection.sim_member.as_mut() {
            member.channel = Some(channel);
        }
    }
}

pub(crate) async fn close_simulation_room(room_id: i32) {
    let mut manager = MANAGER.lock().await;
    let Some(room) = manager.simulation_rooms.remove(&room_id) else {
        return;
    };
    for session_id in room.members {
        if let Some(connection) = manager.sessions.get(&session_id) {
            if connection.tx.send(Outbound::Close).is_err() {
                tracing::warn!(room_id, session_id, "simulation close queue is closed");
            }
        }
    }
}
/// Remove every live session in a general-training room and ask each socket to close.
///
/// The manager owns session generations, so removing the session before sending the
/// close frame also prevents a late disconnect from touching a replacement session.
pub(crate) async fn close_general_room(kind: &str, train_id: &str) {
    let Some(kind) = parse_general_kind(kind) else {
        return;
    };
    let key = GeneralRoomKey {
        kind,
        train_id: train_id.to_owned(),
    };
    let mut manager = MANAGER.lock().await;
    let Some(session_ids) = manager.general_rooms.remove(&key) else {
        return;
    };
    for session_id in session_ids {
        let Some(connection) = manager.sessions.remove(&session_id) else {
            continue;
        };
        if manager
            .current_by_key
            .get(&connection.key)
            .is_some_and(|current| current == &session_id)
        {
            manager.current_by_key.remove(&connection.key);
        }
        if connection.tx.send(Outbound::Close).is_err() {
            tracing::warn!(kind = ?kind, train_id, session_id, "general room close queue is closed");
        }
    }
}

/// Notify invited users after a general-training creation transaction commits.
/// Java's WebSocketService.sendInfo only reaches the classic `/websocket/{sid}` session,
/// so room sockets (which `JSON.parse` every frame as a room message) never see it.
/// The payload intentionally uses the legacy ResponseModel `map` field.
pub(crate) async fn notify_general_new_train(
    kind: &str,
    train_id: &str,
    title: &str,
    recipients: &[String],
) {
    let Some(kind) = parse_general_kind(kind) else {
        return;
    };
    let recipient_ids: std::collections::HashSet<&str> =
        recipients.iter().map(String::as_str).collect();
    if recipient_ids.is_empty() {
        return;
    }
    let train_id_value = train_id
        .parse::<i64>()
        .map(|value| json!(value))
        .unwrap_or_else(|_| json!(train_id));
    let message = general_notification_response(
        200,
        json!({"type": kind.protocol_name(), "id": train_id_value, "title": title}),
    );
    let manager = MANAGER.lock().await;
    for connection in manager.sessions.values().filter(|connection| {
        connection.family == Family::Classic && recipient_ids.contains(connection.user.id.as_str())
    }) {
        if connection.tx.send(Outbound::Text(message.clone())).is_err() {
            tracing::warn!(kind = ?kind, train_id, user_id = %connection.user.id, "general new-train notification queue is closed");
        }
    }
}

/// Notify role-1 room members after a participant has been settled.
/// Recipients are supplied from the committed membership rows, rather than from
/// live room state, so offline role-1 users retain the same addressing contract.
pub(crate) async fn notify_general_result(
    kind: &str,
    train_id: &str,
    user_id: &str,
    recipients: &[String],
) {
    let Some(kind) = parse_general_kind(kind) else {
        return;
    };
    let recipient_ids: std::collections::HashSet<&str> =
        recipients.iter().map(String::as_str).collect();
    if recipient_ids.is_empty() {
        return;
    }
    let train_id_value = train_id
        .parse::<i64>()
        .map(|value| json!(value))
        .unwrap_or_else(|_| json!(train_id));
    let message = general_notification_response(
        201,
        json!({"type": kind.protocol_name(), "userId": user_id, "trainId": train_id_value}),
    );
    let manager = MANAGER.lock().await;
    for connection in manager.sessions.values().filter(|connection| {
        connection.family == Family::Classic && recipient_ids.contains(connection.user.id.as_str())
    }) {
        if connection.tx.send(Outbound::Text(message.clone())).is_err() {
            tracing::warn!(kind = ?kind, train_id, user_id, recipient = %connection.user.id, "general result notification queue is closed");
        }
    }
}

pub(crate) async fn notify_simulation_result(room_id: i32, user_id: &str, recipients: &[String]) {
    let manager = MANAGER.lock().await;
    // Java SimulationResultNotifier sends through sendMessage: the notice is the envelope's `data`.
    let notice =
        json!({"type":"result","topic":"result","roomId":room_id,"userId":user_id}).to_string();
    let payload = sim_response(1, &notice, "", "");
    for target in recipients {
        for connection in manager.sessions.values().filter(|connection| {
            connection.sim_room_id == Some(room_id) && connection.user.id == *target
        }) {
            if connection.tx.send(Outbound::Text(payload.clone())).is_err() {
                tracing::warn!(
                    room_id,
                    user_id,
                    target,
                    "simulation result queue is closed"
                );
            }
        }
    }
}
