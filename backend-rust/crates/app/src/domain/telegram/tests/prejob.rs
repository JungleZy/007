use crate::{
    domain::{
        telegram::telegram_write,
        test_support::{document, gate, settings, waiting, Fixture},
    },
    platform::auth::AuthUser,
};
use axum::{
    extract::{Extension, State},
    Json,
};
use sqlx::Row;
use std::time::Duration;

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn settings_replacements_serialize_and_delete_only_exact_primary_keys() {
    // telegram_write.rs:352. A global set has no unrelated child; assert record-only
    // locks, unrelated users stay writable, and two admins leave one complete set.
    let fixture = Fixture::new().await;
    let empty: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_telegram_train_setting")
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
    assert_eq!(empty, 0, "scratch settings table must be empty");
    for id in [&fixture.unrelated, &fixture.target] {
        sqlx::query(
            "INSERT INTO t_telegram_train_setting (id,type,`key`,value) VALUES (?,0,'0','old')",
        )
        .bind(id)
        .execute(&fixture.pool)
        .await
        .unwrap();
    }
    let (gate, connection) = gate(&fixture.pool, "t_telegram_train_setting", &fixture.target).await;
    let state = fixture.state.clone();
    let user = fixture.actor();
    let first = tokio::spawn(async move {
        telegram_write::save_setting(State(state), Extension(user), Json(settings(0))).await
    });
    let reached = waiting(&fixture.pool, connection, "t_telegram_train_setting", 1).await;
    let gap_locks: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.data_locks l WHERE l.ENGINE_TRANSACTION_ID IN (SELECT w.REQUESTING_ENGINE_TRANSACTION_ID FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=?) AND l.OBJECT_NAME='t_telegram_train_setting' AND l.LOCK_TYPE='RECORD' AND l.LOCK_STATUS='GRANTED' AND l.LOCK_MODE NOT LIKE '%REC_NOT_GAP%'")
        .bind(connection).fetch_one(&fixture.pool).await.unwrap();
    let state = fixture.state.clone();
    let user = AuthUser {
        id: fixture.other.clone(),
        is_admin: true,
    };
    let second = tokio::spawn(async move {
        telegram_write::save_setting(State(state), Extension(user), Json(settings(10))).await
    });
    let named_waiting = tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.metadata_locks WHERE OBJECT_TYPE='USER LEVEL LOCK' AND LOCK_STATUS='PENDING' AND OBJECT_NAME=CONCAT('telegram-settings:',LEFT(SHA2(DATABASE(),256),46))").fetch_one(&fixture.pool).await.unwrap();
            if count > 0 { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok();
    let mut other = fixture.pool.begin().await.unwrap();
    sqlx::query("SET innodb_lock_wait_timeout=1")
        .execute(&mut *other)
        .await
        .unwrap();
    let writable = sqlx::query("UPDATE t_user SET user_name='writable' WHERE id=?")
        .bind(&fixture.other)
        .execute(&mut *other)
        .await
        .is_ok();
    other.rollback().await.unwrap();
    gate.commit().await.unwrap();
    let first = document(first.await.unwrap()).await;
    let second = document(second.await.unwrap()).await;
    let rows = sqlx::query("SELECT id,value FROM t_telegram_train_setting ORDER BY id")
        .fetch_all(&fixture.pool)
        .await
        .unwrap();
    let values: Vec<String> = rows.iter().map(|row| row.get("value")).collect();
    for row in &rows {
        sqlx::query("DELETE FROM t_telegram_train_setting WHERE id=?")
            .bind(row.get::<String, _>("id"))
            .execute(&fixture.pool)
            .await
            .unwrap();
    }
    fixture.cleanup().await;
    assert!(
        reached && named_waiting,
        "replacements must share the database-scoped gate"
    );
    assert!(writable, "waiting admin must not hold its user row");
    assert_eq!(
        gap_locks, 0,
        "global replacement must not acquire scan/gap locks"
    );
    assert_eq!(first["code"], 200, "{first}");
    assert_eq!(second["code"], 200, "{second}");
    assert_eq!(values.len(), 4, "both replacements must not coexist");
    assert!(
        values.iter().any(|value| value.contains("<20")),
        "the second complete set must win"
    );
}
