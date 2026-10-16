use crate::domain::post_telegram::post_telegram_write::*;
use crate::platform::auth::AuthUser;
use crate::platform::state::AppState;
use axum::extract::Extension;
use axum::extract::Query;
use axum::extract::State;
use axum::response::Response;
use axum::Json;
use serde_json::json;
use serde_json::Value;
use sqlx::Row;
use sqlx::{MySql, MySqlPool, Transaction};
use std::{future::Future, sync::Arc, time::Duration};

const RULE: &str = r#"{"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":240,"r":2,"l":1},"skew":51,"code":{"dot":{"base":1000,"l":1,"r":1,"max":1},"dash":{"base":1000,"l":1,"r":1,"max":5}},"gap":{"little":{"base":1000,"l":1,"r":1,"max":1},"middle":{"base":1000,"l":1,"r":1,"max":1},"large":{"base":1000,"l":1,"r":1,"max":1}},"other":{"errorCode":{"l":1,"max":1},"quantoCode":{"l":1,"max":1},"quantoGroup":{"l":1,"max":1},"alterError":{"l":1,"max":1},"quantoRow":{"l":1,"max":1},"bunchGroup":{"l":1,"max":1}}}"#;

fn group(key: &str) -> Value {
    let symbol = i32::from(key == "T");
    let duration = if symbol == 0 { 100 } else { 300 };
    let name = if symbol == 0 { "点" } else { "划" };
    json!({"moresKey":json!([key,key,key,key]).to_string(),"patKeys":json!([key,key,key,key]).to_string(),
        "moresValue":json!([[symbol],[symbol],[symbol],[symbol]]).to_string(),
        "moresTime":json!([[duration],[duration],[duration],[duration]]).to_string(),
        "patLogs":json!([[{"name":"间隔","key":2,"value":100},{"name":name,"key":symbol,"value":duration}],
            [{"name":"间隔","key":2,"value":100},{"name":name,"key":symbol,"value":duration}],
            [{"name":"间隔","key":2,"value":100},{"name":name,"key":symbol,"value":duration}],
            [{"name":"间隔","key":2,"value":100},{"name":name,"key":symbol,"value":duration}]]).to_string()})
}

fn standards() -> Value {
    json!([{"dot":100,"line":300,"codeGap":100,"wordGap":300,"groupGap":700,"offSize":51}])
}

fn capture(id: &str, page: i32, attempt: i32, extension: bool) -> Value {
    let intervals = if extension {
        json!([{"startedMs":0,"endedMs":1000},{"startedMs":2000,"endedMs":3000}])
    } else {
        json!([{"startedMs":0,"endedMs":1000}])
    };
    json!({"trainId":id,"floorNumber":page,"attempt":attempt,"messageBody":[group("E")],
        "standard":standards(),"finishInfo":"{}","captureIntervals":intervals})
}

struct Fixture {
    state: AppState,
    user: AuthUser,
    other: AuthUser,
    target: String,
    unrelated: String,
    rule: String,
    triggers: tokio::sync::Mutex<Vec<String>>,
}

impl Fixture {
    async fn new() -> Self {
        let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("isolated scratch database URL");
        assert!(url.ends_with("/rust_scratch_lockdemo"));
        let pool = sqlx::mysql::MySqlPoolOptions::new()
            .max_connections(8)
            .connect(&url)
            .await
            .unwrap();
        let suffix = uuid::Uuid::new_v4();
        let user = AuthUser {
            id: format!("hand-owner-{suffix}"),
            is_admin: false,
        };
        let other = AuthUser {
            id: format!("hand-other-{suffix}"),
            is_admin: true,
        };
        let target = format!("zzz-hand-{suffix}");
        let unrelated = format!("aaa-hand-{suffix}");
        let rule = format!("hand-rule-{suffix}");
        sqlx::query("INSERT INTO t_grading_rule (id,type,title,score,content,status,is_default) VALUES (?,0,'handkey regression',100,?,0,1)")
            .bind(&rule).bind(RULE).execute(&pool).await.unwrap();
        for (id, actor) in [(&target, &user), (&unrelated, &other)] {
            sqlx::query("INSERT INTO t_user (id,user_name) VALUES (?,'handkey regression')")
                .bind(&actor.id)
                .execute(&pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO t_post_telegram_train (id,name,is_cable,type,code_sort,is_random,is_average,message_number,status,create_user,create_time,start_time,protocol_version,attempt,full_score,rule_id,rule_content,score,active_millis,valid_time,speed,error_number,accuracy) VALUES (?,'handkey regression',0,1,0,0,0,100,1,?,NOW(6),DATE_SUB(NOW(6),INTERVAL 2 MINUTE),1,0,100,?,?,'100',0,0,'0',0,'0.00')")
                .bind(id).bind(&actor.id).bind(&rule).bind(RULE).execute(&pool).await.unwrap();
            sqlx::query("INSERT INTO t_post_telegram_train_floor_content (id,train_id,floor_number,sort,mores_key,mores_value,mores_time,pat_keys) VALUES (?,?,1,0,?,'[[0]]','[[100]]','[\"E\"]')")
                .bind(format!("{id}-s")).bind(id).bind("[\"E\",\"E\",\"E\",\"E\"]").execute(&pool).await.unwrap();
            sqlx::query("INSERT INTO t_post_telegram_train_floor_content_value (id,train_id,floor_number,attempt,message_body,standard,finish_info,capture_intervals,received_at) VALUES (?,?,1,0,?,?,'{}',?,NOW(6))")
                .bind(format!("{id}-v")).bind(id).bind(json!([group("E")]).to_string()).bind(standards().to_string())
                .bind("[{\"startedMs\":0,\"endedMs\":1000}]").execute(&pool).await.unwrap();
            sqlx::query("INSERT INTO t_cable (id,title) VALUES (?,'handkey cable regression')")
                .bind(id)
                .execute(&pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO t_cable_floor (id,cable_id,floor_number,sort,mores_key) VALUES (?,?,0,0,?)")
                .bind(format!("{id}-c")).bind(id).bind("[\"E\",\"E\",\"E\",\"E\"]").execute(&pool).await.unwrap();
        }
        Self {
            state: AppState {
                config: Arc::new(crate::platform::config::AppConfig::from_env().unwrap()),
                pool: Some(pool),
                database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
            },
            user,
            other,
            target,
            unrelated,
            rule,
            triggers: tokio::sync::Mutex::new(Vec::new()),
        }
    }

    fn pool(&self) -> &MySqlPool {
        self.state.pool.as_ref().unwrap()
    }

    async fn register_trigger(&self, name: &str) {
        self.triggers.lock().await.push(name.to_owned());
    }

    async fn cleanup(&self) -> Result<(), sqlx::Error> {
        // Aborted handler transactions must release their connections before
        // teardown; use a fresh pool rather than any handler under test.
        self.pool().close().await;
        let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("scratch database URL");
        let pool = sqlx::mysql::MySqlPoolOptions::new()
            .max_connections(1)
            .connect(&url)
            .await?;
        let triggers = std::mem::take(&mut *self.triggers.lock().await);
        for trigger in triggers {
            sqlx::raw_sql(&format!("DROP TRIGGER IF EXISTS {trigger}"))
                .execute(&pool)
                .await?;
        }
        let mut tx = pool.begin().await?;
        let mut trains: Vec<String> =
            sqlx::query_scalar("SELECT id FROM t_post_telegram_train WHERE create_user IN (?,?)")
                .bind(&self.user.id)
                .bind(&self.other.id)
                .fetch_all(&mut *tx)
                .await?;
        trains.extend([self.target.clone(), self.unrelated.clone()]);
        trains.sort_unstable();
        trains.dedup();
        for id in trains {
            for table in [
                "t_post_telegram_train_floor_content_value",
                "t_post_telegram_train_floor_content",
            ] {
                let children: Vec<String> = sqlx::query_scalar(&format!(
                    "SELECT id FROM {table} WHERE train_id=? ORDER BY id"
                ))
                .bind(&id)
                .fetch_all(&mut *tx)
                .await?;
                for child in children {
                    sqlx::query(&format!("DELETE FROM {table} WHERE id=?"))
                        .bind(child)
                        .execute(&mut *tx)
                        .await?;
                }
            }
            sqlx::query("DELETE FROM t_post_telegram_train WHERE id=?")
                .bind(id)
                .execute(&mut *tx)
                .await?;
        }
        for id in [&self.target, &self.unrelated] {
            sqlx::query("DELETE FROM t_cable_floor WHERE id=?")
                .bind(format!("{id}-c"))
                .execute(&mut *tx)
                .await?;
            sqlx::query("DELETE FROM t_cable WHERE id=?")
                .bind(id)
                .execute(&mut *tx)
                .await?;
        }
        for actor in [&self.user, &self.other] {
            sqlx::query("DELETE FROM t_user WHERE id=?")
                .bind(&actor.id)
                .execute(&mut *tx)
                .await?;
        }
        sqlx::query("DELETE FROM t_grading_rule WHERE id=?")
            .bind(&self.rule)
            .execute(&mut *tx)
            .await?;
        tx.commit().await?;
        pool.close().await;
        Ok(())
    }
}

async fn with_fixture<F, Fut>(case: F)
where
    F: FnOnce(Arc<Fixture>) -> Fut + Send + 'static,
    Fut: Future<Output = ()> + Send + 'static,
{
    let fixture = Arc::new(Fixture::new().await);
    let working = Arc::clone(&fixture);
    let result = tokio::spawn(async move { case(working).await }).await;
    let cleanup = fixture.cleanup().await;
    match result {
        Ok(()) => cleanup.expect("fixture teardown failed"),
        Err(error) => {
            if let Err(cleanup) = cleanup {
                eprintln!("fixture teardown also failed: {cleanup}");
            }
            if error.is_panic() {
                std::panic::resume_unwind(error.into_panic());
            }
            panic!("fixture case cancelled: {error}");
        }
    }
}

struct HandlerTask(tokio::task::JoinHandle<Response>);

impl HandlerTask {
    fn spawn(future: impl Future<Output = Response> + Send + 'static) -> Self {
        Self(tokio::spawn(future))
    }

    async fn join(mut self, timeout: Duration) -> Result<Response, String> {
        match tokio::time::timeout(timeout, &mut self.0).await {
            Ok(result) => result.map_err(|error| error.to_string()),
            Err(_) => {
                self.0.abort();
                let outcome = (&mut self.0).await;
                Err(format!(
                    "handler exceeded {timeout:?}; cancellation outcome: {outcome:?}"
                ))
            }
        }
    }
}

impl Drop for HandlerTask {
    fn drop(&mut self) {
        self.0.abort();
    }
}

async fn document(response: Response) -> Value {
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    serde_json::from_slice(&bytes).unwrap()
}

async fn invoke(
    state: AppState,
    user: AuthUser,
    operation: &str,
    id: String,
    attempt: i32,
) -> Response {
    match operation {
        "begin" => {
            begin(
                State(state),
                Extension(user),
                Json(ActionRequest {
                    id: Some(id),
                    attempt: Some(attempt),
                }),
            )
            .await
        }
        "finish" => {
            finish(
                State(state),
                Extension(user),
                Json(ActionRequest {
                    id: Some(id),
                    attempt: Some(attempt),
                }),
            )
            .await
        }
        "stop" => {
            stop(
                State(state),
                Extension(user),
                Json(ActionRequest {
                    id: Some(id),
                    attempt: Some(attempt),
                }),
            )
            .await
        }
        "delete" => {
            delete(
                State(state),
                Extension(user),
                Query(DeleteQuery { train_id: Some(id) }),
            )
            .await
        }
        "save" => {
            save_content(
                State(state),
                Extension(user),
                Json(serde_json::from_value(capture(&id, 1, attempt, true)).unwrap()),
            )
            .await
        }
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

async fn waiting(pool: &MySqlPool, connection: u64, table: &str, count: i64) -> bool {
    tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let found: i64 = sqlx::query_scalar("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME=?")
                .bind(connection).bind(table).fetch_one(pool).await.unwrap();
            if found >= count { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok()
}

async fn parent_waiters(pool: &MySqlPool, id: &str, count: i64) -> bool {
    tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let found: i64 = sqlx::query_scalar("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID WHERE b.OBJECT_NAME='t_post_telegram_train' AND b.LOCK_DATA LIKE CONCAT('%',?,'%')")
                .bind(id).fetch_one(pool).await.unwrap();
            if found >= count { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok()
}

async fn isolation(operation: &'static str, table: &'static str, suffix: &str) {
    let suffix = suffix.to_owned();
    with_fixture(move |fixture| async move {
        let (held, connection) = gate(
            fixture.pool(),
            table,
            &format!("{}{suffix}", fixture.target),
        )
        .await;
        let state = fixture.state.clone();
        let user = fixture.user.clone();
        let id = fixture.target.to_uppercase();
        let writer = HandlerTask::spawn(async move { invoke(state, user, operation, id, 0).await });
        let reached = waiting(fixture.pool(), connection, table, 1).await;
        let mut probe = fixture.pool().begin().await.unwrap();
        sqlx::query("SET innodb_lock_wait_timeout=1")
            .execute(&mut *probe)
            .await
            .unwrap();
        let mut writable = true;
        for (table, suffix) in [
            ("t_post_telegram_train", ""),
            ("t_post_telegram_train_floor_content", "-s"),
            ("t_post_telegram_train_floor_content_value", "-v"),
            ("t_cable", ""),
            ("t_cable_floor", "-c"),
        ] {
            writable &= sqlx::query(&format!("UPDATE {table} SET id=id WHERE id=?"))
                .bind(format!("{}{suffix}", fixture.unrelated))
                .execute(&mut *probe)
                .await
                .is_ok();
        }
        probe.rollback().await.unwrap();
        held.commit().await.unwrap();
        let result = document(writer.join(Duration::from_secs(5)).await.unwrap()).await;
        assert!(reached, "{operation} did not reach the child PK gate");
        assert!(writable, "{operation} locked an unrelated parent's rows");
        assert_eq!(result["code"], 200, "{result}");
    })
    .await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn child_mutations_do_not_lock_unrelated_train_or_cable_rows() {
    for operation in ["finish", "save", "stop", "delete"] {
        isolation(operation, "t_post_telegram_train_floor_content_value", "-v").await;
    }
    for operation in ["stop", "delete"] {
        isolation(operation, "t_post_telegram_train_floor_content", "-s").await;
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn queued_reset_fences_old_capture_finish_and_controls() {
    with_fixture(|fixture| async move {
    let (held, connection) = gate(
        fixture.pool(),
        "t_post_telegram_train_floor_content_value",
        &format!("{}-v", fixture.target),
    )
    .await;
    let state = fixture.state.clone();
    let user = fixture.user.clone();
    let id = fixture.target.clone();
    let reset = HandlerTask::spawn(async move { invoke(state, user, "stop", id, 0).await });
    let reached = waiting(
        fixture.pool(),
        connection,
        "t_post_telegram_train_floor_content_value",
        1,
    )
    .await;
    let mut queued = Vec::new();
    for operation in ["save", "finish"] {
        let state = fixture.state.clone();
        let user = fixture.user.clone();
        let id = fixture.target.to_uppercase();
        queued.push(HandlerTask::spawn(async move {
            invoke(state, user, operation, id, 0).await
        }));
    }
    let serialized = parent_waiters(fixture.pool(), &fixture.target, 2).await;
    held.commit().await.unwrap();
    assert_eq!(document(reset.join(Duration::from_secs(5)).await.unwrap()).await["code"], 200);
    for task in queued {
        assert_eq!(document(task.join(Duration::from_secs(5)).await.unwrap()).await["code"], 208);
    }
    for operation in ["begin", "stop", "finish"] {
        assert_eq!(
            document(
                invoke(
                    fixture.state.clone(),
                    fixture.user.clone(),
                    operation,
                    fixture.target.clone(),
                    0
                )
                .await
            )
            .await["code"],
            208
        );
    }
    let row = sqlx::query("SELECT status,attempt,start_time IS NULL AS no_start FROM t_post_telegram_train WHERE id=?").bind(&fixture.target).fetch_one(fixture.pool()).await.unwrap();
    assert_eq!(row.get::<Option<i32>, _>("status"), Some(0));
    assert_eq!(row.get::<Option<i32>, _>("attempt"), Some(1));
    assert_eq!(row.get::<i64, _>("no_start"), 1);
    let count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM t_post_telegram_train_floor_content_value WHERE train_id=?",
    )
    .bind(&fixture.target)
    .fetch_one(fixture.pool())
    .await
    .unwrap();
    assert_eq!(count, 0);
    let source: String = sqlx::query_scalar(
        "SELECT mores_value FROM t_post_telegram_train_floor_content WHERE id=?",
    )
    .bind(format!("{}-s", fixture.target))
    .fetch_one(fixture.pool())
    .await
    .unwrap();
    assert_eq!(source, "[]");
    assert_eq!(
        document(
            invoke(
                fixture.state.clone(),
                fixture.user.clone(),
                "begin",
                fixture.target.clone(),
                1
            )
            .await
        )
        .await["code"],
        200
    );
    assert!(
        reached && serialized,
        "reset must own the parent before dependent reads"
    );
    }).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn settlement_freezes_score_and_rejects_queued_extensions_but_allows_exact_replay() {
    with_fixture(|fixture| async move {
        let (held, connection) = gate(
            fixture.pool(),
            "t_post_telegram_train_floor_content_value",
            &format!("{}-v", fixture.target),
        )
        .await;
        let state = fixture.state.clone();
        let user = fixture.user.clone();
        let id = fixture.target.clone();
        let settlement =
            HandlerTask::spawn(async move { invoke(state, user, "finish", id, 0).await });
        let reached = waiting(
            fixture.pool(),
            connection,
            "t_post_telegram_train_floor_content_value",
            1,
        )
        .await;
        let state = fixture.state.clone();
        let user = fixture.user.clone();
        let id = fixture.target.clone();
        let extension = HandlerTask::spawn(async move { invoke(state, user, "save", id, 0).await });
        let serialized = parent_waiters(fixture.pool(), &fixture.target, 1).await;
        held.commit().await.unwrap();
        let finished = document(settlement.join(Duration::from_secs(5)).await.unwrap()).await;
        assert_eq!(finished["code"], 200, "{finished}");
        assert_eq!(finished["data"]["score"], "100");
        assert_eq!(finished["data"]["activeMillis"], 1000);
        assert_eq!(finished["data"]["speed"], "240");
        assert_eq!(finished["data"]["accuracy"], "100.00");
        assert_eq!(
            document(extension.join(Duration::from_secs(5)).await.unwrap()).await["code"],
            202
        );
        let before: String = sqlx::query_scalar(
            "SELECT resolver FROM t_post_telegram_train_floor_content_value WHERE id=?",
        )
        .bind(format!("{}-v", fixture.target))
        .fetch_one(fixture.pool())
        .await
        .unwrap();
        let replay = save_content(
            State(fixture.state.clone()),
            Extension(fixture.user.clone()),
            Json(
                serde_json::from_value(capture(&fixture.target.to_uppercase(), 1, 0, false))
                    .unwrap(),
            ),
        )
        .await;
        assert_eq!(document(replay).await["code"], 200);
        assert_eq!(
            document(
                invoke(
                    fixture.state.clone(),
                    fixture.user.clone(),
                    "begin",
                    fixture.target.clone(),
                    0
                )
                .await
            )
            .await["code"],
            208
        );
        assert_eq!(
            document(
                invoke(
                    fixture.state.clone(),
                    fixture.other.clone(),
                    "delete",
                    fixture.target.clone(),
                    0
                )
                .await
            )
            .await["code"],
            207
        );
        let after: String = sqlx::query_scalar(
            "SELECT resolver FROM t_post_telegram_train_floor_content_value WHERE id=?",
        )
        .bind(format!("{}-v", fixture.target))
        .fetch_one(fixture.pool())
        .await
        .unwrap();
        assert_eq!(before, after);
        assert_eq!(
            serde_json::from_str::<Value>(&after).unwrap()["resolverMessage"],
            json!(["EEEE"])
        );
        let resolver: Value = serde_json::from_str(&after).unwrap();
        let events: Value =
            serde_json::from_str(resolver["resolverPatLogs"][0].as_str().unwrap()).unwrap();
        assert_eq!(events[0][0]["name"], "间隔");
        assert_eq!(events[0][1]["name"], "点");
        assert!(reached && serialized);
    })
    .await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn concurrent_lazy_reads_use_current_parent_attempt_and_generate_once() {
    with_fixture(|fixture| async move {
    let (mut held, connection) =
        gate(fixture.pool(), "t_post_telegram_train", &fixture.target).await;
    let mut readers = Vec::new();
    for _ in 0..2 {
        let state = fixture.state.clone();
        let user = fixture.user.clone();
        let id = fixture.target.to_uppercase();
        readers.push(HandlerTask::spawn(async move {
            content(
                State(state),
                Extension(user),
                Json(ContentQuery {
                    id: Some(id),
                    floor_number: Some(3),
                }),
            )
            .await
        }));
    }
    let reached = waiting(fixture.pool(), connection, "t_post_telegram_train", 2).await;
    sqlx::query("UPDATE t_post_telegram_train SET message_number=201,attempt=1 WHERE id=?")
        .bind(&fixture.target)
        .execute(&mut *held)
        .await
        .unwrap();
    held.commit().await.unwrap();
    let first = document(readers.remove(0).join(Duration::from_secs(5)).await.unwrap()).await;
    let second = document(readers.remove(0).join(Duration::from_secs(5)).await.unwrap()).await;
    assert_eq!(first["code"], 200, "{first}");
    assert_eq!(second["code"], 200, "{second}");
    assert_eq!(first["data"]["attempt"], 1);
    assert_eq!(first["data"]["messageKey"], second["data"]["messageKey"]);
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_post_telegram_train_floor_content WHERE train_id=? AND floor_number=3").bind(&fixture.target).fetch_one(fixture.pool()).await.unwrap();
    assert_eq!(count, 1);
    let canonical: String = sqlx::query_scalar("SELECT train_id FROM t_post_telegram_train_floor_content WHERE train_id=? AND floor_number=3").bind(&fixture.target).fetch_one(fixture.pool()).await.unwrap();
    assert_eq!(canonical, fixture.target);
    assert!(reached);
    }).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn fixed_cable_snapshot_starts_after_rule_wait_and_ignores_unrelated_floor_locks() {
    with_fixture(|fixture| async move {
    let (held, connection) = gate(fixture.pool(), "t_grading_rule", &fixture.rule).await;
    let (other_floor, _) = gate(
        fixture.pool(),
        "t_cable_floor",
        &format!("{}-c", fixture.unrelated),
    )
    .await;
    let state = fixture.state.clone();
    let user = fixture.user.clone();
    let body = json!({"name":"frozen cable","isCable":1,"type":1,"messageNumber":100,"ruleId":fixture.rule.to_uppercase(),"cableId":fixture.target.to_uppercase(),"startPage":1});
    let creator = HandlerTask::spawn(async move {
        add(
            State(state),
            Extension(user),
            Json(serde_json::from_value(body).unwrap()),
        )
        .await
    });
    let reached = waiting(fixture.pool(), connection, "t_grading_rule", 1).await;
    let mut replacement = fixture.pool().begin().await.unwrap();
    sqlx::query("DELETE FROM t_cable_floor WHERE id=?")
        .bind(format!("{}-c", fixture.target))
        .execute(&mut *replacement)
        .await
        .unwrap();
    sqlx::query("INSERT INTO t_cable_floor (id,cable_id,floor_number,sort,mores_key) VALUES (?,?,0,0,'[\"T\",\"T\",\"T\",\"T\"]')")
        .bind(format!("{}-c", fixture.target)).bind(&fixture.target).execute(&mut *replacement).await.unwrap();
    replacement.commit().await.unwrap();
    held.commit().await.unwrap();
    let result = creator.join(Duration::from_secs(2)).await;
    other_floor.commit().await.unwrap();
    let created = document(result.expect("snapshot must not lock unrelated cable floors")).await;
    assert_eq!(created["code"], 200, "{created}");
    assert_eq!(created["data"]["ruleId"], fixture.rule);
    let id = created["data"]["id"].as_str().unwrap();
    sqlx::query("UPDATE t_cable_floor SET mores_key='[\"E\",\"E\",\"E\",\"E\"]' WHERE id=?")
        .bind(format!("{}-c", fixture.target))
        .execute(fixture.pool())
        .await
        .unwrap();
    let page = document(
        content(
            State(fixture.state.clone()),
            Extension(fixture.user.clone()),
            Json(ContentQuery {
                id: Some(id.to_owned()),
                floor_number: Some(1),
            }),
        )
        .await,
    )
    .await;
    assert_eq!(
        page["data"]["messageKey"][0]["moresKey"],
        "[\"T\",\"T\",\"T\",\"T\"]"
    );
    assert_eq!(
        document(
            invoke(
                fixture.state.clone(),
                fixture.user.clone(),
                "delete",
                id.to_owned(),
                0
            )
            .await
        )
        .await["code"],
        200
    );
    assert!(reached);
    }).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo with CREATE TRIGGER privilege"]
async fn lazy_capture_generation_and_raw_insert_roll_back_together_on_one_connection() {
    with_fixture(|fixture| async move {
    sqlx::query("UPDATE t_post_telegram_train SET message_number=201 WHERE id=?")
        .bind(&fixture.target)
        .execute(fixture.pool())
        .await
        .unwrap();
    let trigger = format!("b2_hand_insert_{}", uuid::Uuid::new_v4().simple());
    fixture.register_trigger(&trigger).await;
    sqlx::raw_sql(&format!("CREATE TRIGGER {trigger} BEFORE INSERT ON t_post_telegram_train_floor_content_value FOR EACH ROW BEGIN IF NEW.train_id='{}' AND NEW.floor_number=3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='handkey rollback regression'; END IF; END", fixture.target)).execute(fixture.pool()).await.unwrap();
    let mut payload = capture(&fixture.target.to_uppercase(), 3, 0, false);
    payload["captureIntervals"] = json!([{"startedMs":2000,"endedMs":3000}]);
    let failed = document(
        save_content(
            State(fixture.state.clone()),
            Extension(fixture.user.clone()),
            Json(serde_json::from_value(payload.clone()).unwrap()),
        )
        .await,
    )
    .await;
    sqlx::raw_sql(&format!("DROP TRIGGER {trigger}"))
        .execute(fixture.pool())
        .await
        .unwrap();
    assert_eq!(failed["code"], 500);
    for table in [
        "t_post_telegram_train_floor_content",
        "t_post_telegram_train_floor_content_value",
    ] {
        let count: i64 = sqlx::query_scalar(&format!(
            "SELECT COUNT(*) FROM {table} WHERE train_id=? AND floor_number=3"
        ))
        .bind(&fixture.target)
        .fetch_one(fixture.pool())
        .await
        .unwrap();
        assert_eq!(
            count, 0,
            "failed capture left generated children in {table}"
        );
    }
    let url = std::env::var("RUST_PHASE2_DATABASE_URL").unwrap();
    let bounded = sqlx::mysql::MySqlPoolOptions::new()
        .max_connections(1)
        .acquire_timeout(Duration::from_millis(200))
        .connect(&url)
        .await
        .unwrap();
    let mut state = fixture.state.clone();
    state.pool = Some(bounded);
    let saved = tokio::time::timeout(
        Duration::from_secs(5),
        save_content(
            State(state),
            Extension(fixture.user.clone()),
            Json(serde_json::from_value(payload).unwrap()),
        ),
    )
    .await
    .unwrap();
    assert_eq!(document(saved).await["code"], 200);
    let details = document(
        crate::domain::post_telegram::post_telegram::detail(
            State(fixture.state.clone()),
            Extension(fixture.user.clone()),
            Json(json!({"id":fixture.target.to_uppercase()})),
        )
        .await,
    )
    .await;
    assert_eq!(
        details["data"]["pageAnalyzeVOS"],
        json!([{"pageNumber":1,"patNumber":4,"totalTime":1000},{"pageNumber":3,"patNumber":4,"totalTime":1000}])
    );
    }).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo with CREATE TRIGGER privilege"]
async fn reset_rolls_back_raw_deletion_when_source_clear_fails() {
    with_fixture(|fixture| async move {
    let trigger = format!("b2_hand_reset_{}", uuid::Uuid::new_v4().simple());
    fixture.register_trigger(&trigger).await;
    sqlx::raw_sql(&format!("CREATE TRIGGER {trigger} BEFORE UPDATE ON t_post_telegram_train_floor_content FOR EACH ROW BEGIN IF NEW.id='{}-s' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='handkey reset rollback'; END IF; END", fixture.target)).execute(fixture.pool()).await.unwrap();
    let failed = document(
        invoke(
            fixture.state.clone(),
            fixture.user.clone(),
            "stop",
            fixture.target.clone(),
            0,
        )
        .await,
    )
    .await;
    sqlx::raw_sql(&format!("DROP TRIGGER {trigger}"))
        .execute(fixture.pool())
        .await
        .unwrap();
    assert_eq!(failed["code"], 500);
    let count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM t_post_telegram_train_floor_content_value WHERE train_id=?",
    )
    .bind(&fixture.target)
    .fetch_one(fixture.pool())
    .await
    .unwrap();
    assert_eq!(count, 1);
    let row = sqlx::query("SELECT status,attempt FROM t_post_telegram_train WHERE id=?")
        .bind(&fixture.target)
        .fetch_one(fixture.pool())
        .await
        .unwrap();
    assert_eq!(row.get::<Option<i32>, _>("status"), Some(1));
    assert_eq!(row.get::<Option<i32>, _>("attempt"), Some(0));
    }).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo with CREATE TRIGGER privilege"]
async fn settlement_rolls_back_prior_resolvers_when_later_page_write_fails() {
    with_fixture(|fixture| async move {
    sqlx::query("UPDATE t_post_telegram_train SET message_number=200 WHERE id=?")
        .bind(&fixture.target)
        .execute(fixture.pool())
        .await
        .unwrap();
    sqlx::query("INSERT INTO t_post_telegram_train_floor_content (id,train_id,floor_number,sort,mores_key,mores_value,mores_time,pat_keys) VALUES (?,?,2,0,'[\"T\",\"T\",\"T\",\"T\"]','[]','[]','[]')")
        .bind(format!("{}-t", fixture.target)).bind(&fixture.target).execute(fixture.pool()).await.unwrap();
    sqlx::query("INSERT INTO t_post_telegram_train_floor_content_value (id,train_id,floor_number,attempt,message_body,standard,finish_info,capture_intervals,received_at) VALUES (?,?,2,0,?,?,'{}','[{\"startedMs\":1000,\"endedMs\":3000}]',NOW(6))")
        .bind(format!("{}-w", fixture.target)).bind(&fixture.target).bind(json!([group("T")]).to_string()).bind(standards().to_string()).execute(fixture.pool()).await.unwrap();
    let trigger = format!("b2_hand_finish_{}", uuid::Uuid::new_v4().simple());
    fixture.register_trigger(&trigger).await;
    sqlx::raw_sql(&format!("CREATE TRIGGER {trigger} BEFORE UPDATE ON t_post_telegram_train_floor_content_value FOR EACH ROW BEGIN IF NEW.id='{}-w' AND NEW.resolver IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='handkey settlement rollback'; END IF; END", fixture.target)).execute(fixture.pool()).await.unwrap();
    let failed = document(
        invoke(
            fixture.state.clone(),
            fixture.user.clone(),
            "finish",
            fixture.target.clone(),
            0,
        )
        .await,
    )
    .await;
    sqlx::raw_sql(&format!("DROP TRIGGER {trigger}"))
        .execute(fixture.pool())
        .await
        .unwrap();
    assert_eq!(failed["code"], 500);
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_post_telegram_train_floor_content_value WHERE train_id=? AND resolver IS NOT NULL").bind(&fixture.target).fetch_one(fixture.pool()).await.unwrap();
    assert_eq!(count, 0);
    let status: Option<i32> =
        sqlx::query_scalar("SELECT status FROM t_post_telegram_train WHERE id=?")
            .bind(&fixture.target)
            .fetch_one(fixture.pool())
            .await
            .unwrap();
    assert_eq!(status, Some(1));
    }).await;
}

#[test]
fn character_count_preserves_source_shape_and_event_name_validation() {
    let mut body = group("E");
    assert_eq!(count_characters(&[body.clone()]), Ok(4));
    body["moresKey"] = json!("[1]");
    assert_eq!(count_characters(&[body.clone()]), Err("拍发内容字段无效"));
    body["moresKey"] = json!("[\"E\",\"E\",\"E\",\"E\"]");
    let mut logs: Value = serde_json::from_str(body["patLogs"].as_str().unwrap()).unwrap();
    logs[0][0]["name"] = json!(12);
    body["patLogs"] = json!(logs.to_string());
    body["patKeys"] = json!("[]");
    assert_eq!(count_characters(&[body.clone()]), Err("拍发内容字段无效"));
    logs[0][0]["name"] = json!("间隔\n\"备注\"");
    body["patLogs"] = json!(logs.to_string());
    assert_eq!(
        count_characters(&[body.clone()]),
        Err("拍发字符、码值与时长记录不一致")
    );
    body["patKeys"] = json!("[\"E\",\"E\",\"E\",\"E\"]");
    assert_eq!(count_characters(&[body.clone()]), Ok(4));
    logs[0][0]["name"] = Value::Null;
    body["patLogs"] = json!(logs.to_string());
    assert_eq!(count_characters(&[body]), Ok(4));
}
