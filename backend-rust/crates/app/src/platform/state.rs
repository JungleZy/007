use std::sync::{atomic::AtomicBool, Arc};

use sqlx::{mysql::MySqlPoolOptions, MySqlPool};

use crate::platform::config::AppConfig;
use crate::platform::error::AppError;
use crate::platform::health::refresh_database_readiness;

#[derive(Clone)]
pub(crate) struct AppState {
    pub(crate) config: Arc<AppConfig>,
    pub(crate) pool: Option<MySqlPool>,
    pub(crate) database_ready: Arc<AtomicBool>,
}

pub(crate) async fn build_state(config: Arc<AppConfig>) -> Result<AppState, AppError> {
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
