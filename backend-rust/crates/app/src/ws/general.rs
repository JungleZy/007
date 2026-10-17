use super::dispatch::send_family_error;
use super::manager::MANAGER;
use super::socket_response;
use super::{GeneralKind, Outbound, Prepared};
use serde_json::Value;

pub(super) async fn general_message(prepared: &Prepared, session_id: &str, text: &str) {
    let Some(kind) = prepared.route.general_kind() else {
        return;
    };
    let value: Value = match serde_json::from_str(text) {
        Ok(value) => value,
        Err(_) => {
            send_family_error(session_id, prepared.family, "消息格式错误").await;
            return;
        }
    };
    let Some(object) = value.as_object() else {
        send_family_error(session_id, prepared.family, "消息格式错误").await;
        return;
    };
    let Some(topic) = object
        .get("topic")
        .and_then(Value::as_str)
        .filter(|topic| !topic.trim().is_empty())
    else {
        send_family_error(session_id, prepared.family, "消息格式错误").await;
        return;
    };
    let control = matches!(topic, "begin" | "end");
    let participant = matches!(topic, "ready" | "pat" | "finish");
    if !control && !participant {
        send_family_error(session_id, prepared.family, "未知主题").await;
        return;
    }
    let is_control = prepared.role == Some(1);
    if control != is_control {
        send_family_error(session_id, prepared.family, "无权限操作").await;
        return;
    }
    let mut outbound = object.clone();
    outbound.insert("id".to_owned(), Value::String(prepared.user.id.clone()));
    let outbound = Value::Object(outbound).to_string();
    let recipients = {
        let manager = MANAGER.lock().await;
        manager
            .general_snapshot(kind, &prepared.scope, session_id)
            .map(|(_, recipients)| recipients)
            .unwrap_or_default()
    };
    if control {
        for (recipient, role) in recipients {
            if role != 1 {
                let _ = recipient.send(Outbound::Text(socket_response(1, &outbound, "", "")));
            }
        }
    } else {
        if topic == "ready" {
            let mut manager = MANAGER.lock().await;
            manager.set_general_status(session_id, 2);
        }
        if kind == GeneralKind::Ticker && topic == "finish" {
            let mut manager = MANAGER.lock().await;
            manager.set_general_status(session_id, 3);
        }
        for (recipient, role) in recipients {
            if role == 1 {
                let _ = recipient.send(Outbound::Text(socket_response(1, &outbound, "", "")));
            }
        }
    }
}
