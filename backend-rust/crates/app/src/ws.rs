use super::{envelope_response, lookup_user, AppState};
use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        Path, Query, State,
    },
    response::Response,
};
use serde::Deserialize;
use std::{
    collections::HashMap,
    sync::{Arc, LazyLock},
};
use tokio::sync::{broadcast, Mutex};
use uuid::Uuid;

static ROOMS: LazyLock<Arc<Mutex<HashMap<String, broadcast::Sender<String>>>>> =
    LazyLock::new(|| Arc::new(Mutex::new(HashMap::new())));
fn rooms() -> Arc<Mutex<HashMap<String, broadcast::Sender<String>>>> {
    ROOMS.clone()
}
#[derive(Debug, Deserialize)]
pub(crate) struct WsAuth {
    pub token: Option<String>,
    #[serde(rename = "deviceId")]
    pub device_id: Option<String>,
}

pub(crate) async fn status() -> Response {
    super::envelope_json(
        axum::http::StatusCode::OK,
        200,
        Some(serde_json::json!({"connections":0,"rooms":rooms().lock().await.len()})),
        "ok",
        "",
    )
}
pub(crate) async fn open(
    State(state): State<AppState>,
    Path(room): Path<String>,
    Query(auth): Query<WsAuth>,
    upgrade: WebSocketUpgrade,
) -> Response {
    let Some(token) = auth.token.as_deref().filter(|v| !v.is_empty()) else {
        return envelope_response(axum::http::StatusCode::OK, 203, "token不能为空", "");
    };
    let Some(device) = auth.device_id.as_deref().filter(|v| !v.is_empty()) else {
        return envelope_response(axum::http::StatusCode::OK, 204, "设备标识不能为空", "");
    };
    match lookup_user(&state, token, device).await {
        Ok(Some(user)) => upgrade.on_upgrade(move |socket| serve(socket, room, user.id)),
        Ok(None) => envelope_response(axum::http::StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(_) => envelope_response(
            axum::http::StatusCode::INTERNAL_SERVER_ERROR,
            500,
            "服务器错误",
            "",
        ),
    }
}
async fn serve(socket: WebSocket, room: String, user_id: String) {
    let room_state = rooms();
    let sender = {
        let mut map = room_state.lock().await;
        map.entry(room.clone())
            .or_insert_with(|| broadcast::channel(128).0)
            .clone()
    };
    let mut receiver = sender.subscribe();
    let (connection_id, mut socket) = (Uuid::new_v4().to_string(), socket);
    let hello=serde_json::json!({"topic":"connected","room":room,"userId":user_id,"connectionId":connection_id}).to_string();
    let _ = socket.send(Message::Text(hello.into())).await;
    loop {
        tokio::select! {incoming=socket.recv()=>match incoming{Some(Ok(Message::Text(text)))=>{let value=text.to_string();let _=sender.send(value.clone());if socket.send(Message::Text(value.into())).await.is_err(){break;}},Some(Ok(Message::Ping(data)))=>{if socket.send(Message::Pong(data)).await.is_err(){break;}},Some(Ok(Message::Close(_)))|None=>break,Some(Ok(_))=>{},Some(Err(_))=>break},outgoing=receiver.recv()=>match outgoing{Ok(text)=>{if socket.send(Message::Text(text.into())).await.is_err(){break;}},Err(broadcast::error::RecvError::Lagged(_))=>{let _=socket.send(Message::Text(serde_json::json!({"code":500,"message":"广播队列溢出"}).to_string().into())).await;},Err(_)=>break}}
    }
}
