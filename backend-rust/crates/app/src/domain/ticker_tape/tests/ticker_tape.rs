use crate::domain::ticker_tape::ticker_tape::*;
use crate::platform::auth::AuthUser;
use crate::platform::state::AppState;
use axum::extract::Extension;
use axum::extract::State;
use axum::http::StatusCode;
use axum::Json;
use serde_json::json;
use serde_json::Value;

#[test]
fn json_body_reader_keeps_java_empty_text_guard() {
    assert_eq!(parse_message_body(None).unwrap(), Value::Null);
    assert_eq!(
        parse_message_body(Some(String::new())).unwrap(),
        Value::Null
    );
    for whitespace in [" ", "\t\r\n", " \t\r\n "] {
        assert_eq!(
            parse_message_body(Some(whitespace.into())).unwrap(),
            Value::Null
        );
    }
    assert!(parse_message_body(Some("\u{a0}".into())).is_err());
    assert!(parse_message_body(Some(" \tinvalid".into())).is_err());
    assert_eq!(parse_message_body(Some("[]".into())).unwrap(), json!([]));
    assert!(parse_message_body(Some("not json".into())).is_err());
}

#[tokio::test]
#[ignore = "requires an isolated MySQL database with the project schema (RUST_PHASE2_DATABASE_URL)"]
async fn last_train_applies_bean_conversion_without_json_parsing() -> Result<(), sqlx::Error> {
    use http_body_util::BodyExt;
    use std::sync::{atomic::AtomicBool, Arc};

    let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("isolated database URL");
    let pool = sqlx::mysql::MySqlPoolOptions::new().connect(&url).await?;
    let user = AuthUser {
        id: uuid::Uuid::new_v4().to_string(),
        is_admin: false,
    };
    let id = uuid::Uuid::new_v4().to_string();
    sqlx::query("INSERT INTO t_ticker_tape_train (id,type,status,user_id,code_message_body,create_time) VALUES (?,1,0,?,'[]',NOW(6))")
        .bind(&id).bind(&user.id).execute(&pool).await?;
    let state = AppState {
        config: Arc::new(crate::platform::config::AppConfig::from_env().unwrap()),
        pool: Some(pool.clone()),
        database_ready: Arc::new(AtomicBool::new(true)),
    };
    for (stored, expected) in [
        ("", json!([])),
        ("   ", json!([])),
        (",", json!([])),
        ("not json", Value::Null),
        ("[]", json!([])),
        ("[{\"codeMessage\":\"1234\",\"sort\":1}]", Value::Null),
    ] {
        sqlx::query("UPDATE t_ticker_tape_train SET code_message_body=? WHERE id=?")
            .bind(stored)
            .bind(&id)
            .execute(&pool)
            .await?;
        let response = last_train(
            State(state.clone()),
            Extension(user.clone()),
            Json(TypeRequest {
                train_type: Some(1),
            }),
        )
        .await;
        assert_eq!(response.status(), StatusCode::OK, "{stored:?}");
        let bytes = response.into_body().collect().await.unwrap().to_bytes();
        let body: Value = serde_json::from_slice(&bytes).unwrap();
        assert_eq!(body["code"], 200, "{stored:?}");
        assert_eq!(body["data"]["codeMessageBody"], expected, "{stored:?}");
    }
    for stored in ["", "   ", "\t\r\n", " \t\r\n "] {
        sqlx::query("UPDATE t_ticker_tape_train SET code_message_body=? WHERE id=?")
            .bind(stored)
            .bind(&id)
            .execute(&pool)
            .await?;
        let detail = by_id(
            State(state.clone()),
            Extension(user.clone()),
            Json(IdRequest {
                id: Some(id.clone()),
            }),
        )
        .await;
        let history = list(
            State(state.clone()),
            Extension(user.clone()),
            Json(PageRequest {
                page: Some(1),
                rows: Some(10),
            }),
        )
        .await;
        for (response, is_page) in [(detail, false), (history, true)] {
            assert_eq!(response.status(), StatusCode::OK);
            let bytes = response.into_body().collect().await.unwrap().to_bytes();
            let body: Value = serde_json::from_slice(&bytes).unwrap();
            assert_eq!(body["code"], 200);
            let train = if is_page {
                &body["data"]["data"][0]
            } else {
                &body["data"]
            };
            assert_eq!(train["id"], id);
            assert_eq!(train["type"], 1);
            assert_eq!(train["status"], 0);
            assert_eq!(train["codeMessageBody"], Value::Null);
        }
    }
    // Invalid non-empty JSON must still fail both JSON-reading callers.
    sqlx::query("UPDATE t_ticker_tape_train SET code_message_body=' not json ' WHERE id=?")
        .bind(&id)
        .execute(&pool)
        .await?;
    let detail = by_id(
        State(state.clone()),
        Extension(user.clone()),
        Json(IdRequest {
            id: Some(id.clone()),
        }),
    )
    .await;
    let history = list(
        State(state),
        Extension(user),
        Json(PageRequest {
            page: Some(1),
            rows: Some(10),
        }),
    )
    .await;
    for response in [detail, history] {
        assert_eq!(response.status(), StatusCode::INTERNAL_SERVER_ERROR);
    }
    sqlx::query("DELETE FROM t_ticker_tape_train WHERE id=?")
        .bind(&id)
        .execute(&pool)
        .await?;
    Ok(())
}

/// Settling one user's paused predecessor (which also refreshes that user's statistics row)
/// must leave every other user's training and statistics rows writable. Both tables are
/// indexed by primary key only, so a locking read on `user_id` would lock every row it scans
/// and stall unrelated users' saves until commit.
///
/// Needs an isolated MySQL database with the project schema:
/// `RUST_PHASE2_DATABASE_URL=mysql://… cargo test -p rust-backend -- --ignored`.
#[tokio::test]
#[ignore = "requires an isolated MySQL database with the project schema (RUST_PHASE2_DATABASE_URL)"]
async fn settling_one_users_predecessor_does_not_lock_other_users_rows() -> Result<(), sqlx::Error>
{
    let url = std::env::var("RUST_PHASE2_DATABASE_URL")
        .expect("RUST_PHASE2_DATABASE_URL must target an isolated rehearsal database");
    let pool = sqlx::mysql::MySqlPoolOptions::new()
        .max_connections(3)
        .connect(&url)
        .await?;
    let tag = uuid::Uuid::new_v4().simple().to_string();
    let owner = format!("lock-owner-{tag}");
    let other = format!("lock-other-{tag}");
    for user in [&owner, &other] {
        // A paused training of type 1 (statistics type 2) with a statistics row each.
        sqlx::query("INSERT INTO t_ticker_tape_train (id,type,status,user_id,rate,valid_time,create_time) VALUES (?,1,2,?,10,'20',NOW(6))")
            .bind(format!("train-{user}"))
            .bind(user)
            .execute(&pool)
            .await?;
        sqlx::query("INSERT INTO t_ticker_tape_train_statistical (id,type,total_count,total_time,avg_speed,user_id) VALUES (?,2,0,'0',0,?)")
            .bind(format!("stat-{user}"))
            .bind(user)
            .execute(&pool)
            .await?;
    }

    // Act: settle the owner's predecessor and keep the transaction open.
    let mut tx = pool.begin().await?;
    settle_previous(&mut tx, &owner, 1).await?;
    let mut bystander = pool.acquire().await?;
    sqlx::query("SET SESSION innodb_lock_wait_timeout=2")
        .execute(&mut *bystander)
        .await?;
    let training = sqlx::query("UPDATE t_ticker_tape_train SET name='x' WHERE id=?")
        .bind(format!("train-{other}"))
        .execute(&mut *bystander)
        .await;
    let statistics =
        sqlx::query("UPDATE t_ticker_tape_train_statistical SET total_count=7 WHERE id=?")
            .bind(format!("stat-{other}"))
            .execute(&mut *bystander)
            .await;
    // The owner's own rows were really settled (finished + counted) inside the transaction.
    let settled: (Option<i32>, Option<i32>) = (
        sqlx::query_scalar("SELECT status FROM t_ticker_tape_train WHERE id=?")
            .bind(format!("train-{owner}"))
            .fetch_one(&mut *tx)
            .await?,
        sqlx::query_scalar("SELECT total_count FROM t_ticker_tape_train_statistical WHERE id=?")
            .bind(format!("stat-{owner}"))
            .fetch_one(&mut *tx)
            .await?,
    );
    tx.rollback().await?;
    drop(bystander);
    for user in [&owner, &other] {
        sqlx::query("DELETE FROM t_ticker_tape_train WHERE user_id=?")
            .bind(user)
            .execute(&pool)
            .await?;
        sqlx::query("DELETE FROM t_ticker_tape_train_statistical WHERE user_id=?")
            .bind(user)
            .execute(&pool)
            .await?;
    }

    assert_eq!(settled, (Some(3), Some(1)));
    assert!(
        training.is_ok(),
        "another user's training row is locked: {training:?}"
    );
    assert!(
        statistics.is_ok(),
        "another user's statistics row is locked: {statistics:?}"
    );
    Ok(())
}
