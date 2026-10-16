use super::manager::MANAGER;
use super::{finish_peer_close, HEARTBEAT_PING, HEARTBEAT_PONG};
use crate::platform::envelope::envelope_json;
use axum::extract::ws::{Message, WebSocket, WebSocketUpgrade};
use axum::http::StatusCode;
use axum::response::Response;
use serde_json::json;

pub(super) async fn status() -> Response {
    let manager = MANAGER.lock().await;
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"connections": manager.sessions.len(), "rooms": manager.room_count()})),
        "ok",
        "",
    )
}
pub(super) async fn status_open(upgrade: WebSocketUpgrade) -> Response {
    upgrade.on_upgrade(status_serve)
}

async fn status_serve(mut socket: WebSocket) {
    while let Some(message) = socket.recv().await {
        match message {
            Ok(Message::Text(text)) if text.as_str() == HEARTBEAT_PING => {
                if let Err(error) = socket
                    .send(Message::Text(HEARTBEAT_PONG.to_owned().into()))
                    .await
                {
                    tracing::warn!(%error, "status websocket heartbeat delivery failed");
                    break;
                }
            }
            Ok(Message::Ping(payload)) => {
                if let Err(error) = socket.send(Message::Pong(payload)).await {
                    tracing::warn!(%error, "status websocket pong delivery failed");
                    break;
                }
            }
            // StatusWebSocket answers every other text frame with a literal "pong".
            Ok(Message::Text(_)) => {
                if let Err(error) = socket.send(Message::Text("pong".into())).await {
                    tracing::warn!(%error, "status websocket reply delivery failed");
                    break;
                }
            }
            Ok(Message::Close(_)) => {
                finish_peer_close(&mut socket).await;
                break;
            }
            Err(_) => break,
            Ok(_) => {}
        }
    }
}
