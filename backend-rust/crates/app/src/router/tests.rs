use crate::{
    platform::{config::AppConfig, state::AppState},
    router::build_router,
};
use axum::{body::Body, extract::Request, http::StatusCode};
use std::sync::{atomic::AtomicBool, Arc};
use tower::ServiceExt;

#[tokio::test]
async fn websocket_wildcards_reject_plain_http_without_changing_rest_405() {
    let mut config = AppConfig::from_env().unwrap();
    config.ws_readonly_enabled = true;
    let state = AppState {
        config: Arc::new(config),
        pool: None,
        database_ready: Arc::new(AtomicBool::new(false)),
    };
    let app = build_router(state);
    for prefix in ["", "/api"] {
        for path in [
            "/websocket/missing",
            "/websocketUnion/missing",
            "/startWebsocket/missing",
            "/simulation/router/savePage",
            "/generalKeyPatTrain/missing/missing",
            "/generalTelexPatTrain/missing/missing",
            "/generalTickerPat/missing/missing/missing",
        ] {
            for method in ["GET", "POST", "PUT", "DELETE"] {
                let response = app
                    .clone()
                    .oneshot(
                        Request::builder()
                            .method(method)
                            .uri(format!("{prefix}{path}"))
                            .body(Body::empty())
                            .unwrap(),
                    )
                    .await
                    .unwrap();
                assert_eq!(
                    response.status(),
                    StatusCode::NOT_FOUND,
                    "{method} {prefix}{path}"
                );
            }
        }
    }
    let missing = app
        .clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/api/unknown-b8-route")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(missing.status(), StatusCode::NOT_FOUND);
    let wrong_method = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/health")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(wrong_method.status(), StatusCode::METHOD_NOT_ALLOWED);
}
