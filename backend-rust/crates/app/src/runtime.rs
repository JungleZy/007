use std::{sync::Arc, time::Duration};

use tokio::{net::TcpListener, signal};
use tracing::info;

use crate::domain::general_train::general_train;
use crate::platform::config::AppConfig;
use crate::platform::error::AppError;
use crate::platform::state::build_state;
use crate::router::build_router;

pub(crate) async fn run() -> Result<(), AppError> {
    let config = Arc::new(AppConfig::from_env()?);
    let state = build_state(config.clone()).await?;
    let listener = TcpListener::bind(config.listen_addr)
        .await
        .map_err(AppError::Bind)?;
    let app = build_router(state.clone());
    let recovery_task = if config.general_train_readonly_enabled {
        let recovery_state = state.clone();
        Some(tokio::spawn(async move {
            let mut interval = tokio::time::interval(Duration::from_secs(5));
            loop {
                interval.tick().await;
                general_train::recover_general_trainings(&recovery_state).await;
            }
        }))
    } else {
        None
    };

    info!(
        database_configured = config.database_url.is_some(),
        tools_readonly_enabled = config.tools_readonly_enabled,
        auth_readonly_enabled = config.auth_readonly_enabled,
        max_connections = config.max_connections,
        body_limit = config.body_limit,
        "rust backend listening"
    );
    let result = axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await
        .map_err(|error| AppError::Bind(std::io::Error::other(error)));
    if let Some(recovery_task) = recovery_task {
        recovery_task.abort();
    }
    result?;
    info!("rust backend stopped gracefully");
    Ok(())
}

pub(crate) fn init_tracing() {
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
