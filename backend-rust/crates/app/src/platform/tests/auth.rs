use crate::platform::{
    auth::{require_admin, token_digest, AuthUser},
    health::admin_probe,
};
use axum::{body::Body, extract::Request, http::StatusCode, middleware, routing::get, Router};
use http_body_util::BodyExt;
use serde_json::Value;
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
