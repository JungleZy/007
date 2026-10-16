use super::classic::json_int;
use super::dispatch::send_family_error;
use super::manager::{SimClose, MANAGER};
use super::{sim_response, value_text};
use super::{Family, Outbound, Prepared, SimMember};
use crate::platform::state::AppState;
use serde_json::json;
use serde_json::Map;
use serde_json::Value;
use sqlx::Row;
use tokio::sync::mpsc;

pub(super) async fn simulation_message(
    state: &AppState,
    prepared: &Prepared,
    session_id: &str,
    text: &str,
) {
    let Some(room_id) = prepared.sim_room_id else {
        return;
    };
    let Some(pool) = &state.pool else {
        tracing::error!(room_id, session_id, "simulation database unavailable");
        send_session_sim_system_error(session_id).await;
        return;
    };
    let room_row = match sqlx::query(
        "SELECT room_type, create_user_id FROM simulation_router_room WHERE id=? LIMIT 1",
    )
    .bind(room_id)
    .fetch_optional(pool)
    .await
    {
        Ok(row) => row,
        Err(error) => {
            tracing::error!(%error, room_id, session_id, "simulation room lookup failed");
            send_session_sim_system_error(session_id).await;
            return;
        }
    };
    let Some(room_row) = room_row else {
        send_family_error(session_id, Family::Simulation, "房间不存在").await;
        return;
    };
    let room_type = room_row.get::<Option<i32>, _>("room_type").unwrap_or(-1);
    let parsed: Value = match serde_json::from_str(text) {
        Ok(value) => value,
        Err(_) => {
            send_family_error(session_id, Family::Simulation, "消息格式错误").await;
            return;
        }
    };
    let Some(object) = parsed.as_object() else {
        send_family_error(session_id, Family::Simulation, "消息格式错误").await;
        return;
    };
    let Some(topic) = object
        .get("topic")
        .or_else(|| object.get("type"))
        .map(value_text)
    else {
        send_family_error(session_id, Family::Simulation, "消息格式错误").await;
        return;
    };
    let Some((sender, everyone)) = ({
        let manager = MANAGER.lock().await;
        manager.sim_snapshot(room_id, session_id)
    }) else {
        return;
    };
    // Java excludes the sender (by user id) for some topics and includes it for others: handlers
    // that broadcast to the whole room take `everyone`, the rest take `recipients`.
    let recipients: Vec<_> = everyone
        .iter()
        .filter(|(_, member)| member.user.id != sender.user.id)
        .cloned()
        .collect();
    if room_type == 1 {
        simulation_disturb(
            state,
            room_id,
            session_id,
            &sender,
            &everyone,
            &recipients,
            &topic,
            object,
            text,
        )
        .await;
    } else if room_type == 2 || room_type == 3 {
        simulation_report(
            state,
            room_id,
            session_id,
            &sender,
            &recipients,
            &topic,
            object,
            text,
        )
        .await;
    } else if room_type == 0 {
        simulation_router(
            state,
            room_id,
            session_id,
            &sender,
            &everyone,
            &recipients,
            &topic,
            text,
        )
        .await;
    } else {
        send_family_error(session_id, Family::Simulation, "消息格式错误").await;
    }
}

async fn simulation_disturb(
    state: &AppState,
    room_id: i32,
    session_id: &str,
    sender: &SimMember,
    everyone: &[(mpsc::UnboundedSender<Outbound>, SimMember)],
    recipients: &[(mpsc::UnboundedSender<Outbound>, SimMember)],
    topic: &str,
    object: &Map<String, Value>,
    text: &str,
) {
    if matches!(topic, "0" | "1" | "2" | "3") {
        let mut output = object.clone();
        output.insert("topic".to_owned(), Value::String(topic.to_owned()));
        let output = Value::Object(output).to_string();
        let channel = topic.parse::<i32>().ok();
        // Java sends these to the whole room, the sender included.
        for (tx, member) in everyone {
            if topic == "0" || member.channel == channel {
                let _ = tx.send(Outbound::Text(sim_response(1, &output, "", "")));
            }
        }
    } else if topic == "begin" || topic == "end" {
        let mut output = object.clone();
        output.insert("topic".to_owned(), json!(0));
        let output = Value::Object(output).to_string();
        if let Some(pool) = &state.pool {
            if topic == "begin" {
                if let Err(error) =
                    sqlx::query("UPDATE simulation_router_room SET stats=1 WHERE id=?")
                        .bind(room_id)
                        .execute(pool)
                        .await
                {
                    tracing::error!(%error, room_id, session_id, operation = "begin", "simulation update failed");
                    send_session_sim_system_error(session_id).await;
                    return;
                }
            } else {
                let total = object
                    .get("body")
                    .and_then(|body| body.get("totalTime"))
                    .and_then(Value::as_i64);
                if let Err(error) =
                    sqlx::query("UPDATE simulation_router_room SET stats=2,total_time=? WHERE id=?")
                        .bind(total)
                        .bind(room_id)
                        .execute(pool)
                        .await
                {
                    tracing::error!(%error, room_id, session_id, operation = "end", "simulation update failed");
                    send_session_sim_system_error(session_id).await;
                    return;
                }
            }
        }
        for (tx, member) in recipients {
            if member.user.id != sender.user.id {
                let _ = tx.send(Outbound::Text(sim_response(1, &output, "", "")));
            }
        }
    } else if topic == "select" {
        let Some(channel) = object
            .get("body")
            .and_then(|body| body.get("road"))
            .and_then(|value| json_int(Some(value)))
            .and_then(|v| i32::try_from(v).ok())
        else {
            send_session_sim_error(session_id, "消息格式错误").await;
            return;
        };
        if let Some(pool) = &state.pool {
            // Java only acts when the sender has a member row (e.g. a room creator without one:
            // no database change, no broadcast, no session channel change).
            match sqlx::query_scalar::<_, i32>(
                "SELECT id FROM simulation_router_room_user WHERE room_id=? AND user_id=? LIMIT 1",
            )
            .bind(room_id)
            .bind(&sender.user.id)
            .fetch_optional(pool)
            .await
            {
                Ok(Some(_)) => {}
                Ok(None) => return,
                Err(error) => {
                    tracing::error!(%error, room_id, session_id, operation = "select member", "simulation lookup failed");
                    send_session_sim_system_error(session_id).await;
                    return;
                }
            }
            if let Err(error) = sqlx::query(
                "UPDATE simulation_router_room_user SET channel=? WHERE room_id=? AND user_id=?",
            )
            .bind(channel)
            .bind(room_id)
            .bind(&sender.user.id)
            .execute(pool)
            .await
            {
                tracing::error!(%error, room_id, session_id, operation = "select channel", "simulation update failed");
                send_session_sim_system_error(session_id).await;
                return;
            }
        }
        // Java updates the in-memory channel only for a sender that is not a userType 0 member
        // (its broadcast branch comes first); the database row changes either way.
        if sender.user_type != Some(0) {
            let mut manager = MANAGER.lock().await;
            manager.set_sim_channel(session_id, channel);
        }
        for (tx, member) in everyone {
            if member.user_type == Some(0) {
                let _ = tx.send(Outbound::Text(sim_response(1, text, "", "")));
            }
        }
    }
    // Java ignores every other disturb topic silently (result included).
}

async fn simulation_report(
    state: &AppState,
    room_id: i32,
    session_id: &str,
    sender: &SimMember,
    recipients: &[(mpsc::UnboundedSender<Outbound>, SimMember)],
    topic: &str,
    object: &Map<String, Value>,
    text: &str,
) {
    let control = matches!(topic, "1" | "2" | "3" | "4");
    let Some(pool) = &state.pool else {
        tracing::error!(
            room_id,
            session_id,
            "simulation report database unavailable"
        );
        send_session_sim_system_error(session_id).await;
        return;
    };
    let room =
        match sqlx::query("SELECT create_user_id FROM simulation_router_room WHERE id=? LIMIT 1")
            .bind(room_id)
            .fetch_optional(pool)
            .await
        {
            Ok(room) => room,
            Err(error) => {
                tracing::error!(%error, room_id, session_id, "simulation organizer lookup failed");
                send_session_sim_system_error(session_id).await;
                return;
            }
        };
    let organizer = room
        .and_then(|row| row.get::<Option<String>, _>("create_user_id"))
        .is_some_and(|id| id == sender.user.id)
        || sender.user_type == Some(0);
    if control && !organizer {
        send_session_sim_error(session_id, "无权控制该训练").await;
        return;
    }
    if !control && topic != "online" && topic != "result" {
        send_session_sim_error(session_id, "消息格式错误").await;
        return;
    }
    if topic == "1" {
        if let Some(pool) = &state.pool {
            // Java updateStatsToGoing: stats=1 and play_status=1 (topics 1 and 3).
            if let Err(error) =
                sqlx::query("UPDATE simulation_router_room SET stats=1,play_status=1 WHERE id=?")
                    .bind(room_id)
                    .execute(pool)
                    .await
            {
                tracing::error!(%error, room_id, session_id, operation = "start report", "simulation update failed");
                send_session_sim_system_error(session_id).await;
                return;
            }
        }
    } else if topic == "2" || topic == "3" {
        if let Some(pool) = &state.pool {
            let sql = if topic == "2" {
                "UPDATE simulation_router_room SET play_status=0 WHERE id=?"
            } else {
                "UPDATE simulation_router_room SET stats=1,play_status=1 WHERE id=?"
            };
            if let Err(error) = sqlx::query(sql).bind(room_id).execute(pool).await {
                tracing::error!(%error, room_id, session_id, operation = "set report playback", "simulation update failed");
                send_session_sim_system_error(session_id).await;
                return;
            }
        }
    } else if topic == "4" {
        if let Some(pool) = &state.pool {
            let count = object.get("count").and_then(|value| json_int(Some(value)));
            if let Err(error) =
                sqlx::query("UPDATE simulation_router_room SET stats=2,total_time=? WHERE id=?")
                    .bind(count)
                    .bind(room_id)
                    .execute(pool)
                    .await
            {
                tracing::error!(%error, room_id, session_id, operation = "end report", "simulation update failed");
                send_session_sim_system_error(session_id).await;
                return;
            }
        }
    } else if topic == "online" {
        {
            let mut manager = MANAGER.lock().await;
            manager.set_sim_channel(session_id, sender.channel.unwrap_or(-1));
        }
        let mut output = object.clone();
        output.insert("id".to_owned(), Value::String(sender.user.id.clone()));
        let output = Value::Object(output).to_string();
        for (tx, member) in recipients {
            if member.user_type == Some(0) {
                let _ = tx.send(Outbound::Text(sim_response(1, &output, "", "")));
            }
        }
        {
            let mut manager = MANAGER.lock().await;
            if let Some(conn) = manager.sessions.get_mut(session_id) {
                if let Some(member) = conn.sim_member.as_mut() {
                    member.status = 2;
                }
            }
        }
        return;
    } else if topic == "result" {
        return;
    }
    for (tx, _member) in recipients {
        let _ = tx.send(Outbound::Text(sim_response(1, text, "", "")));
    }
}

async fn simulation_router(
    state: &AppState,
    room_id: i32,
    session_id: &str,
    sender: &SimMember,
    everyone: &[(mpsc::UnboundedSender<Outbound>, SimMember)],
    recipients: &[(mpsc::UnboundedSender<Outbound>, SimMember)],
    topic: &str,
    text: &str,
) {
    match topic {
        "ready" => {
            {
                let mut manager = MANAGER.lock().await;
                if let Some(conn) = manager.sessions.get_mut(session_id) {
                    conn.status = 2;
                    if let Some(member) = conn.sim_member.as_mut() {
                        member.status = 2;
                    }
                }
            }
            for (tx, _) in recipients {
                let _ = tx.send(Outbound::Text(sim_response(1, text, "", "")));
            }
        }
        "play" => {
            for (tx, member) in recipients {
                // Java: a participant without a channel reaches nobody.
                if sender.user_type.is_none()
                    || (sender.channel.is_some() && member.channel == sender.channel)
                {
                    let _ = tx.send(Outbound::Text(sim_response(
                        1,
                        text,
                        &sender.user.name.clone().unwrap_or_default(),
                        &member.user.name.clone().unwrap_or_default(),
                    )));
                }
            }
        }
        "begin" => {
            if let Some(pool) = &state.pool {
                if let Err(error) = sqlx::query(
                    "UPDATE simulation_router_room SET stats=1,start_time=NOW() WHERE id=?",
                )
                .bind(room_id)
                .execute(pool)
                .await
                {
                    tracing::error!(%error, room_id, session_id, operation = "begin router", "simulation update failed");
                    send_session_sim_system_error(session_id).await;
                    return;
                }
            }
            for (tx, member) in everyone {
                if member.user_type.is_some() {
                    let _ = tx.send(Outbound::Text(sim_response(1, text, "", "")));
                }
            }
        }
        "end" => {
            if let Some(pool) = &state.pool {
                // Java: total_time = now - start_time, then start_time is reset to now; a room
                // that never began has no start_time and the whole message is rejected.
                let started = sqlx::query_scalar::<_, Option<String>>(
                    "SELECT CAST(start_time AS CHAR) FROM simulation_router_room WHERE id=?",
                )
                .bind(room_id)
                .fetch_optional(pool)
                .await;
                match started {
                    Ok(Some(None)) => {
                        send_session_sim_error(session_id, "消息格式错误").await;
                        return;
                    }
                    Ok(_) => {}
                    Err(error) => {
                        tracing::error!(%error, room_id, session_id, operation = "end router", "simulation lookup failed");
                        send_session_sim_system_error(session_id).await;
                        return;
                    }
                }
                if let Err(error) = sqlx::query(
                    "UPDATE simulation_router_room SET stats=2,total_time=TIMESTAMPDIFF(SECOND,start_time,NOW()),start_time=NOW() WHERE id=?",
                )
                .bind(room_id)
                .execute(pool)
                .await
                {
                    tracing::error!(%error, room_id, session_id, operation = "end router", "simulation update failed");
                    send_session_sim_system_error(session_id).await;
                    return;
                }
            }
            for (tx, member) in everyone {
                if member.user_type.is_some() {
                    let _ = tx.send(Outbound::Text(sim_response(1, text, "", "")));
                }
            }
        }
        "over" | "change" => {
            for (tx, _) in everyone {
                let _ = tx.send(Outbound::Text(sim_response(1, text, "", "")));
            }
        }
        // Java only logs unknown router topics.
        _ => {}
    }
}

async fn send_session_sim_system_error(session_id: &str) {
    let manager = MANAGER.lock().await;
    if let Some(conn) = manager.sessions.get(session_id) {
        if conn
            .tx
            .send(Outbound::Text(sim_response(
                500,
                "服务器错误",
                &conn.user.id,
                &conn.user.id,
            )))
            .is_err()
        {
            tracing::warn!(session_id, "failed to enqueue simulation system error");
        }
    }
}
async fn send_session_sim_error(session_id: &str, message: &str) {
    let manager = MANAGER.lock().await;
    if let Some(conn) = manager.sessions.get(session_id) {
        if conn
            .tx
            .send(Outbound::Text(sim_response(
                -1,
                message,
                &conn.user.id,
                &conn.user.id,
            )))
            .is_err()
        {
            tracing::warn!(session_id, "failed to enqueue simulation error");
        }
    }
}

pub(super) async fn simulation_close(state: &AppState, close: SimClose) {
    let member = close.member;
    let mut recipients = Vec::new();
    if close.room_type == 1 {
        if member.user_type == Some(1) {
            recipients.extend(
                close
                    .remaining
                    .iter()
                    .filter(|other| other.user_type == Some(0))
                    .cloned(),
            );
            if let Some(pool) = &state.pool {
                let stats = match sqlx::query(
                    "SELECT stats FROM simulation_router_room WHERE id=? LIMIT 1",
                )
                .bind(close.room_id)
                .fetch_optional(pool)
                .await
                {
                    Ok(row) => row.and_then(|row| row.get::<Option<i32>, _>("stats")),
                    Err(error) => {
                        tracing::error!(%error, room_id = close.room_id, "simulation disconnect status lookup failed");
                        for recipient in &close.remaining {
                            send_to_user(
                                close.room_id,
                                recipient.clone(),
                                sim_response(500, "服务器错误", "", ""),
                            )
                            .await;
                        }
                        return;
                    }
                };
                if stats == Some(0) {
                    if let Err(error) = sqlx::query(
                        "DELETE FROM simulation_router_room_user WHERE room_id=? AND user_id=?",
                    )
                    .bind(close.room_id)
                    .bind(&member.user.id)
                    .execute(pool)
                    .await
                    {
                        tracing::error!(%error, room_id = close.room_id, operation = "remove disconnected member", "simulation disconnect update failed");
                        for recipient in &close.remaining {
                            send_to_user(
                                close.room_id,
                                recipient.clone(),
                                sim_response(500, "服务器错误", "", ""),
                            )
                            .await;
                        }
                        return;
                    }
                }
            }
        } else {
            recipients.extend(
                close
                    .remaining
                    .iter()
                    .filter(|other| other.user_type == Some(1))
                    .cloned(),
            );
        }
        let message = json!({"topic":"offline","body":{"id":member.user.id}}).to_string();
        for recipient in recipients {
            send_to_user(close.room_id, recipient, sim_response(1, &message, "", "")).await;
        }
    } else if close.room_type == 2 || close.room_type == 3 {
        if !matches!(member.user_type, Some(0 | 1)) {
            return;
        }
        if member.channel == Some(1) {
            if let Some(recipient) = close
                .remaining
                .iter()
                .find(|other| other.channel == Some(0))
            {
                send_to_user(
                    close.room_id,
                    recipient.clone(),
                    sim_response(
                        1,
                        &json!({"type":0,"id":member.user.id}).to_string(),
                        &member.user.name.clone().unwrap_or_default(),
                        "",
                    ),
                )
                .await;
            }
        } else if member.channel == Some(0) {
            if let Some(pool) = &state.pool {
                if let Err(error) =
                    sqlx::query("UPDATE simulation_router_room SET play_status=0 WHERE id=?")
                        .bind(close.room_id)
                        .execute(pool)
                        .await
                {
                    tracing::error!(%error, room_id = close.room_id, operation = "stop disconnected playback", "simulation disconnect update failed");
                    for recipient in &close.remaining {
                        send_to_user(
                            close.room_id,
                            recipient.clone(),
                            sim_response(500, "服务器错误", "", ""),
                        )
                        .await;
                    }
                    return;
                }
            }
            for recipient in close
                .remaining
                .iter()
                .filter(|other| other.user_type == Some(1))
            {
                send_to_user(
                    close.room_id,
                    recipient.clone(),
                    sim_response(1, &json!({"type":2}).to_string(), "", ""),
                )
                .await;
            }
        }
    } else if close.room_type == 0 {
        let message = json!({"topic":"offline","body":{"id":member.user.id}}).to_string();
        for recipient in close.remaining {
            send_to_user(close.room_id, recipient, sim_response(1, &message, "", "")).await;
        }
    }
}

async fn send_to_user(room_id: i32, member: SimMember, message: String) {
    let manager = MANAGER.lock().await;
    if let Some(connection) = manager.sessions.values().find(|connection| {
        connection.family == Family::Simulation
            && connection.sim_room_id == Some(room_id)
            && connection.user.id == member.user.id
    }) {
        if connection.tx.send(Outbound::Text(message)).is_err() {
            tracing::warn!(room_id, user_id = %member.user.id, "simulation outbound queue closed");
        }
    }
}
