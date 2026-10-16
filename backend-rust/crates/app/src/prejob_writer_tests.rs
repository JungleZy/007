//! Isolated-DB regressions for daily pre-job writers (no production database URLs).
use crate::{
    platform::{auth::AuthUser, config::AppConfig, state::AppState},
    synthetical, telegram_write, telegraph_key, telex,
};
use axum::{
    extract::{Extension, State},
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::{MySql, MySqlPool, Row, Transaction};
use std::sync::Arc;
use std::time::Duration;

struct Fixture {
    state: AppState,
    pool: MySqlPool,
    owner: String,
    other: String,
    target: String,
    unrelated: String,
}

impl Fixture {
    async fn new() -> Self {
        let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("scratch database URL");
        assert!(url.ends_with("/rust_scratch_lockdemo"));
        let pool = sqlx::mysql::MySqlPoolOptions::new()
            .max_connections(8)
            .connect(&url)
            .await
            .unwrap();
        let suffix = uuid::Uuid::new_v4();
        let owner = format!("prejob-owner-{suffix}");
        let other = format!("prejob-other-{suffix}");
        let target = format!("zzz-{suffix}");
        let unrelated = format!("aaa-{suffix}");
        for (id, user) in [(&target, &owner), (&unrelated, &other)] {
            sqlx::query("INSERT INTO t_user (id,user_name) VALUES (?,'prejob regression')")
                .bind(user)
                .execute(&pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO t_telex_pat (id,user_id,type,count,mistake,duration) VALUES (?,?,0,0,0,0)").bind(id).bind(user).execute(&pool).await.unwrap();
            sqlx::query("INSERT INTO t_telegraph_key_pat_train (id,create_user_id,type,total_time,total_num,total_error,create_time) VALUES (?,?,0,0,0,0,NOW())").bind(id).bind(user).execute(&pool).await.unwrap();
            for table in [
                "t_telex_pat_train_statistical",
                "t_telegraph_key_train_statistical",
            ] {
                sqlx::query(&format!("INSERT INTO {table} (id,user_id,type,total_count,total_time,avg_speed) VALUES (?,?,0,0,'0',0)"))
                    .bind(id).bind(user).execute(&pool).await.unwrap();
            }
            sqlx::query("INSERT INTO t_telegraph_key_train_statistical (id,user_id,type,total_count,total_time,avg_speed) VALUES (?,?,2,0,'0',0)")
                .bind(format!("{id}-synth")).bind(user).execute(&pool).await.unwrap();
            let content = r#"[{"text":"1234","value":"1234","type":true,"isFocus":true}]"#;
            sqlx::query("INSERT INTO t_telegraph_key_pat_synthetical_train (id,create_user_id,status,protocol_version,source_content,content,accumulated_active_millis,duration,total_number,error_number,accuracy,speed,message_type,create_time) VALUES (?,?,2,1,?,?,60000,'60',1,0,100,'4.00',0,'2020-01-01')")
                .bind(id).bind(user).bind(content).bind(content).execute(&pool).await.unwrap();
        }
        let state = AppState {
            config: Arc::new(AppConfig::from_env().unwrap()),
            pool: Some(pool.clone()),
            database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
        };
        Self {
            state,
            pool,
            owner,
            other,
            target,
            unrelated,
        }
    }

    fn actor(&self) -> AuthUser {
        AuthUser {
            id: self.owner.clone(),
            is_admin: true,
        }
    }

    async fn cleanup(self) {
        for table in [
            "t_telex_pat",
            "t_telegraph_key_pat_train",
            "t_telex_pat_train_statistical",
            "t_telegraph_key_train_statistical",
            "t_telegraph_key_pat_synthetical_train",
        ] {
            let column = if matches!(
                table,
                "t_telegraph_key_pat_train" | "t_telegraph_key_pat_synthetical_train"
            ) {
                "create_user_id"
            } else {
                "user_id"
            };
            for user in [&self.owner, &self.other] {
                let ids: Vec<String> = sqlx::query_scalar(&format!(
                    "SELECT id FROM {table} WHERE {column}=? ORDER BY id"
                ))
                .bind(user)
                .fetch_all(&self.pool)
                .await
                .unwrap();
                for id in ids {
                    sqlx::query(&format!("DELETE FROM {table} WHERE id=?"))
                        .bind(id)
                        .execute(&self.pool)
                        .await
                        .unwrap();
                }
            }
        }
        for user in [&self.owner, &self.other] {
            sqlx::query("DELETE FROM t_user WHERE id=?")
                .bind(user)
                .execute(&self.pool)
                .await
                .unwrap();
        }
    }
}

async fn document(response: Response) -> Value {
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    serde_json::from_slice(&bytes).unwrap()
}

async fn invoke(state: AppState, user: AuthUser, op: &str, id: String) -> Response {
    match op {
        "telex-save" => telex::save_base(State(state), Extension(user), Json(serde_json::from_value(json!({"type":0,"count":24,"mistake":2,"duration":60000})).unwrap())).await,
        "telex-clear" => telex::delete_base(State(state), Extension(user), Json(serde_json::from_value(json!({"type":0})).unwrap())).await,
        "key-save" => telegraph_key::save(State(state), Extension(user), Json(serde_json::from_value(json!({"id":id,"type":0,"totalTime":60000,"totalNum":24,"totalError":2})).unwrap())).await,
        "key-clear" => telegraph_key::clear(State(state), Extension(user), Json(serde_json::from_value(json!({"type":0})).unwrap())).await,
        "synth-save" => synthetical::save(State(state), Extension(user), Json(json!({"title":"replacement","messageType":0,"content":"[{\"text\":\"5678\",\"value\":\"\",\"type\":true,\"isFocus\":false}]"}))).await,
        "synth-finish" => synthetical::finish(State(state), Extension(user), Json(json!({"id":id,"content":"[{\"text\":\"1234\",\"value\":\"1234\",\"type\":true,\"isFocus\":true}]"}))).await,
        _ => panic!("unknown operation"),
    }
}

async fn gate(pool: &MySqlPool, table: &str, id: &str) -> (Transaction<'static, MySql>, u64) {
    let mut tx = pool.begin().await.unwrap();
    let connection = sqlx::query_scalar("SELECT CONNECTION_ID()")
        .fetch_one(&mut *tx)
        .await
        .unwrap();
    sqlx::query(&format!("SELECT id FROM {table} WHERE id=? FOR UPDATE"))
        .bind(id)
        .fetch_one(&mut *tx)
        .await
        .unwrap();
    (tx, connection)
}

async fn waiting(pool: &MySqlPool, connection: u64, table: &str, writers: i64) -> bool {
    tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let count: i64 = sqlx::query_scalar("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME=?")
                .bind(connection).bind(table).fetch_one(pool).await.unwrap();
            if count >= writers { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok()
}

async fn isolation(op: &'static str, table: &str, synth_stat: bool) {
    let fixture = Fixture::new().await;
    let target = if synth_stat {
        format!("{}-synth", fixture.target)
    } else {
        fixture.target.clone()
    };
    let (gate, connection) = gate(&fixture.pool, table, &target).await;
    let state = fixture.state.clone();
    let actor = fixture.actor();
    let id = fixture.target.clone();
    let writer = tokio::spawn(async move { invoke(state, actor, op, id).await });
    let reached = waiting(&fixture.pool, connection, table, 1).await;
    // Hold a late PK so a broad scan would have locked the earlier unrelated PK.
    let mut other = fixture.pool.begin().await.unwrap();
    sqlx::query("SET innodb_lock_wait_timeout=1")
        .execute(&mut *other)
        .await
        .unwrap();
    let mut writable = true;
    for affected in [
        "t_user",
        "t_telex_pat",
        "t_telex_pat_train_statistical",
        "t_telegraph_key_pat_train",
        "t_telegraph_key_train_statistical",
        "t_telegraph_key_pat_synthetical_train",
    ] {
        let id = if affected == "t_user" {
            &fixture.other
        } else {
            &fixture.unrelated
        };
        writable &= sqlx::query(&format!("UPDATE {affected} SET id=id WHERE id=?"))
            .bind(id)
            .execute(&mut *other)
            .await
            .is_ok();
    }
    other.rollback().await.unwrap();
    gate.commit().await.unwrap();
    let result = document(
        tokio::time::timeout(Duration::from_secs(5), writer)
            .await
            .unwrap()
            .unwrap(),
    )
    .await;
    fixture.cleanup().await;
    assert!(reached, "{op} did not reach the target PK gate");
    assert!(writable, "{op} locked another user's rows");
    assert_eq!(result["code"], 200, "{op}: {result}");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn telex_base_writers_use_primary_keys() {
    // telex.rs:103/221 (training), :154/229 (statistics).
    for op in ["telex-save", "telex-clear"] {
        for table in ["t_telex_pat", "t_telex_pat_train_statistical"] {
            isolation(op, table, false).await;
        }
    }
}

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

fn settings(offset: i32) -> Value {
    let mut values = Vec::new();
    for (key, min, max) in [("0", 10 + offset, 80), ("1", 80, 560)] {
        for kind in [0, 1] {
            let value = json!({
                "min": if kind == 0 { format!("<{min}") } else { min.to_string() },
                "max": if kind == 0 { format!(">{max}") } else { max.to_string() },
                "type": kind, "name":"合格", "msg":"请继续练习"
            });
            values.push(json!({"type":0,"key":key,"value":value.to_string()}));
        }
    }
    Value::Array(values)
}

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
