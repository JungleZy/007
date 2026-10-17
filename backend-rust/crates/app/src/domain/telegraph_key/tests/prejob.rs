use crate::domain::{
    telegraph_key::synthetical,
    test_support::{document, gate, invoke, isolation, waiting, Fixture},
};
use axum::{
    extract::{Extension, State},
    Json,
};
use serde_json::json;
use sqlx::Row;
use std::time::Duration;

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn electronic_key_writers_use_primary_keys() {
    // telegraph_key.rs:183 (statistics save), :241/273 (training/statistics clear).
    isolation("key-save", "t_telegraph_key_train_statistical", false).await;
    for table in [
        "t_telegraph_key_pat_train",
        "t_telegraph_key_train_statistical",
    ] {
        isolation("key-clear", table, false).await;
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn synthetical_writers_use_primary_keys() {
    // synthetical.rs:586 (latest parent), :415/451 (recount and statistics).
    isolation("synth-save", "t_telegraph_key_pat_synthetical_train", false).await;
    for op in ["synth-save", "synth-finish"] {
        isolation(op, "t_telegraph_key_train_statistical", true).await;
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn same_owner_writers_serialize_before_children_and_preserve_totals() {
    for op in ["telex-save", "key-save", "synth-save", "synth-finish"] {
        let fixture = Fixture::new().await;
        let (gate, connection) = gate(&fixture.pool, "t_user", &fixture.owner).await;
        let mut writers = Vec::new();
        for _ in 0..2 {
            let state = fixture.state.clone();
            let actor = fixture.actor();
            let id = fixture.target.clone();
            writers.push(tokio::spawn(
                async move { invoke(state, actor, op, id).await },
            ));
        }
        let reached = waiting(&fixture.pool, connection, "t_user", 2).await;
        let child_locks: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.data_locks l WHERE l.ENGINE_TRANSACTION_ID IN (SELECT w.REQUESTING_ENGINE_TRANSACTION_ID FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=?) AND l.OBJECT_NAME<>'t_user' AND l.LOCK_TYPE='RECORD' AND l.LOCK_STATUS='GRANTED'")
            .bind(connection).fetch_one(&fixture.pool).await.unwrap();
        gate.commit().await.unwrap();
        let mut replies = Vec::new();
        for writer in writers {
            replies.push(
                document(
                    tokio::time::timeout(Duration::from_secs(5), writer)
                        .await
                        .unwrap()
                        .unwrap(),
                )
                .await,
            );
        }
        let table = if op == "telex-save" {
            "t_telex_pat_train_statistical"
        } else {
            "t_telegraph_key_train_statistical"
        };
        let kind = if op.starts_with("synth") { 2 } else { 0 };
        let row = sqlx::query(&format!(
            "SELECT total_count,total_time FROM {table} WHERE user_id=? AND type=?"
        ))
        .bind(&fixture.owner)
        .bind(kind)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
        let count: Option<i32> = row.get("total_count");
        let time: Option<String> = row.get("total_time");
        let parents: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_telegraph_key_pat_synthetical_train WHERE create_user_id=? AND status=0")
            .bind(&fixture.owner).fetch_one(&fixture.pool).await.unwrap();
        fixture.cleanup().await;
        assert!(reached, "{op}: both requests must wait on the owner");
        assert_eq!(child_locks, 0, "{op}: child locks preceded owner");
        for response in replies {
            assert_eq!(response["code"], 200, "{op}: {response}");
        }
        assert_eq!(
            count,
            Some(if op.starts_with("synth") { 1 } else { 2 }),
            "{op}"
        );
        assert_eq!(time.as_deref(), Some("60000"), "{op}");
        if op == "synth-save" {
            assert_eq!(
                parents, 1,
                "concurrent replacements must leave one pending training"
            );
        }
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn different_parents_settle_with_current_owner_history() {
    let fixture = Fixture::new().await;
    let sibling = format!("{}-sibling", fixture.target);
    sqlx::query("INSERT INTO t_telegraph_key_pat_synthetical_train (id,create_user_id,status,protocol_version,source_content,content,accumulated_active_millis,duration,total_number,error_number,accuracy,speed,message_type,create_time) SELECT ?,create_user_id,status,protocol_version,source_content,content,accumulated_active_millis,duration,total_number,error_number,accuracy,speed,message_type,'2020-01-02' FROM t_telegraph_key_pat_synthetical_train WHERE id=?")
        .bind(&sibling).bind(&fixture.target).execute(&fixture.pool).await.unwrap();
    let (gate, connection) = gate(&fixture.pool, "t_user", &fixture.owner).await;
    let mut writers = Vec::new();
    for id in [fixture.target.clone(), sibling] {
        let state = fixture.state.clone();
        let actor = fixture.actor();
        writers.push(tokio::spawn(async move {
            invoke(state, actor, "synth-finish", id).await
        }));
    }
    let reached = waiting(&fixture.pool, connection, "t_user", 2).await;
    gate.commit().await.unwrap();
    let mut replies = Vec::new();
    for writer in writers {
        replies.push(
            document(
                tokio::time::timeout(Duration::from_secs(5), writer)
                    .await
                    .unwrap()
                    .unwrap(),
            )
            .await,
        );
    }
    let row = sqlx::query("SELECT total_count,total_time,CAST(avg_speed AS CHAR) AS speed FROM t_telegraph_key_train_statistical WHERE user_id=? AND type=2")
        .bind(&fixture.owner).fetch_one(&fixture.pool).await.unwrap();
    let count: Option<i32> = row.get("total_count");
    let time: Option<String> = row.get("total_time");
    let speed: Option<String> = row.get("speed");
    fixture.cleanup().await;
    assert!(reached, "both settlements must wait for the same owner");
    for response in replies {
        assert_eq!(response["code"], 200, "{response}");
    }
    assert_eq!(
        count,
        Some(2),
        "RR preflight snapshots must not hide another parent's committed settlement"
    );
    assert_eq!(time.as_deref(), Some("120000"));
    assert_eq!(speed.as_deref(), Some("4.00"));
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn synthetical_existing_writes_use_one_pool_connection() {
    let fixture = Fixture::new().await;
    sqlx::query("UPDATE t_telegraph_key_pat_synthetical_train SET status=0 WHERE id=?")
        .bind(&fixture.target)
        .execute(&fixture.pool)
        .await
        .unwrap();
    let url = std::env::var("RUST_PHASE2_DATABASE_URL").unwrap();
    let bounded = sqlx::mysql::MySqlPoolOptions::new()
        .max_connections(1)
        .acquire_timeout(Duration::from_millis(200))
        .connect(&url)
        .await
        .unwrap();
    let mut state = fixture.state.clone();
    state.pool = Some(bounded);
    let actor = fixture.actor();
    let id = fixture.target.clone();
    let replies = tokio::time::timeout(Duration::from_secs(5), async {
        let mut replies = Vec::new();
        replies.push(document(synthetical::save(State(state.clone()), Extension(actor.clone()), Json(json!({"id":id,"title":"single connection"}))).await).await);
        replies.push(document(synthetical::begin(State(state.clone()), Extension(actor.clone()), Json(json!({"id":id}))).await).await);
        let body = json!({"id":id,"content":"[{\"text\":\"1234\",\"value\":\"1234\",\"type\":true,\"isFocus\":true}]"});
        replies.push(document(synthetical::stop(State(state.clone()), Extension(actor.clone()), Json(body.clone())).await).await);
        replies.push(document(synthetical::go_to(State(state.clone()), Extension(actor.clone()), Json(json!({"id":id}))).await).await);
        replies.push(document(synthetical::finish(State(state), Extension(actor), Json(body)).await).await);
        replies
    }).await;
    fixture.cleanup().await;
    for response in replies.expect("preflight must not reenter a one-connection pool inside BEGIN")
    {
        assert_eq!(response["code"], 200, "{response}");
    }
}
