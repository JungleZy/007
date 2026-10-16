use std::sync::atomic::Ordering;

use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde::Serialize;
use serde_json::json;
use tokio::time::timeout;

use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::state::AppState;

#[derive(Serialize)]
pub(crate) struct HealthResponse {
    status: &'static str,
}

#[derive(Serialize)]
pub(crate) struct ReadinessResponse {
    status: &'static str,
    readiness: &'static str,
    dependencies: bool,
}

#[derive(Serialize)]
struct AuthProbeResponse {
    user_id: String,
    is_admin: bool,
}

pub(crate) async fn refresh_database_readiness(state: &AppState) {
    let ready = match &state.pool {
        Some(pool) => timeout(
            state.config.db_timeout,
            sqlx::query("SELECT 1").execute(pool),
        )
        .await
        .map_or(false, |result| result.is_ok()),
        None => false,
    };
    state.database_ready.store(ready, Ordering::Release);
}

pub(crate) async fn health() -> (StatusCode, Json<HealthResponse>) {
    (StatusCode::OK, Json(HealthResponse { status: "ok" }))
}

pub(crate) async fn live() -> (StatusCode, Json<HealthResponse>) {
    (StatusCode::OK, Json(HealthResponse { status: "ok" }))
}

pub(crate) async fn ready(State(state): State<AppState>) -> (StatusCode, Json<ReadinessResponse>) {
    refresh_database_readiness(&state).await;
    if state.pool.is_none() {
        return (
            StatusCode::OK,
            Json(ReadinessResponse {
                status: "ready",
                readiness: "process-only",
                dependencies: false,
            }),
        );
    }
    if state.database_ready.load(Ordering::Acquire) {
        (
            StatusCode::OK,
            Json(ReadinessResponse {
                status: "ready",
                readiness: "dependencies-ready",
                dependencies: true,
            }),
        )
    } else {
        (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(ReadinessResponse {
                status: "not-ready",
                readiness: "database-unavailable",
                dependencies: false,
            }),
        )
    }
}

pub(crate) async fn auth_probe(Extension(user): Extension<AuthUser>) -> Response {
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!(AuthProbeResponse {
            user_id: user.id,
            is_admin: user.is_admin
        })),
        "ok",
        "",
    )
}

pub(crate) async fn admin_probe(Extension(user): Extension<AuthUser>) -> Response {
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!(AuthProbeResponse {
            user_id: user.id,
            is_admin: user.is_admin
        })),
        "ok",
        "",
    )
}
