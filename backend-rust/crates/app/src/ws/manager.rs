use super::{general_kind_for_family, sim_response, socket_response, union_response};
use super::{
    Connection, Family, GeneralKind, GeneralRoomKey, Outbound, Prepared, RouteSpec, SimMember,
    SimRoom, UnionMember, UnionRoom,
};
use serde_json::json;
use serde_json::Value;
use std::collections::HashMap;
use std::sync::Arc;
use std::sync::LazyLock;
use tokio::sync::mpsc;
use tokio::sync::Mutex;
use uuid::Uuid;

#[derive(Default)]
pub(super) struct WsManager {
    next_generation: u64,
    pub(super) sessions: HashMap<String, Connection>,
    pub(super) current_by_key: HashMap<String, String>,
    pub(super) general_rooms: HashMap<GeneralRoomKey, Vec<String>>,
    pub(super) simulation_rooms: HashMap<i32, SimRoom>,
    pub(super) union_rooms: HashMap<String, UnionRoom>,
}

pub(super) struct Registration {
    pub(super) session_id: String,
    pub(super) generation: u64,
    pub(super) replaced: Vec<(mpsc::UnboundedSender<Outbound>, bool)>,
    pub(super) notifications: Vec<(mpsc::UnboundedSender<Outbound>, String)>,
}

pub(super) struct SimClose {
    pub(super) room_id: i32,
    pub(super) room_type: i32,
    pub(super) member: SimMember,
    pub(super) remaining: Vec<SimMember>,
}

pub(super) struct Disconnect {
    pub(super) notifications: Vec<(mpsc::UnboundedSender<Outbound>, String)>,
    pub(super) sim: Option<SimClose>,
}

impl WsManager {
    pub(super) fn room_count(&self) -> usize {
        self.general_rooms.len() + self.simulation_rooms.len() + self.union_rooms.len()
    }

    pub(super) fn register(
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
                    let mut body = json!({
                        "id": prepared.user.id,
                        "userName": prepared.user.name,
                        "userImg": prepared.user.user_img,
                        "channel": member.channel,
                    });
                    // Java serialises the body with Gson, which leaves null fields out.
                    if let Some(object) = body.as_object_mut() {
                        object.retain(|_, value| !value.is_null());
                    }
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

    pub(super) fn active(&self, session_id: &str, generation: u64) -> bool {
        self.sessions
            .get(session_id)
            .is_some_and(|conn| conn.generation == generation)
    }

    pub(super) fn set_general_status(&mut self, session_id: &str, status: i32) {
        if let Some(conn) = self.sessions.get_mut(session_id) {
            conn.status = status;
        }
    }

    pub(super) fn set_sim_channel(&mut self, session_id: &str, channel: i32) {
        if let Some(conn) = self.sessions.get_mut(session_id) {
            if let Some(member) = conn.sim_member.as_mut() {
                member.channel = Some(channel);
            }
        }
    }

    pub(super) fn union_room_json(&self, id: &str) -> Option<Value> {
        self.union_rooms.get(id).map(UnionRoom::json)
    }

    pub(super) fn disconnect(&mut self, session_id: &str, generation: u64) -> Option<Disconnect> {
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

    pub(super) fn union_users(&self) -> Vec<UnionMember> {
        self.sessions
            .values()
            .filter(|conn| conn.family == Family::Union)
            .map(|conn| conn.user.clone().into())
            .collect()
    }

    pub(super) fn union_recipients(&self) -> Vec<mpsc::UnboundedSender<Outbound>> {
        self.sessions
            .values()
            .filter(|conn| conn.family == Family::Union)
            .map(|conn| conn.tx.clone())
            .collect()
    }

    pub(super) fn sim_snapshot(
        &self,
        room_id: i32,
        session_id: &str,
    ) -> Option<(SimMember, Vec<(mpsc::UnboundedSender<Outbound>, SimMember)>)> {
        let room = self.simulation_rooms.get(&room_id)?;
        let sender = self.sessions.get(session_id)?.sim_member.clone()?;
        let recipients = room
            .members
            .iter()
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

    pub(super) fn general_snapshot(
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

pub(super) async fn lock_room_lifecycle(
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

/// The room a connection is about to open, as the registration will name it.
pub(super) async fn lock_route_room(route: &RouteSpec) -> Option<tokio::sync::OwnedMutexGuard<()>> {
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

pub(super) static MANAGER: LazyLock<Arc<Mutex<WsManager>>> =
    LazyLock::new(|| Arc::new(Mutex::new(WsManager::default())));
