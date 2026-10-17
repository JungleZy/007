use crate::domain::entering::entering::*;
use crate::platform::auth::AuthUser;
use crate::platform::state::AppState;
use axum::http::StatusCode;
use rust_decimal::Decimal;
use serde_json::json;
use serde_json::Value;
use uuid::Uuid;

#[test]
fn statistics_rate_rounds_half_up_and_guards_empty_totals() {
    // 1 entry in 120 s is exactly 0.5 per minute; ScoreMath.rate rounds HALF_UP to 1.
    assert_eq!(
        telex_statistics_figures(Some(120), Some(1)),
        ("120".to_owned(), Decimal::from(1))
    );
    assert_eq!(
        telex_statistics_figures(Some(7), Some(1)).1,
        Decimal::from(9)
    );
    // Nullable Integers count as 0 instead of failing; no time or no count means rate 0.
    assert_eq!(
        telex_statistics_figures(None, None),
        ("0".to_owned(), Decimal::from(0))
    );
    assert_eq!(
        telex_statistics_figures(Some(0), Some(5)).1,
        Decimal::from(0)
    );
    assert_eq!(
        telex_statistics_figures(Some(60), Some(-3)).1,
        Decimal::from(0)
    );
    assert_eq!(
        telex_statistics_figures(Some(-5), Some(3)),
        ("-5".to_owned(), Decimal::from(0))
    );
    // The largest Integer pair must not overflow the intermediate product.
    assert_eq!(
        telex_statistics_figures(Some(1), Some(i32::MAX)).1,
        Decimal::from(128_849_018_820_i64)
    );
}

#[test]
fn type_is_coerced_like_jackson_and_malformed_values_are_a_400() {
    assert_eq!(telex_type(&json!({"type": "2"})).unwrap(), Some(2));
    assert_eq!(telex_type(&json!({"type": 1.9})).unwrap(), Some(1));
    assert_eq!(telex_type(&json!({"type": null})).unwrap(), None);
    assert_eq!(telex_type(&json!({})).unwrap(), None);
    for body in [
        json!({"type": "abc"}),
        json!({"type": [1]}),
        json!([1]),
        Value::Null,
    ] {
        assert_eq!(
            telex_type(&body).unwrap_err().status(),
            StatusCode::BAD_REQUEST
        );
    }
}

#[tokio::test]
#[ignore = "requires an isolated MySQL database with the project schema (RUST_PHASE2_DATABASE_URL)"]
async fn inherited_null_category_is_skipped() -> Result<(), sqlx::Error> {
    let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("isolated database required");
    let pool = sqlx::mysql::MySqlPoolOptions::new().connect(&url).await?;
    let user = AuthUser {
        id: Uuid::new_v4().to_string(),
        is_admin: false,
    };
    sqlx::query("INSERT INTO t_user(id) VALUES (?)")
        .bind(&user.id)
        .execute(&pool)
        .await?;
    sqlx::query(
        "INSERT INTO t_entering_statistical(id,user_id,type,child_type) VALUES (?,?,1,NULL)",
    )
    .bind(Uuid::new_v4().to_string())
    .bind(&user.id)
    .execute(&pool)
    .await?;
    let state = AppState {
        config: std::sync::Arc::new(crate::platform::config::AppConfig::from_env().unwrap()),
        pool: Some(pool.clone()),
        database_ready: std::sync::Arc::new(std::sync::atomic::AtomicBool::new(true)),
    };
    let response = statistics_for(&state, &user, 1).await;
    let status = response.status();
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    let body: Value = serde_json::from_slice(&bytes).unwrap();
    sqlx::query("DELETE FROM t_entering_statistical WHERE user_id=?")
        .bind(&user.id)
        .execute(&pool)
        .await?;
    sqlx::query("DELETE FROM t_user WHERE id=?")
        .bind(&user.id)
        .execute(&pool)
        .await?;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(body["code"], 200);
    assert_eq!(
        body["data"]
            .as_array()
            .unwrap()
            .iter()
            .map(|row| row["type"].as_i64().unwrap())
            .collect::<Vec<_>>(),
        vec![0, 1, 2, 3]
    );
    Ok(())
}

#[tokio::test]
#[ignore = "requires an isolated MySQL database with the project schema (RUST_PHASE2_DATABASE_URL)"]
async fn statistics_writers_do_not_lock_other_users_rows() -> Result<(), sqlx::Error> {
    let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("isolated database required");
    let pool = sqlx::mysql::MySqlPoolOptions::new()
        .max_connections(3)
        .connect(&url)
        .await?;
    let tag = Uuid::new_v4().simple().to_string();
    let owner = format!("owner-{tag}");
    let other = format!("other-{tag}");
    for user in [&owner, &other] {
        sqlx::query("INSERT INTO t_user(id) VALUES (?)")
            .bind(user)
            .execute(&pool)
            .await?;
        sqlx::query("INSERT INTO t_entering_exercise(id,create_user_id,type,status,duration,speed) VALUES (?,?,9,2,20,10)")
            .bind(format!("train-{user}")).bind(user).execute(&pool).await?;
        for child in [1, 3] {
            sqlx::query("INSERT INTO t_entering_statistical(id,user_id,type,child_type,total_count,total_time,avg_speed) VALUES (?,?,1,?,0,'0',0)")
                .bind(format!("stat-{child}-{user}")).bind(user).bind(child).execute(&pool).await?;
        }
    }
    let mut tx = pool.begin().await?;
    assert!(crate::shared::statistical::lock_user(&mut tx, &owner).await?);
    sqlx::query("SELECT id FROM t_entering_exercise WHERE id=? FOR UPDATE")
        .bind(format!("train-{owner}"))
        .fetch_one(&mut *tx)
        .await?;
    update_exercise_statistics(&mut tx, &owner, 9)
        .await
        .unwrap();
    record_telex_statistics(
        &mut tx,
        &owner,
        &TelexSave {
            id: None,
            kind: 1,
            total_time: Some(60),
            total_num: Some(10),
            total_error: Some(0),
            message_name: None,
        },
    )
    .await?;
    let mut bystander = pool.acquire().await?;
    sqlx::query("SET SESSION innodb_lock_wait_timeout=2")
        .execute(&mut *bystander)
        .await?;
    let training = sqlx::query("UPDATE t_entering_exercise SET duration=21 WHERE id=?")
        .bind(format!("train-{other}"))
        .execute(&mut *bystander)
        .await;
    let statistics = sqlx::query("UPDATE t_entering_statistical SET total_count=7 WHERE id=?")
        .bind(format!("stat-1-{other}"))
        .execute(&mut *bystander)
        .await;
    let count: Option<i32> =
        sqlx::query_scalar("SELECT total_count FROM t_entering_statistical WHERE id=?")
            .bind(format!("stat-1-{owner}"))
            .fetch_one(&mut *tx)
            .await?;
    tx.rollback().await?;
    drop(bystander);
    for user in [&owner, &other] {
        sqlx::query("DELETE FROM t_entering_exercise WHERE create_user_id=?")
            .bind(user)
            .execute(&pool)
            .await?;
        sqlx::query("DELETE FROM t_entering_statistical WHERE user_id=?")
            .bind(user)
            .execute(&pool)
            .await?;
        sqlx::query("DELETE FROM t_user WHERE id=?")
            .bind(user)
            .execute(&pool)
            .await?;
    }
    assert_eq!(count, Some(1));
    assert!(
        training.is_ok(),
        "another user's training locked: {training:?}"
    );
    assert!(
        statistics.is_ok(),
        "another user's statistics locked: {statistics:?}"
    );
    Ok(())
}
