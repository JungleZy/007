use std::{env, error::Error, fmt, net::SocketAddr};

use axum::{
    extract::Json,
    http::StatusCode,
    routing::get,
    Router,
};
use serde::Serialize;
use tokio::{net::TcpListener, signal};
use tracing::{error, info};

const LISTEN_ADDR_ENV: &str = "RUST_BACKEND_ADDR";
const DEFAULT_LISTEN_ADDR: &str = "127.0.0.1:18002";

#[derive(Debug)]
struct ConfigError {
    message: String,
}

impl ConfigError {
    fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
        }
    }
}

impl fmt::Display for ConfigError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        self.message.fmt(formatter)
    }
}

impl Error for ConfigError {}

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

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();

    if let Err(error) = run().await {
        error!(%error, "rust backend stopped with an error");
        std::process::exit(1);
    }
}

async fn run() -> Result<(), Box<dyn Error + Send + Sync>> {
    let listen_addr = configured_listen_addr()?;
    let listener = TcpListener::bind(listen_addr).await?;

    let app = Router::new()
        .route("/health", get(health))
        .route("/live", get(live))
        .route("/ready", get(ready));

    info!(%listen_addr, "rust backend listening");
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await?;
    info!("rust backend stopped gracefully");

    Ok(())
}

fn configured_listen_addr() -> Result<SocketAddr, ConfigError> {
    let value = env::var(LISTEN_ADDR_ENV).unwrap_or_else(|_| DEFAULT_LISTEN_ADDR.to_owned());
    if value.trim().is_empty() {
        return Err(ConfigError::new(format!(
            "{LISTEN_ADDR_ENV} must not be empty; expected a host:port socket address"
        )));
    }

    value.parse::<SocketAddr>().map_err(|error| {
        ConfigError::new(format!(
            "invalid {LISTEN_ADDR_ENV} value {value:?}; expected a host:port socket address: {error}"
        ))
    })
}

async fn health() -> (StatusCode, Json<HealthResponse>) {
    (
        StatusCode::OK,
        Json(HealthResponse { status: "ok" }),
    )
}

async fn live() -> (StatusCode, Json<HealthResponse>) {
    (
        StatusCode::OK,
        Json(HealthResponse { status: "ok" }),
    )
}

async fn ready() -> (StatusCode, Json<ReadinessResponse>) {
    (
        StatusCode::OK,
        Json(ReadinessResponse {
            status: "ready",
            readiness: "process-only",
            dependencies: false,
        }),
    )
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

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }
    info!("shutdown signal received");
}
