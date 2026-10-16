#![recursion_limit = "512"]

mod admin;
#[cfg(test)]
mod b8_writer_tests;
mod bean_conversion;
mod cable;
mod comprehensive;
mod device;
mod entering;
mod entering_codebook;
mod equipment;
mod equipment_train;
mod exam;
mod general_aux;
mod general_group_net_rule;
mod general_train;
mod grading;
mod group_net;
mod group_net_write;
mod keypoints;
mod masthead;
mod military;
mod military_term_data;
mod password;
mod post_entering;
mod post_military;
mod post_radiotelephone;
mod post_telegram;
mod post_telegram_write;
mod post_telegraph;
mod post_telegraph_write;
mod post_telex;
mod post_telex_write;
mod post_ticker;
#[cfg(test)]
mod prejob_writer_tests;
mod question_level;
#[cfg(test)]
mod radio_military_writer_tests;
mod radio_term;
mod radiotelephone;
mod receive_keypoints;
mod scoring;
mod simulation;
mod statistical;
mod synthetical;
mod telegram;
mod telegram_write;
mod telegraph_key;
mod telex;
mod telex_capture;
mod telex_handle;
mod test_paper;
mod theory;
mod theory_detail;
mod theory_file;
mod theory_question;
mod theory_test;
mod ticker_tape;
mod train_rule;
mod user_training_stats;
mod ws;

mod platform;
mod router;
mod runtime;
mod system;
mod tools;

pub async fn run() {
    runtime::init_tracing();
    if let Err(error) = runtime::run().await {
        tracing::error!(error = %error, "rust backend stopped with an error");
        std::process::exit(1);
    }
}

#[cfg(test)]
mod tests {
    use std::sync::{atomic::AtomicBool, Arc};

    use crate::{
        platform::{
            auth::{require_admin, token_digest, AuthUser},
            config::AppConfig,
            envelope::Envelope,
            health::admin_probe,
            http::panic_response,
            state::AppState,
        },
        router::build_router,
        tools::twelve_month_data,
    };
    use axum::{body::Body, middleware, routing::get, Router};
    use axum::{extract::Request, http::StatusCode};
    use http_body_util::BodyExt;
    use serde_json::Value;
    use tower::ServiceExt;
    use tower_http::catch_panic::CatchPanicLayer;

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

    #[test]
    fn token_digest_matches_java_sha256_contract() {
        assert_eq!(
            token_digest("password"),
            "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"
        );
    }

    #[test]
    fn twelve_month_data_matches_java_descending_year_month_order() {
        let data = twelve_month_data(2026, 1);
        assert_eq!(data.first().expect("first month")["year"], 2026);
        assert_eq!(data.first().expect("first month")["month"], 1);
        assert_eq!(data.last().expect("last month")["year"], 2025);
        assert_eq!(data.last().expect("last month")["month"], 2);
        assert_eq!(data.len(), 12);
    }

    #[test]
    fn missing_response_data_serializes_as_json_null() {
        let body = serde_json::to_value(Envelope::<Value> {
            code: 203,
            data: None,
            message: "token不能为空".into(),
            description: String::new(),
        })
        .expect("envelope must serialize");
        assert_eq!(body["data"], Value::Null);
        assert_eq!(body["code"], 203);
    }
}
