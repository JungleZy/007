use crate::domain::entering::entering::*;
use crate::domain::entering::post_entering;
use crate::platform::auth::AuthUser;
use crate::platform::config::AppConfig;
use crate::platform::extract::request_integer;
use crate::platform::state::AppState;
use axum::extract::Extension;
use axum::extract::Query;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::Response;
use axum::Json;
use serde_json::json;
use serde_json::Value;
use sqlx::Row;
use std::{future::Future, sync::Arc, time::Duration};
use uuid::Uuid;

static STOCK_BACKFILL_LOCK: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());

struct Fixture {
    state: AppState,
    pool: sqlx::MySqlPool,
    owner: AuthUser,
    stranger: AuthUser,
    catalog_id: i32,
    stocks: tokio::sync::Mutex<Vec<i32>>,
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
        let owner = AuthUser {
            id: Uuid::new_v4().to_string(),
            is_admin: false,
        };
        let stranger = AuthUser {
            id: Uuid::new_v4().to_string(),
            is_admin: false,
        };
        for user in [&owner, &stranger] {
            sqlx::query("INSERT INTO t_user(id) VALUES (?)")
                .bind(&user.id)
                .execute(&pool)
                .await
                .unwrap();
        }
        // This catalogue has no authoring API. Only source data is seeded; add/begin/pause/finish
        // below use the handlers, and the answer is derived from the frozen API response.
        let catalog_id = -((Uuid::new_v4().as_u128() % 1_000_000_000) as i32) - 1;
        sqlx::query("INSERT INTO t_entering_exercise_word_stock(id,type,content) VALUES (?,0,?)")
            .bind(catalog_id)
            .bind(r#"[{"font":"甲","pys":"a"},{"font":"乙","pys":"b"}]"#)
            .execute(&pool)
            .await
            .unwrap();
        let state = AppState {
            config: Arc::new(AppConfig::from_env().unwrap()),
            pool: Some(pool.clone()),
            database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
        };
        Self {
            state,
            pool,
            owner,
            stranger,
            catalog_id,
            stocks: tokio::sync::Mutex::new(Vec::new()),
            triggers: tokio::sync::Mutex::new(Vec::new()),
        }
    }

    async fn pre(&self) -> (String, Value) {
        let response = document(
            add(
                State(self.state.clone()),
                Extension(self.owner.clone()),
                Json(json!({"type":0,"name":"input writer regression"})),
            )
            .await,
        )
        .await;
        assert_eq!(response["code"], 200, "{response}");
        let rows: Value =
            serde_json::from_str(response["data"]["content"].as_str().unwrap()).unwrap();
        let answers: Vec<Value> = rows
            .as_array()
            .unwrap()
            .iter()
            .map(|row| {
                let code: String = row["pys"]
                    .as_array()
                    .unwrap()
                    .iter()
                    .map(|letter| letter["py"].as_str().unwrap())
                    .collect();
                json!({"value":code})
            })
            .collect();
        (
            response["data"]["id"].as_str().unwrap().to_owned(),
            json!(answers),
        )
    }

    async fn stock(&self, kind: i32, content: &str) -> i32 {
        let response = document(
            post_entering::word_stock_add(
                State(self.state.clone()),
                Extension(AuthUser {
                    id: self.owner.id.clone(),
                    is_admin: true,
                }),
                Json(json!({"type":kind,"name":"input stock regression","content":content})),
            )
            .await,
        )
        .await;
        assert_eq!(response["code"], 200, "{response}");
        let id = response["data"]["id"].as_i64().unwrap() as i32;
        self.stocks.lock().await.push(id);
        id
    }

    async fn post(&self) -> String {
        let stock = self.stock(2, "ALPHA BETA!").await;
        let response = document(
            post_entering::add(
                State(self.state.clone()),
                Extension(self.owner.clone()),
                Json(json!({"type":"2","wordId":stock.to_string(),"name":"input post regression"})),
            )
            .await,
        )
        .await;
        assert_eq!(response["code"], 200, "{response}");
        response["data"]["id"].as_str().unwrap().to_owned()
    }

    async fn register_trigger(&self, name: &str) {
        self.triggers.lock().await.push(name.to_owned());
    }

    async fn drop_trigger(&self, name: &str) {
        sqlx::raw_sql(&format!("DROP TRIGGER IF EXISTS {name}"))
            .execute(&self.pool)
            .await
            .unwrap();
    }

    async fn cleanup(&self) -> Result<(), sqlx::Error> {
        // Drain cancelled handler transactions before dropping their scoped triggers.
        self.pool.close().await;
        let url = std::env::var("RUST_PHASE2_DATABASE_URL").unwrap();
        let pool = sqlx::mysql::MySqlPoolOptions::new()
            .max_connections(1)
            .connect(&url)
            .await?;
        for trigger in std::mem::take(&mut *self.triggers.lock().await) {
            sqlx::raw_sql(&format!("DROP TRIGGER IF EXISTS {trigger}"))
                .execute(&pool)
                .await?;
        }
        for id in std::mem::take(&mut *self.stocks.lock().await) {
            sqlx::query("DELETE FROM t_post_entering_exercise_word_stock WHERE id=?")
                .bind(id)
                .execute(&pool)
                .await?;
        }
        for user in [&self.owner, &self.stranger] {
            for table in ["t_entering_exercise", "t_post_entering_exercise"] {
                let ids: Vec<String> = sqlx::query_scalar(&format!(
                    "SELECT id FROM {table} WHERE create_user_id=? ORDER BY id"
                ))
                .bind(&user.id)
                .fetch_all(&pool)
                .await?;
                for id in ids {
                    sqlx::query(&format!("DELETE FROM {table} WHERE id=?"))
                        .bind(id)
                        .execute(&pool)
                        .await?;
                }
            }
            let ids: Vec<String> = sqlx::query_scalar(
                "SELECT id FROM t_entering_statistical WHERE user_id=? ORDER BY id",
            )
            .bind(&user.id)
            .fetch_all(&pool)
            .await?;
            for id in ids {
                sqlx::query("DELETE FROM t_entering_statistical WHERE id=?")
                    .bind(id)
                    .execute(&pool)
                    .await?;
            }
            sqlx::query("DELETE FROM t_user WHERE id=?")
                .bind(&user.id)
                .execute(&pool)
                .await?;
        }
        sqlx::query("DELETE FROM t_entering_exercise_word_stock WHERE id=?")
            .bind(self.catalog_id)
            .execute(&pool)
            .await?;
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
    let working = fixture.clone();
    let mut task = tokio::spawn(async move { case(working).await });
    let (result, timed_out) = match tokio::time::timeout(Duration::from_secs(60), &mut task).await {
        Ok(result) => (result, false),
        Err(_) => {
            task.abort();
            ((&mut task).await, true)
        }
    };
    let cleanup = fixture.cleanup().await;
    if timed_out {
        if let Err(error) = cleanup {
            eprintln!("input fixture cleanup also failed: {error}");
        }
        panic!("input fixture case timed out; aborted and awaited");
    }
    match result {
        Ok(()) => cleanup.expect("input fixture cleanup"),
        Err(error) => {
            if let Err(cleanup) = cleanup {
                eprintln!("input fixture cleanup also failed: {cleanup}");
            }
            if error.is_panic() {
                std::panic::resume_unwind(error.into_panic());
            }
            panic!("input fixture case cancelled: {error}");
        }
    }
}

struct HandlerTask(tokio::task::JoinHandle<Response>);

impl HandlerTask {
    fn spawn(future: impl Future<Output = Response> + Send + 'static) -> Self {
        Self(tokio::spawn(future))
    }

    async fn join(mut self) -> Result<Response, String> {
        match tokio::time::timeout(Duration::from_secs(5), &mut self.0).await {
            Ok(result) => result.map_err(|error| error.to_string()),
            Err(_) => {
                self.0.abort();
                let outcome = (&mut self.0).await;
                Err(format!(
                    "input handler timeout; aborted and awaited: {outcome:?}"
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

async fn owner_gate(
    pool: &sqlx::MySqlPool,
    id: &str,
    table: &str,
) -> (sqlx::Transaction<'static, sqlx::MySql>, u64) {
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

async fn waiting(pool: &sqlx::MySqlPool, connection: u64, table: &str, minimum: i64) -> bool {
    tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let count: i64 = sqlx::query_scalar("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME=?")
                .bind(connection).bind(table).fetch_one(pool).await.unwrap();
            if count >= minimum { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok()
}

#[test]
fn shared_scalar_binding_handles_nullable_and_malformed_previews() {
    assert_eq!(request_double(Some(&json!(" null "))).unwrap(), None);
    assert_eq!(request_double(Some(&json!(" 0.5 "))).unwrap(), Some(0.5));
    assert!(request_double(Some(&json!("NaN")))
        .unwrap()
        .unwrap()
        .is_nan());
    assert_eq!(
        request_double(Some(&json!([]))).unwrap_err().status(),
        StatusCode::BAD_REQUEST
    );
    assert_eq!(request_integer(Some(&json!(" 2 "))).unwrap(), Some(2));
    assert_eq!(
        request_string(Some(&json!(42))).unwrap().as_deref(),
        Some("42")
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn pre_input_pause_excludes_idle_time_and_settlement_retries_do_not_double_count() {
    with_fixture(|fixture| async move {
    let (id, answers) = fixture.pre().await;
    let body = json!({"id":id,"content":answers.to_string(),"duration":999999,"correctNum":999999,"accuracy":999.0,"speed":999999});
    let started = document(
        begin(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(json!({"id":id})),
        )
        .await,
    )
    .await;
    sqlx::query("UPDATE t_entering_exercise SET active_started_at=DATE_SUB(NOW(6),INTERVAL 60 SECOND) WHERE id=?")
        .bind(&id).execute(&fixture.pool).await.unwrap();
    let paused = document(
        pause(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(body.clone()),
        )
        .await,
    )
    .await;
    let before: i64 =
        sqlx::query_scalar("SELECT elapsed_millis FROM t_entering_exercise WHERE id=?")
            .bind(&id)
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
    tokio::time::sleep(Duration::from_millis(1100)).await;
    let retry = document(
        pause(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(body.clone()),
        )
        .await,
    )
    .await;
    let finished = document(
        finish(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(body.clone()),
        )
        .await,
    )
    .await;
    let repeated = document(
        finish(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(body),
        )
        .await,
    )
    .await;
    let changed = document(
        finish(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(json!({"id":id,"content":"[]"})),
        )
        .await,
    )
    .await;
    let elapsed: i64 =
        sqlx::query_scalar("SELECT elapsed_millis FROM t_entering_exercise WHERE id=?")
            .bind(&id)
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
    let count: i32 = sqlx::query_scalar("SELECT total_count FROM t_entering_statistical WHERE user_id=? AND type=0 AND child_type=0")
        .bind(&fixture.owner.id).fetch_one(&fixture.pool).await.unwrap();
    assert_eq!(started["code"], 200);
    for response in [&paused, &retry, &finished, &repeated] {
        assert_eq!(response["code"], 200, "{response}");
    }
    assert_eq!(
        paused["data"]["correctNum"],
        answers.as_array().unwrap().len()
    );
    assert_eq!(paused["data"]["errorNum"], 0);
    assert_eq!(paused["data"]["accuracy"], 100.0);
    assert_eq!(paused["data"]["duration"], retry["data"]["duration"]);
    assert_eq!(before, elapsed, "paused idle time must not be charged");
    assert_eq!(count, 1);
    assert_eq!(changed["code"], 208);
    }).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn post_input_finish_orders_owner_state_and_retry_before_metric_rejection() {
    with_fixture(|fixture| async move {
    let id = fixture.post().await;
    let foreign = document(
        post_entering::finish(
            State(fixture.state.clone()),
            Extension(fixture.stranger.clone()),
            Json(json!({"id":id,"speed":999})),
        )
        .await,
    )
    .await;
    let unstarted = document(
        post_entering::finish(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(json!({"id":id,"speed":999})),
        )
        .await,
    )
    .await;
    let started = document(
        post_entering::begin(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(json!({"id":id})),
        )
        .await,
    )
    .await;
    let answers =
        r#"[{"font":"ALPHA BETA !","value":"ALPHA WRONG !","isFirst":true,"trueOrfalse":true}]"#;
    let rejected = document(
        post_entering::finish(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(json!({"id":id,"content":answers,"speed":999})),
        )
        .await,
    )
    .await;
    let saved = document(
        post_entering::finish(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(json!({"id":id,"content":answers})),
        )
        .await,
    )
    .await;
    let retry = document(
        post_entering::finish(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(json!({"id":id,"content":answers,"speed":"999"})),
        )
        .await,
    )
    .await;
    let malformed = document(
        post_entering::finish(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(json!({"id":id,"content":"not json"})),
        )
        .await,
    )
    .await;
    let missing = document(
        post_entering::finish(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(json!({"id":id})),
        )
        .await,
    )
    .await;
    let view = document(
        post_entering::get_by_id(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(json!({"id":id})),
        )
        .await,
    )
    .await;
    assert_eq!(foreign["code"], 207);
    assert_eq!(unstarted["message"], "训练尚未开始");
    assert_eq!(started["code"], 200);
    assert_eq!(
        rejected["message"],
        "成绩和用时由服务端计算，请仅提交录入内容"
    );
    assert_eq!(saved["code"], 200);
    assert_eq!(retry["code"], 200);
    assert_eq!(malformed["code"], 202);
    assert_eq!(missing["code"], 208);
    assert_eq!(view["data"]["correctNum"], 2);
    assert_eq!(view["data"]["errorNum"], 1);
    assert_eq!(view["data"]["accuracy"], 66.67);
    }).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema"]
async fn pre_input_queued_finish_reads_totals_after_the_owner_lock() {
    with_fixture(|fixture| async move {
    let (id, answers) = fixture.pre().await;
    let (other, other_answers) = fixture.pre().await;
    for train in [&id, &other] {
        let response = document(
            begin(
                State(fixture.state.clone()),
                Extension(fixture.owner.clone()),
                Json(json!({"id":train})),
            )
            .await,
        )
        .await;
        assert_eq!(response["code"], 200);
    }
    let (gate, connection) = owner_gate(&fixture.pool, &fixture.owner.id, "t_user").await;
    let state = fixture.state.clone();
    let actor = fixture.owner.clone();
    let target = id.clone();
    let queued = HandlerTask::spawn(async move {
        finish(
            State(state),
            Extension(actor),
            Json(json!({"id":target,"content":answers.to_string()})),
        )
        .await
    });
    let blocked = waiting(&fixture.pool, connection, "t_user", 1).await;
    // A real second settlement is queued behind the same owner. No totals or scores are seeded.
    let state = fixture.state.clone();
    let actor = fixture.owner.clone();
    let second = HandlerTask::spawn(async move {
        finish(
            State(state),
            Extension(actor),
            Json(json!({"id":other,"content":other_answers.to_string()})),
        )
        .await
    });
    let both_blocked = waiting(&fixture.pool, connection, "t_user", 2).await;
    gate.commit().await.unwrap();
    let first = match queued.join().await {
        Ok(response) => Some(document(response).await),
        Err(_) => None,
    };
    let second = match second.join().await {
        Ok(response) => Some(document(response).await),
        Err(_) => None,
    };
    let count: Option<i32> = sqlx::query_scalar("SELECT total_count FROM t_entering_statistical WHERE user_id=? AND type=0 AND child_type=0")
        .bind(&fixture.owner.id).fetch_optional(&fixture.pool).await.unwrap();
    assert!(blocked, "settlement must first serialize on its owner");
    assert!(both_blocked, "both settlements must be queued behind the owner before release");
    assert_eq!(
        first.as_ref().map(|response| &response["code"]),
        Some(&json!(200))
    );
    assert_eq!(
        second.as_ref().map(|response| &response["code"]),
        Some(&json!(200))
    );
    assert_eq!(count, Some(2));
    }).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn pre_input_statistics_failure_rolls_back_answers_and_terminal_state() {
    with_fixture(|fixture| async move {
    let (id, answers) = fixture.pre().await;
    let started = document(
        begin(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(json!({"id":id})),
        )
        .await,
    )
    .await;
    let trigger = format!("input_rollback_{}", Uuid::new_v4().simple());
    let ddl = format!("CREATE TRIGGER {trigger} BEFORE INSERT ON t_entering_statistical FOR EACH ROW BEGIN IF NEW.user_id='{}' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='input statistics rollback'; END IF; END", fixture.owner.id);
    fixture.register_trigger(&trigger).await;
    sqlx::raw_sql(&ddl).execute(&fixture.pool).await.unwrap();
    let response = document(
        finish(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(json!({"id":id,"content":answers.to_string()})),
        )
        .await,
    )
    .await;
    fixture.drop_trigger(&trigger).await;
    let row = sqlx::query("SELECT status,correct_num,error_num,CAST(end_time AS CHAR) end_time,content FROM t_entering_exercise WHERE id=?")
        .bind(&id).fetch_one(&fixture.pool).await.unwrap();
    let content: Value = serde_json::from_str(row.get::<&str, _>("content")).unwrap();
    let status: i32 = row.get("status");
    let correct: i32 = row.get("correct_num");
    let errors: i32 = row.get("error_num");
    let ended: Option<String> = row.get("end_time");
    assert_eq!(started["code"], 200);
    assert_eq!(response["code"], 500);
    assert_eq!((status, correct, errors), (1, 0, 0));
    assert!(ended.is_none());
    assert!(content
        .as_array()
        .unwrap()
        .iter()
        .all(|row| row["value"] == ""));
    }).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema"]
async fn post_input_queued_lazy_size_uses_current_catalog_after_edit() {
    let _serial = STOCK_BACKFILL_LOCK.lock().await;
    with_fixture(|fixture| async move {
        let stock = fixture.stock(2, "OLD").await;
        // A legacy null wordSize is metadata, not a fabricated answer or score.
        sqlx::query("UPDATE t_post_entering_exercise_word_stock SET word_size=NULL WHERE id=?")
            .bind(stock)
            .execute(&fixture.pool)
            .await
            .unwrap();
        let (mut gate, connection) = owner_gate(
            &fixture.pool,
            &stock.to_string(),
            "t_post_entering_exercise_word_stock",
        )
        .await;
        let state = fixture.state.clone();
        let queued = HandlerTask::spawn(async move {
            post_entering::word_stock_list_page(State(state), Json(json!({"type":2}))).await
        });
        let blocked = waiting(
            &fixture.pool,
            connection,
            "t_post_entering_exercise_word_stock",
            1,
        )
        .await;
        sqlx::query("UPDATE t_post_entering_exercise_word_stock SET content=? WHERE id=?")
            .bind(r#"["NEW CONTENT HAS FOUR"]"#)
            .bind(stock)
            .execute(&mut *gate)
            .await
            .unwrap();
        gate.commit().await.unwrap();
        let response = match queued.join().await {
            Ok(response) => Some(document(response).await),
            Err(_) => None,
        };
        let size: Option<i32> = sqlx::query_scalar(
            "SELECT word_size FROM t_post_entering_exercise_word_stock WHERE id=?",
        )
        .bind(stock)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
        let edited = document(
            post_entering::word_stock_add(
                State(fixture.state.clone()),
                Extension(AuthUser {
                    id: fixture.owner.id.clone(),
                    is_admin: true,
                }),
                Json(json!({"id":stock,"type":2,"name":"edited","content":"SHORT"})),
            )
            .await,
        )
        .await;
        assert!(
            blocked,
            "lazy initialization must wait at the catalog PRIMARY row"
        );
        let response = response.expect("queued backfill must complete");
        assert_eq!(response["code"], 200);
        let row = response["data"]
            .as_array()
            .unwrap()
            .iter()
            .find(|row| row["id"] == stock)
            .unwrap();
        assert_eq!(row["content"], r#"["NEW CONTENT HAS FOUR"]"#);
        assert_eq!(row["wordSize"], 4);
        assert_eq!(size, Some(4));
        assert_eq!(
            edited["data"]["wordSize"], 4,
            "edits retain already populated Java metadata"
        );
    })
    .await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema"]
async fn post_input_concurrent_delete_has_one_success_and_one_missing_train() {
    with_fixture(|fixture| async move {
        let id = fixture.post().await;
        let (gate, connection) = owner_gate(&fixture.pool, &id, "t_post_entering_exercise").await;
        let mut tasks = Vec::new();
        for _ in 0..2 {
            let state = fixture.state.clone();
            let actor = fixture.owner.clone();
            let target = id.clone();
            tasks.push(HandlerTask::spawn(async move {
                post_entering::delete(
                    State(state),
                    Extension(actor),
                    Query(std::collections::HashMap::from([(
                        "trainId".to_owned(),
                        target,
                    )])),
                )
                .await
            }));
        }
        let blocked = waiting(&fixture.pool, connection, "t_post_entering_exercise", 2).await;
        gate.commit().await.unwrap();
        let mut codes = Vec::new();
        for task in tasks {
            if let Ok(response) = task.join().await {
                codes.push(document(response).await["code"].as_i64().unwrap());
            }
        }
        assert!(
            blocked,
            "both deletes must serialize at the training PRIMARY row"
        );
        codes.sort_unstable();
        assert_eq!(codes, [200, 202]);
    })
    .await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn post_input_lazy_size_failure_is_rolled_back_and_reported() {
    let _serial = STOCK_BACKFILL_LOCK.lock().await;
    with_fixture(|fixture| async move {
    let stock = fixture.stock(2, "ONE TWO").await;
    sqlx::query("UPDATE t_post_entering_exercise_word_stock SET word_size=NULL WHERE id=?")
        .bind(stock)
        .execute(&fixture.pool)
        .await
        .unwrap();
    let trigger = format!("input_size_{}", Uuid::new_v4().simple());
    fixture.register_trigger(&trigger).await;
    sqlx::raw_sql(&format!("CREATE TRIGGER {trigger} BEFORE UPDATE ON t_post_entering_exercise_word_stock FOR EACH ROW BEGIN IF NEW.id={stock} AND NEW.word_size IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='input size rollback'; END IF; END"))
        .execute(&fixture.pool).await.unwrap();
    let response = document(
        post_entering::word_stock_list_page(State(fixture.state.clone()), Json(json!({"type":2})))
            .await,
    )
    .await;
    fixture.drop_trigger(&trigger).await;
    let size: Option<i32> =
        sqlx::query_scalar("SELECT word_size FROM t_post_entering_exercise_word_stock WHERE id=?")
            .bind(stock)
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
    assert_eq!(response["code"], 500);
    assert_eq!(size, None);
    }).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn post_input_datetime_projections_match_java_across_lifecycle_and_lists() {
    with_fixture(|fixture| async move {
        let id = fixture.post().await;
        sqlx::query("UPDATE t_post_entering_exercise SET create_time='2026-10-09 12:34:56.123456' WHERE id=?")
            .bind(&id).execute(&fixture.pool).await.unwrap();
        let created = document(post_entering::get_by_id(
            State(fixture.state.clone()), Extension(fixture.owner.clone()), Json(json!({"id":id})),
        ).await).await;
        let created_list = document(post_entering::list_page(
            State(fixture.state.clone()), Extension(fixture.owner.clone()), Json(json!({"type":2})),
        ).await).await;
        let begun = document(post_entering::begin(
            State(fixture.state.clone()), Extension(fixture.owner.clone()), Json(json!({"id":id})),
        ).await).await;
        sqlx::query("UPDATE t_post_entering_exercise SET start_time=DATE_SUB(NOW(6),INTERVAL 60 SECOND) WHERE id=?")
            .bind(&id).execute(&fixture.pool).await.unwrap();
        let active = document(post_entering::get_by_id(
            State(fixture.state.clone()), Extension(fixture.owner.clone()), Json(json!({"id":id})),
        ).await).await;
        let settled = document(post_entering::finish(
            State(fixture.state.clone()), Extension(fixture.owner.clone()),
            Json(json!({"id":id,"content":r#"[{"font":"ALPHA BETA !","value":"ALPHA BETA !","isFirst":true}]"#})),
        ).await).await;
        let finished = document(post_entering::get_by_id(
            State(fixture.state.clone()), Extension(fixture.owner.clone()), Json(json!({"id":id})),
        ).await).await;
        let finished_list = document(post_entering::list_page(
            State(fixture.state.clone()), Extension(fixture.owner.clone()), Json(json!({"type":2})),
        ).await).await;
        for response in [&created, &created_list, &begun, &active, &settled, &finished, &finished_list] {
            assert_eq!(response["code"], 200, "{response}");
        }
        assert_eq!(created["data"]["createTime"], "2026-10-09 12:34:56");
        assert_eq!(created["data"]["startTime"], Value::Null);
        assert_eq!(created["data"]["endTime"], Value::Null);
        assert_eq!(created_list["data"][0]["createTime"], created["data"]["createTime"]);
        assert_eq!(created_list["data"][0]["content"], Value::Null);
        assert_eq!(active["data"]["status"], 1);
        assert_eq!(finished["data"]["status"], 2);
        assert_eq!(finished["data"]["createTime"], created["data"]["createTime"]);
        assert_eq!(finished["data"]["startTime"], active["data"]["startTime"]);
        for field in ["startTime", "endTime"] {
            let value = finished["data"][field].as_str().unwrap();
            assert_eq!(value.len(), 19, "Java DTO timestamps have second precision");
            chrono::NaiveDateTime::parse_from_str(value, "%Y-%m-%d %H:%M:%S").unwrap();
            assert_eq!(finished_list["data"][0][field], finished["data"][field]);
        }
        assert_eq!(finished["data"]["correctNum"], 3);
        assert_eq!(finished["data"]["errorNum"], 0);
        assert_eq!(finished["data"]["accuracy"], 100.0);
    }).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo with authentication tables"]
async fn authenticated_preview_accepts_ten_mib_and_rejects_larger_files() {
    use axum::{body::Body, http::Request};
    use tower::ServiceExt;

    with_fixture(|fixture| async move {
        let token = Uuid::new_v4().to_string();
        let device = "input-preview-boundary";
        sqlx::query("UPDATE t_user SET token=?,device_id=? WHERE id=?")
            .bind(crate::platform::auth::token_digest(&token))
            .bind(device)
            .bind(&fixture.owner.id)
            .execute(&fixture.pool)
            .await
            .unwrap();
        let mut config = AppConfig::from_env().unwrap();
        config.entering_readonly_enabled = true;
        config.body_limit = 11 * 1024 * 1024;
        let mut state = fixture.state.clone();
        state.config = Arc::new(config);
        let app = crate::router::build_router(state);
        for (file_size, expected_code) in [
            (2 * 1024 * 1024 + 1, 200),
            (10 * 1024 * 1024, 200),
            (10 * 1024 * 1024 + 1, 202),
        ] {
            let mut body = b"--input-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"input.txt\"\r\nContent-Type: text/plain\r\n\r\n".to_vec();
            body.resize(body.len() + file_size, b'A');
            body.extend_from_slice(b"\r\n--input-boundary--\r\n");
            let request = Request::builder()
                .method("POST")
                .uri("/api/postEnteringExerciseWordStock/view")
                .header("token", &token)
                .header("deviceId", device)
                .header("content-type", "multipart/form-data; boundary=input-boundary")
                .body(Body::from(body))
                .unwrap();
            let response = app.clone().oneshot(request).await.unwrap();
            assert_eq!(response.status(), StatusCode::OK);
            let result = document(response).await;
            assert_eq!(result["code"], expected_code, "file bytes: {file_size}");
            if expected_code == 200 {
                let content = result["data"]["content"].as_str().unwrap();
                assert_eq!(content.len(), file_size);
                assert!(content.bytes().all(|byte| byte == b'A'));
                assert_eq!(result["data"]["wordSize"].as_u64(), Some(file_size as u64));
            } else {
                assert_eq!(result["data"], Value::Null);
            }
        }
    }).await;
}
