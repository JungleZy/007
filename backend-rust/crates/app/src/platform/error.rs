use thiserror::Error;

#[derive(Debug, Error)]
pub(crate) enum AppError {
    #[error("configuration error: {0}")]
    Config(String),
    #[error("failed to bind HTTP listener: {0}")]
    Bind(#[source] std::io::Error),
    #[error("invalid database configuration: {0}")]
    Database(#[from] sqlx::Error),
}
