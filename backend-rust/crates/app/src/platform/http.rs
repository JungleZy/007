use axum::{
    extract::Request,
    http::{HeaderValue, StatusCode},
    middleware::Next,
    response::Response,
};
use tracing::{error, info};
use uuid::Uuid;

use crate::platform::envelope::envelope_response;

pub(crate) async fn not_found() -> StatusCode {
    StatusCode::NOT_FOUND
}

/// A handler panic must not drop the connection: answer with the standard
/// system-failure envelope (HTTP 500 / code 500). The panic payload itself is
/// already reported by the panic hook; only the fact is logged here.
pub(crate) fn panic_response(_panic: Box<dyn std::any::Any + Send + 'static>) -> Response {
    error!("request handler panicked");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

pub(crate) async fn request_id(request: Request, next: Next) -> Response {
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
