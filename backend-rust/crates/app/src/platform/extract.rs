use std::{env, net::SocketAddr};

use axum::extract::Request;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use serde_json::Value;

use crate::platform::error::AppError;

pub(crate) fn parse_bool(name: &str, default: bool) -> Result<bool, AppError> {
    match env::var(name) {
        Ok(value) if value.eq_ignore_ascii_case("true") => Ok(true),
        Ok(value) if value.eq_ignore_ascii_case("false") => Ok(false),
        Ok(_) => Err(AppError::Config(format!("{name} must be true or false"))),
        Err(_) => Ok(default),
    }
}
pub(crate) fn header_text<'a>(request: &'a Request, name: &str) -> Option<&'a str> {
    request
        .headers()
        .get(name)
        .and_then(|value| value.to_str().ok())
        .filter(|value| !value.is_empty())
}

pub(crate) fn parse_socket_addr(name: &str, default: &str) -> Result<SocketAddr, AppError> {
    let value = env::var(name).unwrap_or_else(|_| default.to_owned());
    value
        .parse()
        .map_err(|error| AppError::Config(format!("invalid {name} value {value:?}: {error}")))
}

pub(crate) fn parse_u32(name: &str, default: u32) -> Result<u32, AppError> {
    env::var(name).map_or(Ok(default), |value| {
        value
            .parse()
            .map_err(|_| AppError::Config(format!("{name} must be an integer")))
    })
}

pub(crate) fn parse_u64(name: &str, default: u64) -> Result<u64, AppError> {
    env::var(name).map_or(Ok(default), |value| {
        value
            .parse()
            .map_err(|_| AppError::Config(format!("{name} must be an integer")))
    })
}

pub(crate) fn parse_usize(name: &str, default: usize) -> Result<usize, AppError> {
    env::var(name).map_or(Ok(default), |value| {
        value
            .parse()
            .map_err(|_| AppError::Config(format!("{name} must be an integer")))
    })
}
pub(crate) fn request_integer(value: Option<&Value>) -> Result<Option<i32>, Response> {
    match value {
        None | Some(Value::Null) => Ok(None),
        Some(Value::Number(value)) => {
            let integer = value
                .as_i64()
                .and_then(|value| i32::try_from(value).ok())
                .or_else(|| {
                    value
                        .as_f64()
                        .filter(|value| *value >= i32::MIN as f64 && *value <= i32::MAX as f64)
                        .map(|value| value as i32)
                });
            integer
                .map(Some)
                .ok_or_else(|| StatusCode::BAD_REQUEST.into_response())
        }
        Some(Value::String(value)) => {
            let value = value.trim_matches(|character| character <= '\u{20}');
            if value.is_empty() || value == "null" {
                Ok(None)
            } else {
                value
                    .parse()
                    .map(Some)
                    .map_err(|_| StatusCode::BAD_REQUEST.into_response())
            }
        }
        Some(_) => Err(StatusCode::BAD_REQUEST.into_response()),
    }
}
