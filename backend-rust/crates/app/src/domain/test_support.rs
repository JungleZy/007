use crate::domain::telegraph_key::synthetical;
use crate::domain::telegraph_key::telegraph_key;
use crate::domain::telex::telex;
use crate::platform::auth::AuthUser;
use crate::platform::config::AppConfig;
use crate::platform::state::AppState;
use axum::{
    extract::{Extension, State},
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::{MySql, MySqlPool, Transaction};
use std::sync::Arc;
use std::time::Duration;

pub(super) struct Fixture {
    pub(super) state: AppState,
    pub(super) pool: MySqlPool,
    pub(super) owner: String,
    pub(super) other: String,
    pub(super) target: String,
    pub(super) unrelated: String,
}

impl Fixture {
    pub(super) async fn new() -> Self {
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

    pub(super) fn actor(&self) -> AuthUser {
        AuthUser {
            id: self.owner.clone(),
            is_admin: true,
        }
    }

    pub(super) async fn cleanup(self) {
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

pub(super) async fn document(response: Response) -> Value {
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    serde_json::from_slice(&bytes).unwrap()
}

pub(super) async fn invoke(state: AppState, user: AuthUser, op: &str, id: String) -> Response {
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

pub(super) async fn gate(
    pool: &MySqlPool,
    table: &str,
    id: &str,
) -> (Transaction<'static, MySql>, u64) {
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

pub(super) async fn waiting(pool: &MySqlPool, connection: u64, table: &str, writers: i64) -> bool {
    tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let count: i64 = sqlx::query_scalar("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME=?")
                .bind(connection).bind(table).fetch_one(pool).await.unwrap();
            if count >= writers { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok()
}

pub(super) async fn isolation(op: &'static str, table: &str, synth_stat: bool) {
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

pub(super) fn settings(offset: i32) -> Value {
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
