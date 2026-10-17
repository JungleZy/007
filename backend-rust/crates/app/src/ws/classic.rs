use super::manager::MANAGER;
use super::{classic_response, Outbound, Prepared};
use crate::platform::state::AppState;
use serde_json::Value;
use tokio::sync::mpsc;

pub(super) async fn classic_message(
    state: &AppState,
    prepared: &Prepared,
    session_id: &str,
    text: &str,
) {
    let tx = current_tx(prepared, session_id).await;
    let Some(tx) = tx else {
        return;
    };
    let value: Value = match serde_json::from_str(text) {
        Ok(value) => value,
        Err(_) => {
            let _ = tx.send(Outbound::Text(classic_response(202, "消息格式不正确")));
            return;
        }
    };
    let Some(object) = value.as_object() else {
        let _ = tx.send(Outbound::Text(classic_response(202, "消息格式不正确")));
        return;
    };
    let Some(code) = json_int(object.get("code")) else {
        let _ = tx.send(Outbound::Text(classic_response(202, "消息类型不能为空")));
        return;
    };
    match code {
        2001 | 3001 => {
            let Some(data) = object.get("data").filter(|data| data.is_object()) else {
                let _ = tx.send(Outbound::Text(classic_response(202, "消息格式不正确")));
                return;
            };
            let Some(pool) = &state.pool else {
                let _ = tx.send(Outbound::Text(classic_response(500, "服务器错误")));
                return;
            };
            let result = if code == 2001 {
                crate::domain::telegram::telegram_lifecycle::save_captured_log(
                    pool,
                    &prepared.user.id,
                    data,
                )
                .await
            } else {
                crate::domain::telegram::telegram_lifecycle::save_captured_content(
                    pool,
                    &prepared.user.id,
                    data,
                )
                .await
            };
            if let Err((code, message)) = result {
                let _ = tx.send(Outbound::Text(classic_response(code, message)));
            }
        }
        _ => {}
    }
}

pub(super) async fn current_tx(
    prepared: &Prepared,
    session_id: &str,
) -> Option<mpsc::UnboundedSender<Outbound>> {
    let manager = MANAGER.lock().await;
    manager
        .sessions
        .get(session_id)
        .filter(|conn| conn.family == prepared.family && conn.user.id == prepared.user.id)
        .map(|conn| conn.tx.clone())
}

pub(super) fn json_int(value: Option<&Value>) -> Option<i64> {
    value.and_then(|value| {
        value
            .as_i64()
            .or_else(|| value.as_str().and_then(|text| text.parse().ok()))
    })
}
