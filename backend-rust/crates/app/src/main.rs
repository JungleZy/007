use std::{
    env,
    net::SocketAddr,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
    time::Duration,
};

use axum::{
    extract::{Extension, Request, State},
    http::{HeaderValue, StatusCode},
    middleware::{self, Next},
    response::{IntoResponse, Response},
    routing::get,
    Json, Router,
};
use serde::Serialize;
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use sqlx::{mysql::MySqlPoolOptions, MySqlPool, Row};
use thiserror::Error;
use tokio::{net::TcpListener, signal, time::timeout};
use tower_http::{cors::CorsLayer, limit::RequestBodyLimitLayer};
use tracing::{error, info, warn};
use uuid::Uuid;

const LISTEN_ADDR_ENV: &str = "RUST_BACKEND_ADDR";
const DATABASE_URL_ENV: &str = "RUST_BACKEND_DATABASE_URL";
const DEFAULT_LISTEN_ADDR: &str = "127.0.0.1:18002";
const DEFAULT_BODY_LIMIT: usize = 11 * 1024 * 1024;

#[derive(Debug, Error)]
enum AppError {
    #[error("configuration error: {0}")]
    Config(String),
    #[error("failed to bind HTTP listener: {0}")]
    Bind(#[source] std::io::Error),
    #[error("invalid database configuration: {0}")]
    Database(#[from] sqlx::Error),
}

#[derive(Clone, Debug)]
struct AppConfig {
    listen_addr: SocketAddr,
    database_url: Option<String>,
    max_connections: u32,
    db_timeout: Duration,
    body_limit: usize,
    cors_origin: String,
}

#[derive(Clone)]
struct AppState {
    config: Arc<AppConfig>,
    pool: Option<MySqlPool>,
    database_ready: Arc<AtomicBool>,
}
#[derive(Clone, Debug)]
struct AuthUser {
    id: String,
    is_admin: bool,
}

#[derive(Serialize)]
struct HealthResponse {
    status: &'static str,
}

#[derive(Serialize)]
struct ReadinessResponse {
    status: &'static str,
    readiness: &'static str,
    dependencies: bool,
}

#[derive(Serialize)]
struct Envelope<T> {
    code: i32,
    data: Option<T>,
    message: String,
    description: String,
}

#[derive(Serialize)]
struct AuthProbeResponse {
    user_id: String,
    is_admin: bool,
}

#[tokio::main]
async fn main() {
    init_tracing();
    if let Err(error) = run().await {
        error!(error = %error, "rust backend stopped with an error");
        std::process::exit(1);
    }
}

async fn run() -> Result<(), AppError> {
    let config = Arc::new(AppConfig::from_env()?);
    let state = build_state(config.clone()).await?;
    let listener = TcpListener::bind(config.listen_addr)
        .await
        .map_err(AppError::Bind)?;
    let app = build_router(state.clone());

    info!(
        listen_addr = %config.listen_addr,
        database_configured = config.database_url.is_some(),
        max_connections = config.max_connections,
        body_limit = config.body_limit,
        "rust backend listening"
    );
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await
        .map_err(|error| AppError::Bind(std::io::Error::other(error)))?;
    info!("rust backend stopped gracefully");
    Ok(())
}

impl AppConfig {
    fn from_env() -> Result<Self, AppError> {
        let listen_addr = parse_socket_addr(LISTEN_ADDR_ENV, DEFAULT_LISTEN_ADDR)?;
        let database_url = env::var(DATABASE_URL_ENV)
            .ok()
            .filter(|value| !value.trim().is_empty());
        let max_connections = parse_u32("RUST_BACKEND_MAX_CONNECTIONS", 10)?;
        if max_connections == 0 {
            return Err(AppError::Config(
                "RUST_BACKEND_MAX_CONNECTIONS must be positive".into(),
            ));
        }
        let timeout_seconds = parse_u64("RUST_BACKEND_DB_TIMEOUT_SECS", 5)?;
        if timeout_seconds == 0 {
            return Err(AppError::Config(
                "RUST_BACKEND_DB_TIMEOUT_SECS must be positive".into(),
            ));
        }
        let body_limit = parse_usize("RUST_BACKEND_BODY_LIMIT_BYTES", DEFAULT_BODY_LIMIT)?;
        if body_limit == 0 {
            return Err(AppError::Config(
                "RUST_BACKEND_BODY_LIMIT_BYTES must be positive".into(),
            ));
        }
        let cors_origin = env::var("RUST_BACKEND_CORS_ORIGIN").unwrap_or_else(|_| "*".into());
        if cors_origin != "*" {
            cors_origin.parse::<HeaderValue>().map_err(|_| {
                AppError::Config("RUST_BACKEND_CORS_ORIGIN must be '*' or one valid origin".into())
            })?;
        }
        Ok(Self {
            listen_addr,
            database_url,
            max_connections,
            db_timeout: Duration::from_secs(timeout_seconds),
            body_limit,
            cors_origin,
        })
    }
}

async fn build_state(config: Arc<AppConfig>) -> Result<AppState, AppError> {
    let pool = match config.database_url.as_deref() {
        Some(url) => Some(
            MySqlPoolOptions::new()
                .max_connections(config.max_connections)
                .acquire_timeout(config.db_timeout)
                .connect_lazy(url)?,
        ),
        None => None,
    };
    let state = AppState {
        config,
        pool,
        database_ready: Arc::new(AtomicBool::new(false)),
    };
    refresh_database_readiness(&state).await;
    Ok(state)
}

fn build_router(state: AppState) -> Router {
    let protected = Router::new()
        .route("/auth", get(auth_probe))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin = Router::new()
        .route("/admin", get(admin_probe))
        .route_layer(middleware::from_fn_with_state(state.clone(), require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let api = Router::new()
        .nest("/api/phase1/contract", protected.merge(admin))
        .route("/q/openapi", get(openapi));
    let cors = if state.config.cors_origin == "*" {
        CorsLayer::permissive()
    } else {
        CorsLayer::new()
            .allow_origin(
                state
                    .config
                    .cors_origin
                    .parse::<HeaderValue>()
                    .expect("validated origin"),
            )
            .allow_methods(tower_http::cors::Any)
            .allow_headers(tower_http::cors::Any)
    };
    Router::new()
        .route("/health", get(health))
        .route("/live", get(live))
        .route("/ready", get(ready))
        .merge(api)
        .layer(RequestBodyLimitLayer::new(state.config.body_limit))
        .layer(cors)
        .layer(middleware::from_fn(request_id))
        .with_state(state)
}

async fn request_id(request: Request, next: Next) -> Response {
    let id = request
        .headers()
        .get("x-request-id")
        .and_then(|value| value.to_str().ok())
        .filter(|value| !value.is_empty() && value.len() <= 128)
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| Uuid::new_v4().to_string());
    let method = request.method().clone();
    let uri = request.uri().path().to_owned();
    let mut response = next.run(request).await;
    if let Ok(header_value) = HeaderValue::from_str(&id) {
        response.headers_mut().insert("x-request-id", header_value);
    }
    info!(request_id = %id, method = %method, path = %uri, status = response.status().as_u16(), "request completed");
    response
}

async fn authenticate(State(state): State<AppState>, mut request: Request, next: Next) -> Response {
    let token = match header_text(&request, "token") {
        Some(value) => value,
        None => return envelope_response(StatusCode::OK, 203, "token不能为空", ""),
    };
    let device_id = match header_text(&request, "deviceId") {
        Some(value) => value,
        None => return envelope_response(StatusCode::OK, 204, "设备标识不能为空", ""),
    };
    match lookup_user(&state, token, device_id).await {
        Ok(Some(user)) => {
            request.extensions_mut().insert(user);
            next.run(request).await
        }
        Ok(None) => envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(error) => {
            warn!(error = %error, "authentication dependency failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

async fn require_admin(request: Request, next: Next) -> Response {
    let Some(user) = request.extensions().get::<AuthUser>() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    if !user.is_admin {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    next.run(request).await
}

async fn lookup_user(
    state: &AppState,
    token: &str,
    device_id: &str,
) -> Result<Option<AuthUser>, sqlx::Error> {
    let Some(pool) = &state.pool else {
        return Ok(None);
    };
    let digest = token_digest(token);
    let row = sqlx::query(
        "SELECT u.id, EXISTS(SELECT 1 FROM t_user_role ur JOIN t_role r ON r.id = ur.role_id WHERE ur.user_id = u.id AND r.is_admin = 0) AS is_admin FROM t_user u WHERE u.token = ? AND u.device_id = ? LIMIT 1",
    )
    .bind(digest)
    .bind(device_id)
    .fetch_optional(pool)
    .await?;
    Ok(row.map(|row| AuthUser {
        id: row.get::<String, _>("id"),
        is_admin: row.get::<i64, _>("is_admin") != 0,
    }))
}

async fn refresh_database_readiness(state: &AppState) {
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

async fn health() -> (StatusCode, Json<HealthResponse>) {
    (StatusCode::OK, Json(HealthResponse { status: "ok" }))
}

async fn live() -> (StatusCode, Json<HealthResponse>) {
    (StatusCode::OK, Json(HealthResponse { status: "ok" }))
}

async fn ready(State(state): State<AppState>) -> (StatusCode, Json<ReadinessResponse>) {
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

async fn auth_probe(Extension(user): Extension<AuthUser>) -> Response {
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

async fn admin_probe(Extension(user): Extension<AuthUser>) -> Response {
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

async fn openapi() -> Json<Value> {
    Json(json!({
        "openapi": "3.1.0",
        "info": {"title": "Rust backend Phase 1 infrastructure", "version": "0.1.0"},
        "paths": {
            "/api/phase1/contract/auth": {"get": {"responses": {"200": {"description": "Response envelope"}}}},
            "/api/phase1/contract/admin": {"get": {"responses": {"200": {"description": "Response envelope"}}}}
        }
    }))
}

fn envelope_response(status: StatusCode, code: i32, message: &str, description: &str) -> Response {
    envelope_json(status, code, None, message, description)
}

fn envelope_json(
    status: StatusCode,
    code: i32,
    data: Option<Value>,
    message: &str,
    description: &str,
) -> Response {
    (
        status,
        Json(Envelope {
            code,
            data,
            message: message.to_owned(),
            description: description.to_owned(),
        }),
    )
        .into_response()
}

fn header_text<'a>(request: &'a Request, name: &str) -> Option<&'a str> {
    request
        .headers()
        .get(name)
        .and_then(|value| value.to_str().ok())
        .filter(|value| !value.is_empty())
}

fn token_digest(token: &str) -> String {
    let mut digest = Sha256::new();
    digest.update(token.as_bytes());
    format!("{:x}", digest.finalize())
}

fn parse_socket_addr(name: &str, default: &str) -> Result<SocketAddr, AppError> {
    let value = env::var(name).unwrap_or_else(|_| default.to_owned());
    value
        .parse()
        .map_err(|error| AppError::Config(format!("invalid {name} value {value:?}: {error}")))
}

fn parse_u32(name: &str, default: u32) -> Result<u32, AppError> {
    env::var(name).map_or(Ok(default), |value| {
        value
            .parse()
            .map_err(|_| AppError::Config(format!("{name} must be an integer")))
    })
}

fn parse_u64(name: &str, default: u64) -> Result<u64, AppError> {
    env::var(name).map_or(Ok(default), |value| {
        value
            .parse()
            .map_err(|_| AppError::Config(format!("{name} must be an integer")))
    })
}

fn parse_usize(name: &str, default: usize) -> Result<usize, AppError> {
    env::var(name).map_or(Ok(default), |value| {
        value
            .parse()
            .map_err(|_| AppError::Config(format!("{name} must be an integer")))
    })
}

fn init_tracing() {
    let filter = tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| {
        tracing_subscriber::EnvFilter::new("rust_backend=info,tower_http=info")
    });
    tracing_subscriber::fmt()
        .with_env_filter(filter)
        .compact()
        .init();
}

async fn shutdown_signal() {
    let ctrl_c = async {
        signal::ctrl_c()
            .await
            .expect("failed to install Ctrl+C handler");
    };
    #[cfg(unix)]
    let terminate = async {
        let mut signal = signal::unix::signal(signal::unix::SignalKind::terminate())
            .expect("failed to install SIGTERM handler");
        signal.recv().await;
    };
    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();
    tokio::select! { _ = ctrl_c => {}, _ = terminate => {} }
    info!("shutdown signal received");
}
#[cfg(test)]
mod tests {
    use super::*;
    use axum::{body::Body, middleware, routing::get, Router};
    use http_body_util::BodyExt;
    use tower::ServiceExt;

    #[tokio::test]
    async fn admin_middleware_returns_207_for_non_admin_and_200_for_admin() {
        let app = Router::new()
            .route("/", get(admin_probe))
            .layer(middleware::from_fn(require_admin));

        let ordinary_request = Request::builder()
            .uri("/")
            .extension(AuthUser {
                id: "ordinary".into(),
                is_admin: false,
            })
            .body(Body::empty())
            .expect("ordinary request");
        let ordinary_response = app
            .clone()
            .oneshot(ordinary_request)
            .await
            .expect("ordinary response");
        assert_eq!(ordinary_response.status(), StatusCode::OK);
        let ordinary_body = ordinary_response
            .into_body()
            .collect()
            .await
            .expect("ordinary body")
            .to_bytes();
        let ordinary_json: Value =
            serde_json::from_slice(&ordinary_body).expect("ordinary envelope JSON");
        assert_eq!(ordinary_json["code"], 207);

        let admin_request = Request::builder()
            .uri("/")
            .extension(AuthUser {
                id: "admin".into(),
                is_admin: true,
            })
            .body(Body::empty())
            .expect("admin request");
        let admin_response = app.oneshot(admin_request).await.expect("admin response");
        assert_eq!(admin_response.status(), StatusCode::OK);
        let admin_body = admin_response
            .into_body()
            .collect()
            .await
            .expect("admin body")
            .to_bytes();
        let admin_json: Value = serde_json::from_slice(&admin_body).expect("admin envelope JSON");
        assert_eq!(admin_json["code"], 200);
    }

    #[test]
    fn token_digest_matches_java_sha256_contract() {
        assert_eq!(
            token_digest("password"),
            "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"
        );
    }

    #[test]
    fn missing_response_data_serializes_as_json_null() {
        let body = serde_json::to_value(Envelope::<Value> {
            code: 203,
            data: None,
            message: "token不能为空".into(),
            description: String::new(),
        })
        .expect("envelope must serialize");
        assert_eq!(body["data"], Value::Null);
        assert_eq!(body["code"], 203);
    }
}
