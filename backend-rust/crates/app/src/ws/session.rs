use super::dispatch::dispatch;
use super::manager::{lock_route_room, MANAGER};
use super::simulation::simulation_close;
use super::{classic_response, handshake_error, union_or_classic_close};
use super::{
    finish_peer_close, normal_close, Family, GeneralKind, Outbound, Prepared, RouteSpec, SimMember,
    WsAuth, WsUser, HEARTBEAT_PING, HEARTBEAT_PONG,
};
use crate::platform::auth::lookup_user;
use crate::platform::state::AppState;
use axum::extract::ws::rejection::WebSocketUpgradeRejection;
use axum::extract::ws::Message;
use axum::extract::ws::WebSocket;
use axum::extract::ws::WebSocketUpgrade;
use axum::extract::OriginalUri;
use axum::extract::Query;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::response::Response;
use sqlx::Row;
use tokio::sync::mpsc;

pub(super) async fn open(
    State(state): State<AppState>,
    OriginalUri(uri): OriginalUri,
    Query(auth): Query<WsAuth>,
    upgrade: Result<WebSocketUpgrade, WebSocketUpgradeRejection>,
) -> Response {
    let Ok(upgrade) = upgrade else {
        return StatusCode::NOT_FOUND.into_response();
    };
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
