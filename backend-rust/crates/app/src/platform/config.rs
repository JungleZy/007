use std::{env, net::SocketAddr, time::Duration};

use axum::http::HeaderValue;

use crate::platform::error::AppError;
use crate::platform::extract::parse_bool;
use crate::platform::extract::parse_socket_addr;
use crate::platform::extract::parse_u32;
use crate::platform::extract::parse_u64;
use crate::platform::extract::parse_usize;
use crate::platform::flags::*;

const LISTEN_ADDR_ENV: &str = "RUST_BACKEND_ADDR";
const DATABASE_URL_ENV: &str = "RUST_BACKEND_DATABASE_URL";
const DEFAULT_LISTEN_ADDR: &str = "127.0.0.1:18002";
const DEFAULT_BODY_LIMIT: usize = 11 * 1024 * 1024;

#[derive(Clone, Debug)]
pub(crate) struct AppConfig {
    pub(crate) listen_addr: SocketAddr,
    pub(crate) database_url: Option<String>,
    pub(crate) max_connections: u32,
    pub(crate) db_timeout: Duration,
    pub(crate) body_limit: usize,
    pub(crate) cors_origin: String,
    pub(crate) cable_readonly_enabled: bool,
    pub(crate) tools_readonly_enabled: bool,
    pub(crate) device_readonly_enabled: bool,
    pub(crate) equipment_readonly_enabled: bool,
    pub(crate) entering_readonly_enabled: bool,
    pub(crate) radiotelephone_readonly_enabled: bool,
    pub(crate) telegram_readonly_enabled: bool,
    pub(crate) telegraph_key_readonly_enabled: bool,
    pub(crate) telex_readonly_enabled: bool,
    pub(crate) ticker_tape_readonly_enabled: bool,
    pub(crate) post_telegram_readonly_enabled: bool,
    pub(crate) post_telex_readonly_enabled: bool,
    pub(crate) post_telegraph_readonly_enabled: bool,
    pub(crate) post_ticker_readonly_enabled: bool,
    pub(crate) group_net_readonly_enabled: bool,
    pub(crate) receive_keypoints_readonly_enabled: bool,
    pub(crate) simulation_readonly_enabled: bool,
    pub(crate) general_train_readonly_enabled: bool,
    pub(crate) ws_readonly_enabled: bool,
    pub(crate) theory_readonly_enabled: bool,
    pub(crate) grading_readonly_enabled: bool,
    pub(crate) auth_readonly_enabled: bool,
}

impl AppConfig {
    pub(crate) fn from_env() -> Result<Self, AppError> {
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
        let tools_readonly_enabled = parse_bool(TOOLS_READONLY_ENV, false)?;
        let cable_readonly_enabled = parse_bool(CABLE_READONLY_ENV, false)?;
        let device_readonly_enabled = parse_bool(DEVICE_READONLY_ENV, false)?;
        let equipment_readonly_enabled = parse_bool(EQUIPMENT_READONLY_ENV, false)?;
        let entering_readonly_enabled = parse_bool(ENTERING_READONLY_ENV, false)?;
        let radiotelephone_readonly_enabled = parse_bool(RADIOTELEPHONE_READONLY_ENV, false)?;
        let telegram_readonly_enabled = parse_bool(TELEGRAM_READONLY_ENV, false)?;
        let telegraph_key_readonly_enabled = parse_bool(TELEGRAPH_KEY_READONLY_ENV, false)?;
        let telex_readonly_enabled = parse_bool(TELEX_READONLY_ENV, false)?;
        let ticker_tape_readonly_enabled = parse_bool(TICKER_TAPE_READONLY_ENV, false)?;
        let post_telegram_readonly_enabled = parse_bool(POST_TELEGRAM_READONLY_ENV, false)?;
        let post_telex_readonly_enabled = parse_bool(POST_TELEX_READONLY_ENV, false)?;
        let post_telegraph_readonly_enabled = parse_bool(POST_TELEGRAPH_READONLY_ENV, false)?;
        let post_ticker_readonly_enabled = parse_bool(POST_TICKER_READONLY_ENV, false)?;
        let group_net_readonly_enabled = parse_bool(GROUP_NET_READONLY_ENV, false)?;
        let receive_keypoints_readonly_enabled = parse_bool(RECEIVE_KEYPOINTS_READONLY_ENV, false)?;
        let simulation_readonly_enabled = parse_bool(SIMULATION_READONLY_ENV, false)?;
        let general_train_readonly_enabled = parse_bool(GENERAL_TRAIN_READONLY_ENV, false)?;
        let ws_readonly_enabled = parse_bool(WS_READONLY_ENV, false)?;
        let theory_readonly_enabled = parse_bool(THEORY_READONLY_ENV, false)?;
        let grading_readonly_enabled = parse_bool(GRADING_READONLY_ENV, false)?;
        let auth_readonly_enabled = parse_bool(AUTH_READONLY_ENV, false)?;
        Ok(Self {
            listen_addr,
            database_url,
            max_connections,
            db_timeout: Duration::from_secs(timeout_seconds),
            body_limit,
            cors_origin,
            cable_readonly_enabled,
            tools_readonly_enabled,
            device_readonly_enabled,
            equipment_readonly_enabled,
            entering_readonly_enabled,
            radiotelephone_readonly_enabled,
            telegram_readonly_enabled,
            telegraph_key_readonly_enabled,
            telex_readonly_enabled,
            ticker_tape_readonly_enabled,
            post_telegram_readonly_enabled,
            post_telex_readonly_enabled,
            post_telegraph_readonly_enabled,
            post_ticker_readonly_enabled,
            group_net_readonly_enabled,
            receive_keypoints_readonly_enabled,
            simulation_readonly_enabled,
            general_train_readonly_enabled,
            ws_readonly_enabled,
            theory_readonly_enabled,
            grading_readonly_enabled,
            auth_readonly_enabled,
        })
    }
}
