use super::classic::classic_message;
use super::general::general_message;
use super::manager::MANAGER;
use super::simulation::simulation_message;
use super::union::union_message;
use super::{sim_response, socket_response, Family, Outbound, Prepared};
use crate::platform::state::AppState;

pub(super) async fn dispatch(state: &AppState, prepared: &Prepared, session_id: &str, text: &str) {
    match prepared.family {
        Family::Classic => classic_message(state, prepared, session_id, text).await,
        Family::Union => union_message(state, prepared, session_id, text).await,
        Family::Simulation => simulation_message(state, prepared, session_id, text).await,
        Family::GeneralKey | Family::GeneralTelex | Family::GeneralTicker => {
            general_message(prepared, session_id, text).await
        }
    }
}

pub(super) async fn send_family_error(session_id: &str, family: Family, message: &str) {
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
