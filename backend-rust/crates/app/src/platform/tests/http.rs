use crate::platform::http::panic_response;
use axum::{body::Body, extract::Request, http::StatusCode, routing::get, Router};
use http_body_util::BodyExt;
use serde_json::Value;
use tower::ServiceExt;
use tower_http::catch_panic::CatchPanicLayer;

#[tokio::test]
async fn handler_panic_becomes_system_failure_envelope() {
    async fn nullable_column_decode_panics() -> &'static str {
        panic!("simulated non-Option read of a NULL column");
    }
    let app = Router::new()
        .route("/", get(nullable_column_decode_panics))
        .layer(CatchPanicLayer::custom(panic_response));

    let response = app
        .oneshot(
            Request::builder()
                .uri("/")
                .body(Body::empty())
                .expect("request"),
        )
        .await
        .expect("a handler panic must still produce a response");
    assert_eq!(response.status(), StatusCode::INTERNAL_SERVER_ERROR);
    let body = response
        .into_body()
        .collect()
        .await
        .expect("panic response body")
        .to_bytes();
    let envelope: Value = serde_json::from_slice(&body).expect("panic envelope JSON");
    assert_eq!(envelope["code"], 500);
    assert_eq!(envelope["message"], "服务器错误");
}
