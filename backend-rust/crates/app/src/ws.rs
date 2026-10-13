use super::{envelope_json, lookup_user, AppState};
use axum::{
    extract::{
        ws::{close_code, CloseFrame, Message, WebSocket, WebSocketUpgrade},
        OriginalUri, Query, State,
    },
    http::StatusCode,
    response::Response,
};
use serde::Deserialize;
use serde_json::{json, Map, Value};
use sqlx::Row;
use std::{
    collections::HashMap,
    sync::{Arc, LazyLock},
};
use tokio::sync::{mpsc, Mutex};
use uuid::Uuid;

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
pub(crate) struct WsAuth {
    pub token: Option<String>,
    #[serde(rename = "deviceId")]
    pub device_id: Option<String>,
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
enum Family {
    Classic,
    Union,
    Simulation,
    GeneralKey,
    GeneralTelex,
    GeneralTicker,
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
enum GeneralKind {
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
    user: WsUser,
    user_type: Option<i32>,
    channel: Option<i32>,
    status: i32,
}

#[derive(Clone, Debug)]
struct Prepared {
    route: RouteSpec,
    family: Family,
    scope: String,
    user: WsUser,
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
    user: WsUser,
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
    train_id: String,
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
    fn json(&self) -> Value {
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

#[derive(Default)]
struct WsManager {
    next_generation: u64,
    sessions: HashMap<String, Connection>,
    current_by_key: HashMap<String, String>,
    general_rooms: HashMap<GeneralRoomKey, Vec<String>>,
    simulation_rooms: HashMap<i32, SimRoom>,
    union_rooms: HashMap<String, UnionRoom>,
}

struct Registration {
    session_id: String,
    generation: u64,
    replaced: Vec<(mpsc::UnboundedSender<Outbound>, bool)>,
    notifications: Vec<(mpsc::UnboundedSender<Outbound>, String)>,
}

struct SimClose {
    room_id: i32,
    room_type: i32,
    member: SimMember,
    remaining: Vec<SimMember>,
}

struct Disconnect {
    notifications: Vec<(mpsc::UnboundedSender<Outbound>, String)>,
    sim: Option<SimClose>,
}

impl WsManager {
    fn room_count(&self) -> usize {
        self.general_rooms.len() + self.simulation_rooms.len() + self.union_rooms.len()
    }

    fn register(
        &mut self,
        prepared: &Prepared,
        tx: mpsc::UnboundedSender<Outbound>,
    ) -> Registration {
        self.next_generation = self.next_generation.wrapping_add(1);
        let generation = self.next_generation;
        let session_id = Uuid::new_v4().to_string();
        let key = prepared.key();
        let mut replaced = Vec::new();

        if let Some(old_id) = self.current_by_key.remove(&key) {
            self.remove_for_replacement(&old_id, &mut replaced);
        }

        if let Some(kind) = prepared.route.general_kind() {
            let room_key = GeneralRoomKey {
                kind,
                train_id: prepared.scope.clone(),
            };
            let stale: Vec<String> = self
                .general_rooms
                .get(&room_key)
                .into_iter()
                .flat_map(|members| members.iter())
                .filter(|id| {
                    self.sessions
                        .get(*id)
                        .is_some_and(|c| c.user.id == prepared.user.id)
                })
                .cloned()
                .collect();
            if let Some(members) = self.general_rooms.get_mut(&room_key) {
                members.retain(|id| !stale.iter().any(|stale_id| stale_id == id));
            }
            for stale_id in stale {
                if let Some(old) = self.sessions.remove(&stale_id) {
                    self.current_by_key.retain(|_, id| id != &stale_id);
                    replaced.push((old.tx, false));
                }
            }
        }
        if let Some(room_id) = prepared.sim_room_id {
            let stale: Vec<String> = self
                .simulation_rooms
                .get(&room_id)
                .into_iter()
                .flat_map(|room| room.members.iter())
                .filter(|id| {
                    self.sessions
                        .get(*id)
                        .is_some_and(|c| c.user.id == prepared.user.id)
                })
                .cloned()
                .collect();
            if let Some(room) = self.simulation_rooms.get_mut(&room_id) {
                room.members
                    .retain(|id| !stale.iter().any(|stale_id| stale_id == id));
            }
            for stale_id in stale {
                if let Some(old) = self.sessions.remove(&stale_id) {
                    self.current_by_key.retain(|_, id| id != &stale_id);
                    replaced.push((old.tx, false));
                }
            }
        }

        let connection = Connection {
            id: session_id.clone(),
            generation,
            key: key.clone(),
            family: prepared.family,
            scope: prepared.scope.clone(),
            user: prepared.user.clone(),
            role: prepared.role,
            tx,
            sim_room_id: prepared.sim_room_id,
            sim_member: prepared.sim_member.clone(),
            status: 1,
        };
        self.current_by_key.insert(key, session_id.clone());
        self.sessions.insert(session_id.clone(), connection);

        let mut notifications = Vec::new();
        match prepared.family {
            Family::GeneralKey | Family::GeneralTelex | Family::GeneralTicker => {
                let kind = prepared
                    .route
                    .general_kind()
                    .expect("general family has a kind");
                let room_key = GeneralRoomKey {
                    kind,
                    train_id: prepared.scope.clone(),
                };
                let existing = self
                    .general_rooms
                    .get(&room_key)
                    .cloned()
                    .unwrap_or_default();
                let recipients: Vec<mpsc::UnboundedSender<Outbound>> = existing
                    .iter()
                    .filter_map(|id| self.sessions.get(id))
                    .filter(|conn| (prepared.role == Some(1)) == (conn.role != Some(1)))
                    .map(|conn| conn.tx.clone())
                    .collect();
                self.general_rooms
                    .entry(room_key)
                    .or_default()
                    .push(session_id.clone());
                let online =
                    serde_json::to_string(&json!({"topic":"online","id":prepared.user.id}))
                        .unwrap_or_else(|_| "{\"topic\":\"online\"}".to_owned());
                let wrapped = socket_response(1, &online, "", "");
                notifications.extend(
                    recipients
                        .into_iter()
                        .map(|recipient| (recipient, wrapped.clone())),
                );
            }
            Family::Simulation => {
                let room_id = prepared.sim_room_id.expect("simulation has room id");
                let (existing, room_type, creator) = self
                    .simulation_rooms
                    .get(&room_id)
                    .map(|room| (room.members.clone(), room.room_type, room.creator.clone()))
                    .unwrap_or_else(|| {
                        (
                            Vec::new(),
                            prepared.sim_room_type.unwrap_or_default(),
                            prepared.sim_creator.clone().unwrap_or_default(),
                        )
                    });
                let recipients: Vec<(mpsc::UnboundedSender<Outbound>, SimMember)> = existing
                    .iter()
                    .filter_map(|id| self.sessions.get(id))
                    .filter_map(|conn| {
                        conn.sim_member
                            .clone()
                            .map(|member| (conn.tx.clone(), member))
                    })
                    .collect();
                let room = self
                    .simulation_rooms
                    .entry(room_id)
                    .or_insert_with(|| SimRoom {
                        room_type,
                        creator,
                        members: existing,
                    });
                room.members.push(session_id.clone());
                let member = prepared.sim_member.clone().expect("simulation has member");
                let message = if room.room_type == 1 {
                    let body = json!({
                        "id": prepared.user.id,
                        "userName": prepared.user.name,
                        "userImg": prepared.user.user_img,
                        "channel": member.channel,
                    });
                    let notice = json!({"topic":"online","body":body}).to_string();
                    if prepared.user.id == room.creator {
                        recipients
                            .into_iter()
                            .filter(|(_, recipient)| recipient.user_type == Some(1))
                            .map(|(tx, _)| (tx, sim_response(1, &notice, "", "")))
                            .collect::<Vec<_>>()
                    } else {
                        recipients
                            .into_iter()
                            .filter(|(_, recipient)| recipient.channel == Some(-1))
                            .take(1)
                            .map(|(tx, _)| (tx, sim_response(1, &notice, "", "")))
                            .collect::<Vec<_>>()
                    }
                } else if room.room_type == 2 || room.room_type == 3 {
                    if member.channel == Some(1) {
                        let notice = json!({"type":"1","id":prepared.user.id}).to_string();
                        recipients
                            .into_iter()
                            .filter(|(_, recipient)| recipient.channel == Some(0))
                            .take(1)
                            .map(|(tx, _)| {
                                (
                                    tx,
                                    sim_response(
                                        1,
                                        &notice,
                                        &prepared.user.name.clone().unwrap_or_default(),
                                        "",
                                    ),
                                )
                            })
                            .collect::<Vec<_>>()
                    } else {
                        Vec::new()
                    }
                } else {
                    let notice =
                        json!({"topic":"online","body":{"id":prepared.user.id}}).to_string();
                    recipients
                        .into_iter()
                        .map(|(tx, _)| (tx, sim_response(1, &notice, "", "")))
                        .collect::<Vec<_>>()
                };
                notifications.extend(message);
            }
            Family::Union => {
                let notice = union_response(
                    2,
                    None,
                    None,
                    Some(
                        &serde_json::to_string(&UnionMember::from(prepared.user.clone()))
                            .unwrap_or_default(),
                    ),
                );
                notifications.extend(
                    self.sessions
                        .values()
                        .filter(|conn| conn.family == Family::Union && conn.id != session_id)
                        .map(|conn| (conn.tx.clone(), notice.clone())),
                );
            }
            Family::Classic => {}
        }

        Registration {
            session_id,
            generation,
            replaced,
            notifications,
        }
    }

    fn remove_for_replacement(
        &mut self,
        old_id: &str,
        replaced: &mut Vec<(mpsc::UnboundedSender<Outbound>, bool)>,
    ) {
        let Some(old) = self.sessions.remove(old_id) else {
            return;
        };
        self.current_by_key.retain(|_, id| id != old_id);
        match old.family {
            Family::GeneralKey | Family::GeneralTelex | Family::GeneralTicker => {
                if let Some(kind) = general_kind_for_family(old.family) {
                    let key = GeneralRoomKey {
                        kind,
                        train_id: old.scope.clone(),
                    };
                    let empty = if let Some(members) = self.general_rooms.get_mut(&key) {
                        members.retain(|id| id != old_id);
                        members.is_empty()
                    } else {
                        false
                    };
                    if empty {
                        self.general_rooms.remove(&key);
                    }
                }
                replaced.push((old.tx, false));
            }
            Family::Simulation => {
                if let Some(room_id) = old.sim_room_id {
                    let empty = if let Some(room) = self.simulation_rooms.get_mut(&room_id) {
                        room.members.retain(|id| id != old_id);
                        room.members.is_empty()
                    } else {
                        false
                    };
                    if empty {
                        self.simulation_rooms.remove(&room_id);
                    }
                }
                replaced.push((old.tx, false));
            }
            Family::Classic => replaced.push((old.tx, true)),
            Family::Union => {
                // Union rooms contain logical user ids, not session ids.  A replacement
                // therefore preserves room membership while the connection is swapped.
                replaced.push((old.tx, true));
            }
        }
    }

    fn active(&self, session_id: &str, generation: u64) -> bool {
        self.sessions
            .get(session_id)
            .is_some_and(|conn| conn.generation == generation)
    }

    fn set_general_status(&mut self, session_id: &str, status: i32) {
        if let Some(conn) = self.sessions.get_mut(session_id) {
            conn.status = status;
        }
    }

    fn set_sim_channel(&mut self, session_id: &str, channel: i32) {
        if let Some(conn) = self.sessions.get_mut(session_id) {
            if let Some(member) = conn.sim_member.as_mut() {
                member.channel = Some(channel);
            }
        }
    }

    fn union_room_json(&self, id: &str) -> Option<Value> {
        self.union_rooms.get(id).map(UnionRoom::json)
    }

    fn disconnect(&mut self, session_id: &str, generation: u64) -> Option<Disconnect> {
        let conn = self.sessions.get(session_id)?.clone();
        if conn.generation != generation {
            return None;
        }
        self.sessions.remove(session_id);
        if self
            .current_by_key
            .get(&conn.key)
            .is_some_and(|id| id == session_id)
        {
            self.current_by_key.remove(&conn.key);
        }
        let mut notifications = Vec::new();
        let mut sim = None;
        match conn.family {
            Family::GeneralKey | Family::GeneralTelex | Family::GeneralTicker => {
                if let Some(kind) = general_kind_for_family(conn.family) {
                    let key = GeneralRoomKey {
                        kind,
                        train_id: conn.scope.clone(),
                    };
                    let (remaining_ids, empty) =
                        if let Some(members) = self.general_rooms.get_mut(&key) {
                            members.retain(|id| id != session_id);
                            (members.clone(), members.is_empty())
                        } else {
                            (Vec::new(), false)
                        };
                    if empty {
                        self.general_rooms.remove(&key);
                    }
                    let recipient_ids: Vec<mpsc::UnboundedSender<Outbound>> = remaining_ids
                        .iter()
                        .filter_map(|id| self.sessions.get(id))
                        .filter(|other| (conn.role == Some(1)) == (other.role != Some(1)))
                        .map(|other| other.tx.clone())
                        .collect();
                    let offline =
                        serde_json::to_string(&json!({"topic":"offline","id":conn.user.id}))
                            .unwrap_or_else(|_| "{\"topic\":\"offline\"}".to_owned());
                    let wrapped = socket_response(1, &offline, "", "");
                    notifications.extend(recipient_ids.into_iter().map(|tx| (tx, wrapped.clone())));
                }
            }
            Family::Simulation => {
                if let (Some(room_id), Some(member)) = (conn.sim_room_id, conn.sim_member.clone()) {
                    let room_type = self
                        .simulation_rooms
                        .get(&room_id)
                        .map(|room| room.room_type)
                        .unwrap_or_default();
                    let (remaining_ids, empty) =
                        if let Some(room) = self.simulation_rooms.get_mut(&room_id) {
                            room.members.retain(|id| id != session_id);
                            (room.members.clone(), room.members.is_empty())
                        } else {
                            (Vec::new(), false)
                        };
                    if empty {
                        self.simulation_rooms.remove(&room_id);
                    }
                    let remaining = remaining_ids
                        .iter()
                        .filter_map(|id| self.sessions.get(id).and_then(|c| c.sim_member.clone()))
                        .collect();
                    sim = Some(SimClose {
                        room_id,
                        room_type,
                        member,
                        remaining,
                    });
                }
            }
            Family::Union => {
                let mut room_events = Vec::new();
                for room in self.union_rooms.values_mut() {
                    if !room.users.iter().any(|user| user.id == conn.user.id) {
                        continue;
                    }
                    room.users.retain(|user| user.id != conn.user.id);
                    room_events.push(room.json().to_string());
                }
                self.union_rooms.retain(|_, room| !room.users.is_empty());
                for room_json in room_events {
                    let snapshot = union_response(121, None, None, Some(&room_json));
                    for recipient in self.union_recipients() {
                        notifications.push((recipient, snapshot.clone()));
                    }
                }
                let exit = union_response(
                    3,
                    None,
                    None,
                    Some(
                        &serde_json::to_string(&UnionMember::from(conn.user.clone()))
                            .unwrap_or_default(),
                    ),
                );
                notifications.extend(
                    self.sessions
                        .values()
                        .filter(|other| other.family == Family::Union)
                        .map(|other| (other.tx.clone(), exit.clone())),
                );
            }
            Family::Classic => {}
        }
        Some(Disconnect { notifications, sim })
    }

    fn union_users(&self) -> Vec<UnionMember> {
        self.sessions
            .values()
            .filter(|conn| conn.family == Family::Union)
            .map(|conn| conn.user.clone().into())
            .collect()
    }

    fn union_recipients(&self) -> Vec<mpsc::UnboundedSender<Outbound>> {
        self.sessions
            .values()
            .filter(|conn| conn.family == Family::Union)
            .map(|conn| conn.tx.clone())
            .collect()
    }

    fn sim_snapshot(
        &self,
        room_id: i32,
        session_id: &str,
    ) -> Option<(SimMember, Vec<(mpsc::UnboundedSender<Outbound>, SimMember)>)> {
        let room = self.simulation_rooms.get(&room_id)?;
        let sender = self.sessions.get(session_id)?.sim_member.clone()?;
        let recipients = room
            .members
            .iter()
            .filter(|id| id.as_str() != session_id)
            .filter_map(|id| {
                self.sessions.get(id).and_then(|conn| {
                    conn.sim_member
                        .clone()
                        .map(|member| (conn.tx.clone(), member))
                })
            })
            .collect();
        Some((sender, recipients))
    }

    fn general_snapshot(
        &self,
        kind: GeneralKind,
        train_id: &str,
        session_id: &str,
    ) -> Option<(i32, Vec<(mpsc::UnboundedSender<Outbound>, i32)>)> {
        let key = GeneralRoomKey {
            kind,
            train_id: train_id.to_owned(),
        };
        let sender = self.sessions.get(session_id)?.role.unwrap_or(0);
        let recipients = self
            .general_rooms
            .get(&key)?
            .iter()
            .filter(|id| id.as_str() != session_id)
            .filter_map(|id| {
                self.sessions
                    .get(id)
                    .map(|conn| (conn.tx.clone(), conn.role.unwrap_or(0)))
            })
            .collect();
        Some((sender, recipients))
    }
}

/// Java `RoomLifecycleLocks.generalKeyRoom`: opening a general-training room (role lookup plus
/// registration) and deleting the training (commit plus closing its room) exclude each other, so
/// a socket whose role lookup preceded the delete cannot register after the room was closed.
/// Striped by (kind, train id) so the table never grows; unrelated trainings sharing a stripe
/// merely wait for each other for the duration of one lookup or one delete.
static ROOM_LIFECYCLE: LazyLock<Vec<Arc<Mutex<()>>>> =
    LazyLock::new(|| (0..64).map(|_| Arc::new(Mutex::new(()))).collect());

async fn lock_room_lifecycle(
    kind: GeneralKind,
    train_id: &str,
) -> tokio::sync::OwnedMutexGuard<()> {
    use std::hash::{Hash, Hasher};
    let mut hasher = std::collections::hash_map::DefaultHasher::new();
    kind.hash(&mut hasher);
    train_id.hash(&mut hasher);
    ROOM_LIFECYCLE[(hasher.finish() % 64) as usize]
        .clone()
        .lock_owned()
        .await
}

pub(crate) async fn general_room_lifecycle(
    kind: &str,
    train_id: &str,
) -> Option<tokio::sync::OwnedMutexGuard<()>> {
    let kind = parse_general_kind(kind)?;
    let train_id = match kind {
        GeneralKind::Telex => train_id.to_owned(),
        _ => train_id
            .parse::<i32>()
            .map_or_else(|_| train_id.to_owned(), |id| id.to_string()),
    };
    Some(lock_room_lifecycle(kind, &train_id).await)
}

/// The room a connection is about to open, as the registration will name it.
async fn lock_route_room(route: &RouteSpec) -> Option<tokio::sync::OwnedMutexGuard<()>> {
    let kind = route.general_kind()?;
    let train_id = match route {
        RouteSpec::GeneralKey { raw_train_id } | RouteSpec::GeneralTicker { raw_train_id, .. } => {
            raw_train_id.parse::<i32>().ok()?.to_string()
        }
        RouteSpec::GeneralTelex { train_id } => train_id.clone(),
        _ => return None,
    };
    Some(lock_room_lifecycle(kind, &train_id).await)
}

static MANAGER: LazyLock<Arc<Mutex<WsManager>>> =
    LazyLock::new(|| Arc::new(Mutex::new(WsManager::default())));

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

pub(crate) async fn status() -> Response {
    let manager = MANAGER.lock().await;
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"connections": manager.sessions.len(), "rooms": manager.room_count()})),
        "ok",
        "",
    )
}
pub(crate) async fn status_open(upgrade: WebSocketUpgrade) -> Response {
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
fn parse_general_kind(value: &str) -> Option<GeneralKind> {
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
    let payload =
        json!({"type":"result","topic":"result","roomId":room_id,"userId":user_id}).to_string();
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
pub(crate) async fn open(
    State(state): State<AppState>,
    OriginalUri(uri): OriginalUri,
    Query(auth): Query<WsAuth>,
    upgrade: WebSocketUpgrade,
) -> Response {
    let route = route_spec(uri.path());
    let token = auth.token.unwrap_or_default();
    let device_id = auth.device_id.unwrap_or_default();
    upgrade.on_upgrade(move |socket| serve(socket, state, route, token, device_id))
}

fn route_spec(path: &str) -> RouteSpec {
    let parts: Vec<&str> = path.trim_matches('/').split('/').collect();
    let at = |name: &str| parts.iter().position(|part| *part == name);
    if let Some(index) = at("websocketUnion") {
        if parts.get(index + 1).is_some() {
            return RouteSpec::Union;
        }
    }
    if let Some(index) = at("websocket").or_else(|| at("startWebsocket")) {
        if parts.get(index + 1).is_some() {
            return RouteSpec::Classic;
        }
    }
    if let Some(index) = at("simulation") {
        if let Some(raw_room_id) = parts.get(index + 2) {
            return RouteSpec::Simulation {
                raw_room_id: (*raw_room_id).to_owned(),
            };
        }
    }
    if let Some(index) = at("generalKeyPatTrain") {
        if let Some(raw_train_id) = parts.get(index + 2) {
            return RouteSpec::GeneralKey {
                raw_train_id: (*raw_train_id).to_owned(),
            };
        }
    }
    if let Some(index) = at("generalTelexPatTrain") {
        if let Some(train_id) = parts.get(index + 2) {
            return RouteSpec::GeneralTelex {
                train_id: (*train_id).to_owned(),
            };
        }
    }
    if let Some(index) = at("generalTickerPat") {
        if let (Some(raw_train_id), Some(raw_role)) = (parts.get(index + 2), parts.get(index + 3)) {
            return RouteSpec::GeneralTicker {
                raw_train_id: (*raw_train_id).to_owned(),
                raw_role: (*raw_role).to_owned(),
            };
        }
    }
    RouteSpec::Unknown
}

async fn serve(
    mut socket: WebSocket,
    state: AppState,
    route: RouteSpec,
    token: String,
    device_id: String,
) {
    let family = route.family();
    let (tx, mut outgoing) = mpsc::unbounded_channel();
    let room_guard = lock_route_room(&route).await;
    let prepared = match prepare(&state, route.clone(), &token, &device_id).await {
        Ok(prepared) => prepared,
        Err((error, authenticated_id)) => {
            if let Err(send_error) = socket
                .send(Message::Text(
                    handshake_error(family, &error, authenticated_id.as_deref()).into(),
                ))
                .await
            {
                tracing::warn!(%send_error, ?family, "websocket handshake error delivery failed");
            }
            if let Err(error) = socket.send(normal_close()).await {
                tracing::debug!(%error, ?family, "websocket close delivery failed");
            }
            return;
        }
    };
    let registration = {
        let mut manager = MANAGER.lock().await;
        manager.register(&prepared, tx)
    };
    drop(room_guard);
    for (old, with_error) in registration.replaced {
        if with_error {
            if old
                .send(Outbound::Text(union_or_classic_close(family)))
                .is_err()
            {
                tracing::warn!(?family, "websocket outbound queue closed");
            }
        }
        // WebSocketService only tells a replaced classic session to close and leaves the
        // disconnect to the client; its next business message is rejected below. Every other
        // family closes the replaced session server-side.
        if !matches!(family, Family::Classic) && old.send(Outbound::Close).is_err() {
            tracing::warn!(?family, "websocket outbound queue closed");
        }
    }
    for (recipient, message) in registration.notifications {
        if recipient.send(Outbound::Text(message)).is_err() {
            tracing::warn!(?family, "websocket outbound queue closed");
        }
    }

    let session_id = registration.session_id;
    let generation = registration.generation;
    // Business messages run on a per-connection worker in arrival order, so a handler waiting on a
    // row lock (up to innodb_lock_wait_timeout) no longer stalls this connection's heartbeats and
    // outbound frames. Cleanup below waits for the queued messages first: the Java container never
    // runs onClose concurrently with onMessage of the same session.
    let (work, mut queue) = mpsc::unbounded_channel::<String>();
    let worker = {
        let (state, prepared, session_id) = (state.clone(), prepared.clone(), session_id.clone());
        tokio::spawn(async move {
            while let Some(text) = queue.recv().await {
                dispatch(&state, &prepared, &session_id, &text).await;
            }
        })
    };
    // A replaced classic session loses its outbound queue but stays connected (see above).
    let mut addressable = true;
    loop {
        tokio::select! {
            command = outgoing.recv(), if addressable => {
                match command {
                    Some(Outbound::Text(text)) => {
                        if let Err(error) = socket.send(Message::Text(text.into())).await {
                            tracing::warn!(%error, ?family, session_id, "websocket outbound delivery failed");
                            break;
                        }
                    }
                    Some(Outbound::Close) => {
                        if let Err(error) = socket.send(normal_close()).await {
                            tracing::debug!(%error, ?family, "websocket close delivery failed");
                        }
                        break;
                    }
                    None if matches!(family, Family::Classic) => addressable = false,
                    None => break,
                }
            }
            incoming = socket.recv() => {
                let Some(incoming) = incoming else { break; };
                let Ok(message) = incoming else { break; };
                match message {
                    Message::Text(text) => {
                        let text = text.to_string();
                        if text == HEARTBEAT_PING {
                            if let Err(error) = socket.send(Message::Text(HEARTBEAT_PONG.into())).await {
                            tracing::warn!(%error, ?family, session_id, "websocket outbound delivery failed");
                            break;
                        }
                            continue;
                        }
                        let active = MANAGER.lock().await.active(&session_id, generation);
                        if !active {
                            if matches!(family, Family::Classic) {
                                let rejected = classic_response(1, "当前连接已失效");
                                if socket.send(Message::Text(rejected.into())).await.is_ok() {
                                    let _ = socket.send(normal_close()).await;
                                }
                            }
                            break;
                        }
                        if work.send(text).is_err() { break; }
                    }
                    Message::Ping(data) => {
                        if let Err(error) = socket.send(Message::Pong(data)).await {
                            tracing::warn!(%error, ?family, session_id, "websocket outbound delivery failed");
                            break;
                        }
                    }
                    Message::Close(_) => {
                        finish_peer_close(&mut socket).await;
                        break;
                    }
                    Message::Binary(_) | Message::Pong(_) => {}
                }
            }
        }
    }
    drop(work);
    if let Err(error) = worker.await {
        tracing::error!(%error, ?family, session_id, "websocket message worker failed");
    }
    let disconnect = {
        let mut manager = MANAGER.lock().await;
        manager.disconnect(&session_id, generation)
    };
    if let Some(disconnect) = disconnect {
        for (recipient, message) in disconnect.notifications {
            if recipient.send(Outbound::Text(message)).is_err() {
                tracing::warn!(?family, "websocket outbound queue closed");
            }
        }
        if let Some(sim) = disconnect.sim {
            simulation_close(&state, sim).await;
        }
    }
}

async fn prepare(
    state: &AppState,
    route: RouteSpec,
    token: &str,
    device_id: &str,
) -> Result<Prepared, (String, Option<String>)> {
    let (authenticated, mut user) = async {
        if token.is_empty() || device_id.is_empty() {
            return Err("登录凭据无效，拒绝建立连接".to_owned());
        }
        let authenticated = lookup_user(state, token, device_id)
            .await
            .map_err(|error| {
                tracing::error!(?error, "websocket authentication lookup failed");
                "服务器错误".to_owned()
            })?
            .ok_or_else(|| "登录凭据无效，拒绝建立连接".to_owned())?;
        let user = load_ws_user(state, &authenticated.id)
            .await?
            .ok_or_else(|| "登录凭据无效，拒绝建立连接".to_owned())?;
        Ok::<_, String>((authenticated, user))
    }
    .await
    .map_err(|message| (message, None))?;
    let result = async {
    match route.clone() {
        RouteSpec::Classic => Ok(Prepared {
            route,
            family: Family::Classic,
            scope: String::new(),
            user,
            role: None,
            sim_room_id: None,
            sim_room_type: None,
            sim_creator: None,
            sim_member: None,
        }),
        RouteSpec::Union => Ok(Prepared {
            route,
            family: Family::Union,
            scope: String::new(),
            user,
            role: None,
            sim_room_id: None,
            sim_room_type: None,
            sim_creator: None,
            sim_member: None,
        }),
        RouteSpec::GeneralKey { raw_train_id } => {
            let train_id = raw_train_id
                .parse::<i32>()
                .map_err(|_| "训练id无效".to_owned())?;
            let role = general_role(
                state,
                GeneralKind::Key,
                &authenticated.id,
                &train_id.to_string(),
            )
            .await?;
            Ok(Prepared {
                route,
                family: Family::GeneralKey,
                scope: train_id.to_string(),
                user,
                role,
                sim_room_id: None,
                sim_room_type: None,
                sim_creator: None,
                sim_member: None,
            })
        }
        RouteSpec::GeneralTelex { train_id } => {
            let role =
                general_role(state, GeneralKind::Telex, &authenticated.id, &train_id).await?;
            Ok(Prepared {
                route,
                family: Family::GeneralTelex,
                scope: train_id,
                user,
                role,
                sim_room_id: None,
                sim_room_type: None,
                sim_creator: None,
                sim_member: None,
            })
        }
        RouteSpec::GeneralTicker {
            raw_train_id,
            raw_role,
        } => {
            let train_id = raw_train_id
                .parse::<i32>()
                .map_err(|_| "训练id或角色无效".to_owned())?;
            let path_role = raw_role
                .parse::<i32>()
                .map_err(|_| "训练id或角色无效".to_owned())?;
            let role = general_role(
                state,
                GeneralKind::Ticker,
                &authenticated.id,
                &train_id.to_string(),
            )
            .await?;
            if role != Some(path_role) {
                return Err("训练数据异常".to_owned());
            }
            Ok(Prepared {
                route,
                family: Family::GeneralTicker,
                scope: train_id.to_string(),
                user,
                role,
                sim_room_id: None,
                sim_room_type: None,
                sim_creator: None,
                sim_member: None,
            })
        }
        RouteSpec::Simulation { raw_room_id } => {
            let room_id = raw_room_id
                .parse::<i32>()
                .map_err(|_| "房间id无效".to_owned())?;
            let Some(pool) = &state.pool else {
                tracing::error!(room_id, "websocket simulation database unavailable");
                return Err("服务器错误".to_owned());
            };
            let room = sqlx::query(
                "SELECT r.room_type, r.create_user_id, u.user_account FROM simulation_router_room r LEFT JOIN t_user u ON u.id=r.create_user_id WHERE r.id=? LIMIT 1",
            )
            .bind(room_id)
            .fetch_optional(pool)
            .await
            .map_err(|error| {
                tracing::error!(%error, room_id, "websocket simulation room lookup failed");
                "服务器错误".to_owned()
            })?;
            let Some(room) = room else {
                return Err("人员或房间信息未找到".to_owned());
            };
            let room_type = room.get::<Option<i32>, _>("room_type").unwrap_or(-1);
            let creator = room
                .get::<Option<String>, _>("create_user_id")
                .unwrap_or_default();
            let member_row = sqlx::query("SELECT user_type, channel FROM simulation_router_room_user WHERE room_id=? AND user_id=? LIMIT 1").bind(room_id).bind(&authenticated.id).fetch_optional(pool).await.map_err(|error| {
                tracing::error!(%error, room_id, user_id = %authenticated.id, "websocket simulation member lookup failed");
                "服务器错误".to_owned()
            })?;
            if !(0..=3).contains(&room_type)
                || (member_row.is_none()
                    && (creator != authenticated.id || matches!(room_type, 2 | 3)))
            {
                return Err("人员或房间信息未找到".to_owned());
            }
            if member_row.is_none() {
                user.name = room.get("user_account");
            }
            let member = if let Some(row) = member_row {
                SimMember {
                    user: user.clone(),
                    user_type: row.get("user_type"),
                    channel: row.get("channel"),
                    status: 1,
                }
            } else {
                SimMember {
                    user: user.clone(),
                    user_type: None,
                    channel: Some(-1),
                    status: 1,
                }
            };
            Ok(Prepared {
                route,
                family: Family::Simulation,
                scope: room_id.to_string(),
                user,
                role: member.user_type,
                sim_room_id: Some(room_id),
                sim_room_type: Some(room_type),
                sim_creator: Some(creator),
                sim_member: Some(member),
            })
        }
        RouteSpec::Unknown => Err("连接路径无效".to_owned()),
    }
    }.await;
    result.map_err(|message| (message, Some(authenticated.id)))
}

async fn load_ws_user(state: &AppState, user_id: &str) -> Result<Option<WsUser>, String> {
    let pool = state.pool.as_ref().ok_or_else(|| {
        tracing::error!(user_id, "websocket user database unavailable");
        "服务器错误".to_owned()
    })?;
    let row = sqlx::query("SELECT user_name, user_img FROM t_user WHERE id=? LIMIT 1")
        .bind(user_id)
        .fetch_optional(pool)
        .await
        .map_err(|error| {
            tracing::error!(%error, user_id, "websocket user lookup failed");
            "服务器错误".to_owned()
        })?;
    Ok(row.map(|row| WsUser {
        id: user_id.to_owned(),
        name: row.get("user_name"),
        user_img: row.get("user_img"),
    }))
}

async fn general_role(
    state: &AppState,
    kind: GeneralKind,
    user_id: &str,
    train_id: &str,
) -> Result<Option<i32>, String> {
    let Some(pool) = &state.pool else {
        tracing::error!(
            ?kind,
            user_id,
            train_id,
            "websocket training database unavailable"
        );
        return Err("服务器错误".to_owned());
    };
    let sql = match kind {
        GeneralKind::Key => {
            "SELECT role FROM general_key_pat_user WHERE user_id=? AND train_id=? LIMIT 1"
        }
        GeneralKind::Telex => {
            "SELECT role FROM general_telex_pat_user WHERE user_id=? AND train_id=? LIMIT 1"
        }
        GeneralKind::Ticker => {
            "SELECT role FROM general_ticker_pat_train_user WHERE user_id=? AND train_id=? LIMIT 1"
        }
    };
    let row = if kind == GeneralKind::Key || kind == GeneralKind::Ticker {
        let Ok(id) = train_id.parse::<i32>() else {
            return Err("训练id无效".to_owned());
        };
        sqlx::query(sql)
            .bind(user_id)
            .bind(id)
            .fetch_optional(pool)
            .await
    } else {
        sqlx::query(sql)
            .bind(user_id)
            .bind(train_id)
            .fetch_optional(pool)
            .await
    }
    .map_err(|error| {
        tracing::error!(%error, ?kind, user_id, train_id, "websocket training role lookup failed");
        "服务器错误".to_owned()
    })?;
    row.map(|row| row.get("role"))
        .ok_or_else(|| "训练数据异常".to_owned())
}

async fn dispatch(state: &AppState, prepared: &Prepared, session_id: &str, text: &str) {
    match prepared.family {
        Family::Classic => classic_message(state, prepared, session_id, text).await,
        Family::Union => union_message(state, prepared, session_id, text).await,
        Family::Simulation => simulation_message(state, prepared, session_id, text).await,
        Family::GeneralKey | Family::GeneralTelex | Family::GeneralTicker => {
            general_message(prepared, session_id, text).await
        }
    }
}

async fn classic_message(state: &AppState, prepared: &Prepared, session_id: &str, text: &str) {
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
                crate::telegram_write::save_captured_log(pool, &prepared.user.id, data).await
            } else {
                crate::telegram_write::save_captured_content(pool, &prepared.user.id, data).await
            };
            if let Err((code, message)) = result {
                let _ = tx.send(Outbound::Text(classic_response(code, message)));
            }
        }
        _ => {}
    }
}

async fn current_tx(
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

fn json_int(value: Option<&Value>) -> Option<i64> {
    value.and_then(|value| {
        value
            .as_i64()
            .or_else(|| value.as_str().and_then(|text| text.parse().ok()))
    })
}

async fn union_message(_state: &AppState, prepared: &Prepared, session_id: &str, text: &str) {
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

async fn general_message(prepared: &Prepared, session_id: &str, text: &str) {
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

async fn send_family_error(session_id: &str, family: Family, message: &str) {
    let manager = MANAGER.lock().await;
    if let Some(conn) = manager.sessions.get(session_id) {
        let text = if family == Family::Simulation {
            sim_response(-1, message, &conn.user.id, &conn.user.id)
        } else {
            socket_response(-1, message, "", "")
        };
        if conn.tx.send(Outbound::Text(text)).is_err() {
            tracing::warn!(?family, "websocket outbound queue closed");
        }
    }
}

async fn simulation_message(state: &AppState, prepared: &Prepared, session_id: &str, text: &str) {
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
    let Some((sender, recipients)) = ({
        let manager = MANAGER.lock().await;
        manager.sim_snapshot(room_id, session_id)
    }) else {
        return;
    };
    if room_type == 1 {
        simulation_disturb(
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
        for (tx, member) in recipients {
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
        {
            let mut manager = MANAGER.lock().await;
            manager.set_sim_channel(session_id, channel);
        }
        for (tx, member) in recipients {
            if member.user_type == Some(0) {
                let _ = tx.send(Outbound::Text(sim_response(1, text, "", "")));
            }
        }
    } else if topic != "result" {
        send_session_sim_error(session_id, "消息格式错误").await;
    }
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
            if let Err(error) = sqlx::query("UPDATE simulation_router_room SET stats=1 WHERE id=?")
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
            if let Err(error) =
                sqlx::query("UPDATE simulation_router_room SET play_status=? WHERE id=?")
                    .bind(if topic == "2" { 0 } else { 1 })
                    .bind(room_id)
                    .execute(pool)
                    .await
            {
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
                if sender.user_type.is_none() || member.channel == sender.channel {
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
                if let Err(error) =
                    sqlx::query("UPDATE simulation_router_room SET stats=1 WHERE id=?")
                        .bind(room_id)
                        .execute(pool)
                        .await
                {
                    tracing::error!(%error, room_id, session_id, operation = "begin router", "simulation update failed");
                    send_session_sim_system_error(session_id).await;
                    return;
                }
            }
            for (tx, member) in recipients {
                if member.user_type.is_some() {
                    let _ = tx.send(Outbound::Text(sim_response(1, text, "", "")));
                }
            }
        }
        "end" => {
            if let Some(pool) = &state.pool {
                if let Err(error) =
                    sqlx::query("UPDATE simulation_router_room SET stats=2 WHERE id=?")
                        .bind(room_id)
                        .execute(pool)
                        .await
                {
                    tracing::error!(%error, room_id, session_id, operation = "end router", "simulation update failed");
                    send_session_sim_system_error(session_id).await;
                    return;
                }
            }
            for (tx, member) in recipients {
                if member.user_type.is_some() {
                    let _ = tx.send(Outbound::Text(sim_response(1, text, "", "")));
                }
            }
        }
        "over" | "change" => {
            for (tx, _) in recipients {
                let _ = tx.send(Outbound::Text(sim_response(1, text, "", "")));
            }
        }
        _ => send_session_sim_error(session_id, "消息格式错误").await,
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

async fn simulation_close(state: &AppState, close: SimClose) {
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

fn general_kind_for_family(family: Family) -> Option<GeneralKind> {
    match family {
        Family::GeneralKey => Some(GeneralKind::Key),
        Family::GeneralTelex => Some(GeneralKind::Telex),
        Family::GeneralTicker => Some(GeneralKind::Ticker),
        _ => None,
    }
}

fn scalar(value: Option<&Value>) -> Option<String> {
    match value? {
        Value::String(value) => Some(value.clone()),
        Value::Number(value) => Some(value.to_string()),
        Value::Bool(value) => Some(value.to_string()),
        Value::Null => None,
        _ => None,
    }
}
fn scalar_or_json(value: Option<&Value>) -> Option<String> {
    value.and_then(|value| scalar(Some(value)).or_else(|| serde_json::to_string(value).ok()))
}
fn value_text(value: &Value) -> String {
    scalar(Some(value)).unwrap_or_else(|| value.to_string())
}

fn classic_response(code: i32, data: &str) -> String {
    let mut object = Map::new();
    object.insert("code".to_owned(), json!(code));
    object.insert("data".to_owned(), json!(data));
    Value::Object(object).to_string()
}
fn union_response(
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
fn socket_response(code: i32, data: &str, send: &str, receive: &str) -> String {
    serde_json::to_string(&json!({"code":code,"sendName":send,"receiveName":receive,"data":data}))
        .unwrap_or_default()
}
fn general_notification_response(code: i32, map: Value) -> String {
    serde_json::to_string(&json!({"code": code, "map": map})).unwrap_or_default()
}
fn sim_response(code: i32, data: &str, send: &str, receive: &str) -> String {
    socket_response(code, data, send, receive)
}
fn handshake_error(family: Family, message: &str, authenticated_id: Option<&str>) -> String {
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
fn union_or_classic_close(family: Family) -> String {
    match family {
        Family::Union | Family::Classic => classic_response(1, "关闭连接"),
        _ => String::new(),
    }
}

#[cfg(test)]
#[path = "ws_worker_tests.rs"]
mod worker_tests;
