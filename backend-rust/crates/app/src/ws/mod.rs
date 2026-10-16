mod classic;
mod dispatch;
mod general;
pub(crate) mod manager;
pub(crate) mod notify;
mod session;
mod simulation;
mod status;
mod union;

use axum::extract::ws::{close_code, CloseFrame, Message, WebSocket};
use serde::Deserialize;
use serde_json::Map;
use serde_json::{json, Value};
use tokio::sync::mpsc;

const HEARTBEAT_PING: &str = "__nip_heartbeat_ping__";

/// Answers a peer-initiated close. tungstenite queues the echoed close frame when it reads the
/// peer's close but refuses further sends, and only flushes the reply on the next read; read once
/// more (bounded) so the peer gets the echo like the Java container sends it.
async fn finish_peer_close(socket: &mut WebSocket) {
    let _ = tokio::time::timeout(std::time::Duration::from_secs(1), socket.recv()).await;
}

/// Server-initiated closes carry 1000 like the Java container (javax Session.close()).
fn normal_close() -> Message {
    Message::Close(Some(CloseFrame {
        code: close_code::NORMAL,
        reason: "".into(),
    }))
}
const HEARTBEAT_PONG: &str = "__nip_heartbeat_pong__";

#[derive(Debug, Deserialize, Clone)]
struct WsAuth {
    pub(super) token: Option<String>,
    #[serde(rename = "deviceId")]
    device_id: Option<String>,
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(super) enum Family {
    Classic,
    Union,
    Simulation,
    GeneralKey,
    GeneralTelex,
    GeneralTicker,
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(super) enum GeneralKind {
    Key,
    Telex,
    Ticker,
}
impl GeneralKind {
    fn protocol_name(self) -> &'static str {
        match self {
            Self::Key => "key",
            Self::Telex => "telex",
            Self::Ticker => "ticker",
        }
    }
}

#[derive(Clone, Debug)]
enum RouteSpec {
    Classic,
    Union,
    Simulation {
        raw_room_id: String,
    },
    GeneralKey {
        raw_train_id: String,
    },
    GeneralTelex {
        train_id: String,
    },
    GeneralTicker {
        raw_train_id: String,
        raw_role: String,
    },
    Unknown,
}

impl RouteSpec {
    fn family(&self) -> Family {
        match self {
            Self::Classic => Family::Classic,
            Self::Union => Family::Union,
            Self::Simulation { .. } => Family::Simulation,
            Self::GeneralKey { .. } => Family::GeneralKey,
            Self::GeneralTelex { .. } => Family::GeneralTelex,
            Self::GeneralTicker { .. } => Family::GeneralTicker,
            Self::Unknown => Family::Classic,
        }
    }

    fn general_kind(&self) -> Option<GeneralKind> {
        match self {
            Self::GeneralKey { .. } => Some(GeneralKind::Key),
            Self::GeneralTelex { .. } => Some(GeneralKind::Telex),
            Self::GeneralTicker { .. } => Some(GeneralKind::Ticker),
            _ => None,
        }
    }
}

#[derive(Clone, Debug, serde::Serialize)]
struct WsUser {
    id: String,
    #[serde(rename = "name", skip_serializing_if = "Option::is_none")]
    name: Option<String>,
    #[serde(rename = "userImg", skip_serializing_if = "Option::is_none")]
    user_img: Option<String>,
}

#[derive(Clone, Debug, serde::Serialize)]
struct UnionMember {
    id: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    name: Option<String>,
    #[serde(rename = "userImg", skip_serializing_if = "Option::is_none")]
    user_img: Option<String>,
    #[serde(rename = "type")]
    user_type: i32,
    status: i32,
}

impl From<WsUser> for UnionMember {
    fn from(user: WsUser) -> Self {
        Self {
            id: user.id,
            name: user.name,
            user_img: user.user_img,
            user_type: 0,
            status: 0,
        }
    }
}

#[derive(Clone, Debug)]
struct SimMember {
    pub(super) user: WsUser,
    user_type: Option<i32>,
    channel: Option<i32>,
    status: i32,
}

#[derive(Clone, Debug)]
struct Prepared {
    route: RouteSpec,
    family: Family,
    scope: String,
    pub(super) user: WsUser,
    role: Option<i32>,
    sim_room_id: Option<i32>,
    sim_room_type: Option<i32>,
    sim_creator: Option<String>,
    sim_member: Option<SimMember>,
}

impl Prepared {
    fn key(&self) -> String {
        match self.family {
            Family::Classic => format!("classic:{}", self.user.id),
            Family::Union => format!("union:{}", self.user.id),
            Family::Simulation => format!("simulation:{}:{}", self.user.id, self.scope),
            Family::GeneralKey => format!(
                "general-key:{}:{}:{}",
                self.user.id,
                self.scope,
                self.role.unwrap_or(0)
            ),
            Family::GeneralTelex => format!(
                "general-telex:{}:{}:{}",
                self.user.id,
                self.scope,
                self.role.unwrap_or(0)
            ),
            Family::GeneralTicker => format!(
                "general-ticker:{}:{}:{}",
                self.user.id,
                self.scope,
                self.role.unwrap_or(0)
            ),
        }
    }
}

#[derive(Clone, Debug)]
struct Connection {
    id: String,
    generation: u64,
    key: String,
    family: Family,
    scope: String,
    pub(super) user: WsUser,
    role: Option<i32>,
    tx: mpsc::UnboundedSender<Outbound>,
    sim_room_id: Option<i32>,
    sim_member: Option<SimMember>,
    status: i32,
}

#[derive(Debug)]
enum Outbound {
    Text(String),
    Close,
}

#[derive(Clone, Debug)]
struct GeneralRoomKey {
    kind: GeneralKind,
    pub(super) train_id: String,
}

impl PartialEq for GeneralRoomKey {
    fn eq(&self, other: &Self) -> bool {
        self.kind == other.kind && self.train_id == other.train_id
    }
}
impl Eq for GeneralRoomKey {}
impl std::hash::Hash for GeneralRoomKey {
    fn hash<H: std::hash::Hasher>(&self, state: &mut H) {
        self.kind.hash(state);
        self.train_id.hash(state);
    }
}

#[derive(Clone, Debug)]
struct SimRoom {
    room_type: i32,
    creator: String,
    members: Vec<String>,
}

#[derive(Clone, Debug)]
struct UnionRoom {
    id: String,
    name: Option<String>,
    room_type: i32,
    status: i32,
    password: Option<String>,
    nnt: i32,
    admin: String,
    users: Vec<UnionMember>,
}

impl UnionRoom {
    pub(super) fn json(&self) -> Value {
        json!({
            "id": self.id,
            "name": self.name,
            "type": self.room_type,
            "status": self.status,
            "password": self.password,
            "nnt": self.nnt,
            "admin": self.admin,
            "users": self.users,
        })
    }
}

pub(crate) fn routes(
    _state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::platform::http::not_found;
    use axum::{routing::get, Router};

    Router::new()
        .route("/status", get(status::status_open))
        .route("/websocket/{sid}", get(session::open).fallback(not_found))
        .route(
            "/websocketUnion/{sid}",
            get(session::open).fallback(not_found),
        )
        .route(
            "/startWebsocket/{sid}",
            get(session::open).fallback(not_found),
        )
        .route(
            "/simulation/{id}/{roomId}",
            get(session::open).fallback(not_found),
        )
        .route(
            "/generalKeyPatTrain/{uid}/{trainId}",
            get(session::open).fallback(not_found),
        )
        .route(
            "/generalTelexPatTrain/{uid}/{trainId}",
            get(session::open).fallback(not_found),
        )
        .route(
            "/generalTickerPat/{uid}/{trainId}/{role}",
            get(session::open).fallback(not_found),
        )
        .route("/api/ws/status", get(status::status))
        .route("/api/status", get(status::status_open))
        .route(
            "/api/websocket/{sid}",
            get(session::open).fallback(not_found),
        )
        .route(
            "/api/websocketUnion/{sid}",
            get(session::open).fallback(not_found),
        )
        .route(
            "/api/startWebsocket/{sid}",
            get(session::open).fallback(not_found),
        )
        .route(
            "/api/simulation/{id}/{roomId}",
            get(session::open).fallback(not_found),
        )
        .route(
            "/api/generalKeyPatTrain/{uid}/{trainId}",
            get(session::open).fallback(not_found),
        )
        .route(
            "/api/generalTelexPatTrain/{uid}/{trainId}",
            get(session::open).fallback(not_found),
        )
        .route(
            "/api/generalTickerPat/{uid}/{trainId}/{role}",
            get(session::open).fallback(not_found),
        )
}

pub(super) fn general_kind_for_family(family: Family) -> Option<GeneralKind> {
    match family {
        Family::GeneralKey => Some(GeneralKind::Key),
        Family::GeneralTelex => Some(GeneralKind::Telex),
        Family::GeneralTicker => Some(GeneralKind::Ticker),
        _ => None,
    }
}

pub(super) fn scalar(value: Option<&Value>) -> Option<String> {
    match value? {
        Value::String(value) => Some(value.clone()),
        Value::Number(value) => Some(value.to_string()),
        Value::Bool(value) => Some(value.to_string()),
        Value::Null => None,
        _ => None,
    }
}
pub(super) fn scalar_or_json(value: Option<&Value>) -> Option<String> {
    value.and_then(|value| scalar(Some(value)).or_else(|| serde_json::to_string(value).ok()))
}
pub(super) fn value_text(value: &Value) -> String {
    scalar(Some(value)).unwrap_or_else(|| value.to_string())
}

pub(super) fn classic_response(code: i32, data: &str) -> String {
    let mut object = Map::new();
    object.insert("code".to_owned(), json!(code));
    object.insert("data".to_owned(), json!(data));
    Value::Object(object).to_string()
}
pub(super) fn union_response(
    code: i32,
    send: Option<&str>,
    receive: Option<&str>,
    data: Option<&str>,
) -> String {
    let mut object = Map::new();
    object.insert("code".to_owned(), json!(code));
    if let Some(value) = send {
        object.insert("sendUser".to_owned(), json!(value));
    }
    if let Some(value) = receive {
        object.insert("receiveUser".to_owned(), json!(value));
    }
    if let Some(value) = data {
        object.insert("data".to_owned(), json!(value));
    }
    Value::Object(object).to_string()
}
pub(super) fn socket_response(code: i32, data: &str, send: &str, receive: &str) -> String {
    serde_json::to_string(&json!({"code":code,"sendName":send,"receiveName":receive,"data":data}))
        .unwrap_or_default()
}
pub(super) fn general_notification_response(code: i32, map: Value) -> String {
    serde_json::to_string(&json!({"code": code, "map": map})).unwrap_or_default()
}
pub(super) fn sim_response(code: i32, data: &str, send: &str, receive: &str) -> String {
    socket_response(code, data, send, receive)
}
pub(super) fn handshake_error(
    family: Family,
    message: &str,
    authenticated_id: Option<&str>,
) -> String {
    match family {
        Family::Classic => classic_response(1, message),
        // WebSocketUnionService words its credential rejection for joint training.
        Family::Union if message == "登录凭据无效，拒绝建立连接" => {
            classic_response(1, "登录凭据无效，拒绝建立联合训练连接")
        }
        Family::Union => classic_response(1, message),
        Family::Simulation => {
            let id = authenticated_id.unwrap_or("");
            sim_response(-1, message, id, id)
        }
        Family::GeneralKey | Family::GeneralTelex | Family::GeneralTicker => {
            socket_response(-1, message, "", "")
        }
    }
}
pub(super) fn union_or_classic_close(family: Family) -> String {
    match family {
        Family::Union | Family::Classic => classic_response(1, "关闭连接"),
        _ => String::new(),
    }
}
#[cfg(test)]
mod tests;
