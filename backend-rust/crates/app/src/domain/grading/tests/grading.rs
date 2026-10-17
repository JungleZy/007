use crate::domain::grading::grading::*;
use crate::platform::state::AppState;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::Response;
use axum::Json;
use serde_json::json;
use serde_json::Value;
use sqlx::Row;
use std::{sync::Arc, time::Duration};

async fn response_code(response: Response) -> Value {
    assert_eq!(
        response.status(),
        StatusCode::OK,
        "database errors must not escape"
    );
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    serde_json::from_slice::<Value>(&bytes).unwrap()["code"].clone()
}

async fn fixture() -> (AppState, sqlx::MySqlPool, Vec<String>) {
    let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("scratch database URL");
    assert!(url.ends_with("/rust_scratch_lockdemo"));
    let pool = sqlx::mysql::MySqlPoolOptions::new()
        .max_connections(8)
        .connect(&url)
        .await
        .unwrap();
    let state = AppState {
        config: Arc::new(crate::platform::config::AppConfig::from_env().unwrap()),
        pool: Some(pool.clone()),
        database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
    };
    let suffix = uuid::Uuid::new_v4().to_string();
    let ids = ["000", "aaa", "zzz"]
        .map(|prefix| format!("{prefix}-{suffix}"))
        .to_vec();
    for (id, kind, default) in [
        (&ids[0], 12347, 0),
        (&ids[1], 12346, 0),
        (&ids[2], 12346, 1),
    ] {
        sqlx::query("INSERT INTO t_grading_rule (id,type,title,score,status,is_default) VALUES (?,?,'isolation regression',100,0,?)")
            .bind(id).bind(kind).bind(default).execute(&pool).await.unwrap();
    }
    (state, pool, ids)
}

async fn wait_on_gate(pool: &sqlx::MySqlPool, connection: u64) -> bool {
    tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME='t_grading_rule'")
                .bind(connection).fetch_one(pool).await.unwrap();
            if count > 0 { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok()
}

async fn cleanup(pool: &sqlx::MySqlPool, ids: &[String]) {
    for id in ids {
        sqlx::query("DELETE FROM t_grading_rule WHERE id=?")
            .bind(id)
            .execute(pool)
            .await
            .unwrap();
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
async fn unrelated_type_stays_writable_during_save_and_default_change() {
    for changing in [false, true] {
        let (state, pool, ids) = fixture().await;
        let mut gate = pool.begin().await.unwrap();
        let connection: u64 = sqlx::query_scalar("SELECT CONNECTION_ID()")
            .fetch_one(&mut *gate)
            .await
            .unwrap();
        sqlx::query("SELECT id FROM t_grading_rule WHERE id=? FOR UPDATE")
            .bind(&ids[1])
            .fetch_one(&mut *gate)
            .await
            .unwrap();
        let target = ids[2].clone();
        let writer = tokio::spawn(async move {
            if changing {
                change_default(State(state), Json(json!({"id":target}))).await
            } else {
                save(
                    State(state),
                    Json(json!({"id":target,"type":12346,"score":100,"isDefault":0})),
                )
                .await
            }
        });
        let waiting = wait_on_gate(&pool, connection).await;
        let unrelated = tokio::time::timeout(
            Duration::from_millis(500),
            sqlx::query("UPDATE t_grading_rule SET title='still writable' WHERE id=?")
                .bind(&ids[0])
                .execute(&pool),
        )
        .await;
        gate.commit().await.unwrap();
        let response = tokio::time::timeout(Duration::from_secs(5), writer)
            .await
            .unwrap()
            .unwrap();
        cleanup(&pool, &ids).await;
        assert!(waiting);
        assert!(
            unrelated.is_ok(),
            "another type must not be locked by a table scan"
        );
        unrelated.unwrap().unwrap();
        assert_eq!(response_code(response).await, 200);
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
async fn deleted_default_target_returns_202_without_resetting_survivors() {
    let (state, pool, ids) = fixture().await;
    let mut gate = pool.begin().await.unwrap();
    let connection: u64 = sqlx::query_scalar("SELECT CONNECTION_ID()")
        .fetch_one(&mut *gate)
        .await
        .unwrap();
    sqlx::query("SELECT id FROM t_grading_rule WHERE id=? FOR UPDATE")
        .bind(&ids[1])
        .fetch_one(&mut *gate)
        .await
        .unwrap();
    // The preflight and discovery still see the committed target, but the
    // current PK read after the gate commits must see that it was deleted.
    sqlx::query("DELETE FROM t_grading_rule WHERE id=?")
        .bind(&ids[2])
        .execute(&mut *gate)
        .await
        .unwrap();
    let target = ids[2].clone();
    let writer =
        tokio::spawn(async move { change_default(State(state), Json(json!({"id":target}))).await });
    let waiting = wait_on_gate(&pool, connection).await;
    gate.commit().await.unwrap();
    let response = writer.await.unwrap();
    let default: i32 = sqlx::query_scalar("SELECT is_default FROM t_grading_rule WHERE id=?")
        .bind(&ids[1])
        .fetch_one(&pool)
        .await
        .unwrap();
    cleanup(&pool, &ids).await;
    assert!(waiting);
    assert_eq!(response_code(response).await, 202);
    assert_eq!(
        default, 0,
        "a vanished target must not reset any surviving default"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn concurrent_new_rules_choose_exactly_one_default() {
    let (state, pool, ids) = fixture().await;
    let mut writers = Vec::new();
    for _ in 0..2 {
        let state = state.clone();
        writers.push(tokio::spawn(async move {
            save(
                State(state),
                Json(json!({"type":12348,"score":100,"isDefault":1})),
            )
            .await
        }));
    }
    for writer in writers {
        assert_eq!(response_code(writer.await.unwrap()).await, 200);
    }
    let defaults: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM t_grading_rule WHERE type=12348 AND is_default=0")
            .fetch_one(&pool)
            .await
            .unwrap();
    let created = sqlx::query_scalar::<_, String>("SELECT id FROM t_grading_rule WHERE type=12348")
        .fetch_all(&pool)
        .await
        .unwrap();
    cleanup(&pool, &created).await;
    cleanup(&pool, &ids).await;
    assert_eq!(defaults, 1);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
async fn default_change_uses_current_target_type_after_preflight() {
    let (state, pool, ids) = fixture().await;
    let gate = type_connection(&pool, 12346).await.unwrap();
    let target = ids[2].clone();
    let writer =
        tokio::spawn(async move { change_default(State(state), Json(json!({"id":target}))).await });
    let waiting = tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.metadata_locks WHERE OBJECT_TYPE='USER LEVEL LOCK' AND LOCK_STATUS='PENDING'")
                .fetch_one(&pool).await.unwrap();
            if count > 0 { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await;
    sqlx::query("UPDATE t_grading_rule SET type=12347 WHERE id=?")
        .bind(&ids[2])
        .execute(&pool)
        .await
        .unwrap();
    drop(gate);
    let response = tokio::time::timeout(Duration::from_secs(5), writer)
        .await
        .unwrap()
        .unwrap();
    let mut defaults = Vec::new();
    for id in &ids {
        defaults.push(
            sqlx::query_scalar::<_, i32>("SELECT is_default FROM t_grading_rule WHERE id=?")
                .bind(id)
                .fetch_one(&pool)
                .await
                .unwrap(),
        );
    }
    cleanup(&pool, &ids).await;
    assert!(
        waiting.is_ok(),
        "preflight must precede the concurrent type change"
    );
    assert_eq!(response_code(response).await, 200);
    assert_eq!(defaults, [1, 0, 0], "only the current target type is reset");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
async fn named_lock_waiters_keep_pool_bound_and_close_on_cancellation() {
    let (mut state, observer, ids) = fixture().await;
    let url = std::env::var("RUST_PHASE2_DATABASE_URL").unwrap();
    let bounded = sqlx::mysql::MySqlPoolOptions::new()
        .max_connections(1)
        .acquire_timeout(Duration::from_millis(200))
        .connect(&url)
        .await
        .unwrap();
    let mut probe = bounded.acquire().await.unwrap();
    let initial: u64 = sqlx::query_scalar("SELECT CONNECTION_ID()")
        .fetch_one(&mut *probe)
        .await
        .unwrap();
    drop(probe);
    let gate = type_connection(&observer, 12349).await.unwrap();
    let waiter_pool = bounded.clone();
    let waiter = tokio::spawn(async move { type_connection(&waiter_pool, 12349).await });
    tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let pending: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.metadata_locks m JOIN performance_schema.threads t ON t.THREAD_ID=m.OWNER_THREAD_ID WHERE m.OBJECT_TYPE='USER LEVEL LOCK' AND m.LOCK_STATUS='PENDING' AND t.PROCESSLIST_ID=?")
                .bind(initial).fetch_one(&observer).await.unwrap();
            if pending == 1 { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.expect("waiter must reach GET_LOCK");
    let another = bounded.acquire().await;
    let bounded_wait = matches!(another, Err(sqlx::Error::PoolTimedOut));
    drop(another);
    waiter.abort();
    assert!(waiter.await.unwrap_err().is_cancelled());
    drop(gate);
    let mut next = tokio::time::timeout(Duration::from_secs(8), async {
        loop {
            match type_connection(&bounded, 12349).await {
                Ok(connection) => break connection,
                Err(sqlx::Error::PoolTimedOut) => continue,
                Err(error) => panic!("{error}"),
            }
        }
    })
    .await
    .expect("cancelled waiter must release pool permit and named lock");
    let replacement: u64 = sqlx::query_scalar("SELECT CONNECTION_ID()")
        .fetch_one(&mut *next)
        .await
        .unwrap();
    assert_ne!(
        initial, replacement,
        "a cancelled lock session must never be reused"
    );
    assert!(
        bounded_wait,
        "a waiting named lock must keep the only pool permit"
    );
    assert_eq!(bounded.size(), 1);
    drop(next);
    state.pool = Some(bounded.clone());
    let response = tokio::time::timeout(
        Duration::from_secs(8),
        save(
            State(state),
            Json(json!({"id":ids[2],"type":12346,"score":100,"isDefault":0})),
        ),
    )
    .await
    .expect("post-commit read must work with a pool of one");
    assert_eq!(response_code(response).await, 200);
    cleanup(&observer, &ids).await;
    bounded.close().await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
async fn uppercase_alias_saves_lock_canonical_ids_and_change_default() {
    let (state, pool, ids) = fixture().await;
    sqlx::query("UPDATE t_grading_rule SET type=12347 WHERE id=?")
        .bind(&ids[2])
        .execute(&pool)
        .await
        .unwrap();
    let mut gate = pool.begin().await.unwrap();
    let connection: u64 = sqlx::query_scalar("SELECT CONNECTION_ID()")
        .fetch_one(&mut *gate)
        .await
        .unwrap();
    sqlx::query("SELECT id FROM t_grading_rule WHERE id=? FOR UPDATE")
        .bind(&ids[1])
        .fetch_one(&mut *gate)
        .await
        .unwrap();
    // Opposite-type writers must wait on the same canonical PK, not own
    // opposite records because caller aliases sorted before stored ids.
    let mut writers = Vec::new();
    for (target, kind) in [(&ids[1], 12347), (&ids[2], 12346)] {
        let state = state.clone();
        let target = target.to_uppercase();
        writers.push(tokio::spawn(async move {
            save(State(state), Json(json!({"id":target,"type":kind,"score":100,"title":"alias saved","isDefault":0}))).await
        }));
    }
    let waiting = tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let count: i64 = sqlx::query_scalar("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME='t_grading_rule'")
                .bind(connection).fetch_one(&pool).await.unwrap();
            if count == 2 { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok();
    gate.commit().await.unwrap();
    for writer in writers {
        let response = tokio::time::timeout(Duration::from_secs(5), writer)
            .await
            .expect("alias saves must not deadlock")
            .unwrap();
        assert_eq!(response_code(response).await, 200);
    }
    assert!(waiting);
    for id in [&ids[1], &ids[2]] {
        assert_eq!(
            response_code(
                change_default(State(state.clone()), Json(json!({"id":id.to_uppercase()}))).await
            )
            .await,
            200
        );
        let row = rule_by_id(&pool, &id.to_uppercase())
            .await
            .unwrap()
            .unwrap();
        assert_eq!(row.get::<String, _>("id"), *id);
        assert_eq!(row.get::<String, _>("title"), "alias saved");
        assert_eq!(row.get::<i32, _>("is_default"), 0);
    }
    cleanup(&pool, &ids).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
async fn concurrent_default_saves_and_default_change_have_one_lock_order() {
    let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("scratch database URL");
    assert!(url.ends_with("/rust_scratch_lockdemo"));
    let pool = sqlx::mysql::MySqlPoolOptions::new()
        .max_connections(6)
        .connect(&url)
        .await
        .unwrap();
    let state = AppState {
        config: Arc::new(crate::platform::config::AppConfig::from_env().unwrap()),
        pool: Some(pool.clone()),
        database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
    };
    let suffix = uuid::Uuid::new_v4().to_string();
    let ids = [
        format!("000-{suffix}"),
        format!("aaa-{suffix}"),
        format!("bbb-{suffix}"),
    ];
    for id in &ids {
        sqlx::query("INSERT INTO t_grading_rule (id,type,title,score,status,is_default) VALUES (?,12345,'lock regression',100,0,1)")
            .bind(id).execute(&pool).await.unwrap();
    }
    for change in [false, true] {
        // One writer waits on the first sorted PK; the other waits on the
        // type's named lock without owning any rule record locks.
        let mut gate = pool.begin().await.unwrap();
        let connection: u64 = sqlx::query_scalar("SELECT CONNECTION_ID()")
            .fetch_one(&mut *gate)
            .await
            .unwrap();
        sqlx::query("SELECT id FROM t_grading_rule WHERE id=? FOR UPDATE")
            .bind(&ids[0])
            .fetch_one(&mut *gate)
            .await
            .unwrap();
        let first_state = state.clone();
        let first_id = ids[1].clone();
        let first = tokio::spawn(async move {
            save(
                State(first_state),
                Json(json!({"id":first_id,"type":12345,"score":100,"isDefault":0})),
            )
            .await
        });
        let second_state = state.clone();
        let second_id = ids[2].clone();
        let second = tokio::spawn(async move {
            if change {
                change_default(State(second_state), Json(json!({"id":second_id}))).await
            } else {
                save(
                    State(second_state),
                    Json(json!({"id":second_id,"type":12345,"score":100,"isDefault":0})),
                )
                .await
            }
        });
        let waiting = tokio::time::timeout(Duration::from_secs(5), async {
            loop {
                let count: i64 = sqlx::query_scalar("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME='t_grading_rule'")
                    .bind(connection).fetch_one(&pool).await.unwrap();
                if count == 1 { break; }
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
        }).await;
        gate.commit().await.unwrap();
        let (first, second) = tokio::time::timeout(Duration::from_secs(5), async {
            tokio::join!(first, second)
        })
        .await
        .expect("default operations must not deadlock or time out");
        assert!(
            waiting.is_ok(),
            "one serialized operation must reach the first PK gate"
        );
        assert_eq!(response_code(first.unwrap()).await, 200);
        assert_eq!(response_code(second.unwrap()).await, 200);
        let defaults: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM t_grading_rule WHERE type=12345 AND is_default=0",
        )
        .fetch_one(&pool)
        .await
        .unwrap();
        assert_eq!(
            defaults, 1,
            "concurrent default replacement must retain exactly one default"
        );
    }
    for id in &ids {
        sqlx::query("DELETE FROM t_grading_rule WHERE id=?")
            .bind(id)
            .execute(&pool)
            .await
            .unwrap();
    }
}
