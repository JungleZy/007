use crate::domain::post_ticker::post_ticker::*;
use crate::platform::auth::AuthUser;
use crate::platform::state::AppState;
use axum::extract::Extension;
use axum::extract::Query;
use axum::extract::State;
use axum::response::Response;
use axum::Json;
use serde_json::json;
use serde_json::Value;
use sqlx::mysql::MySqlRow;
use sqlx::Row;
use sqlx::{mysql::MySqlPoolOptions, MySqlPool};
use std::{future::Future, sync::Arc, time::Duration};

static CASE_LOCK: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());
const SETTINGS: [&str; 2] = [
    "t_ticker_tape_train_setting",
    "t_post_ticker_tape_train_setting",
];

struct Fixture {
    state: AppState,
    owner: AuthUser,
    other: AuthUser,
    target: String,
    unrelated: String,
    tag: String,
    triggers: tokio::sync::Mutex<Vec<String>>,
    settings: Vec<Vec<MySqlRow>>,
}

async fn document(response: Response) -> Value {
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    serde_json::from_slice(&bytes).unwrap()
}

impl Fixture {
    async fn new() -> Self {
        let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("isolated database URL");
        assert!(url.ends_with("/rust_scratch_lockdemo"));
        let pool = MySqlPoolOptions::new()
            .max_connections(8)
            .connect(&url)
            .await
            .unwrap();
        for table in [
            "t_post_ticker_tape_train",
            "t_post_ticker_tape_train_page",
            "t_post_ticker_tape_train_page_value",
            SETTINGS[0],
            SETTINGS[1],
        ] {
            let engine: String = sqlx::query_scalar("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")
                .bind(table).fetch_one(&pool).await.unwrap();
            assert_eq!(engine, "InnoDB", "rollback requires transactional {table}");
        }
        for table in SETTINGS {
            let indexes: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")
                .bind(table).fetch_one(&pool).await.unwrap();
            assert_eq!(
                indexes, 0,
                "this regression exercises the actual unindexed settings schema"
            );
        }
        let mut settings = Vec::new();
        for table in SETTINGS {
            settings.push(
                sqlx::query(&format!("SELECT * FROM {table}"))
                    .fetch_all(&pool)
                    .await
                    .unwrap(),
            );
        }
        let tag = uuid::Uuid::new_v4().simple().to_string();
        let owner = AuthUser {
            id: format!("rx-owner-{tag}"),
            is_admin: true,
        };
        let other = AuthUser {
            id: format!("rx-other-{tag}"),
            is_admin: true,
        };
        for actor in [&owner, &other] {
            sqlx::query("INSERT INTO t_user (id,user_name) VALUES (?,'receive regression')")
                .bind(&actor.id)
                .execute(&pool)
                .await
                .unwrap();
        }
        let state = AppState {
            config: Arc::new(crate::platform::config::AppConfig::from_env().unwrap()),
            pool: Some(pool),
            database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
        };
        let mut ids = Vec::new();
        for actor in [&owner, &other] {
            let created = document(add(State(state.clone()), Extension(actor.clone()), Json(json!({
                "name":format!("receive-{tag}"),"isCable":0,"rate":40,"type":0,"totalNumber":5,
                "isAvg":0,"isRandom":0,"codeMessageBody":[],"codeShort":0,"disturb":"[]","ratio":0,"isStartSign":1
            }))).await).await;
            assert_eq!(created["code"], 200);
            ids.push(created["data"]["id"].as_str().unwrap().to_owned());
        }
        let fixture = Self {
            state,
            owner,
            other,
            target: ids.remove(0),
            unrelated: ids.remove(0),
            tag,
            triggers: tokio::sync::Mutex::new(Vec::new()),
            settings,
        };
        for (id, prefix) in [(&fixture.target, "zzz"), (&fixture.unrelated, "aaa")] {
            let rows = sqlx::query(
                "SELECT id,sort FROM t_post_ticker_tape_train_page WHERE train_id=? ORDER BY sort",
            )
            .bind(id)
            .fetch_all(fixture.pool())
            .await
            .unwrap();
            for row in rows {
                sqlx::query("UPDATE t_post_ticker_tape_train_page SET id=? WHERE id=?")
                    .bind(format!(
                        "{prefix}-{}-{}",
                        fixture.tag,
                        row.get::<i32, _>("sort")
                    ))
                    .bind(row.get::<String, _>("id"))
                    .execute(fixture.pool())
                    .await
                    .unwrap();
            }
        }
        fixture
    }

    fn pool(&self) -> &MySqlPool {
        self.state.pool.as_ref().unwrap()
    }

    async fn trigger(&self, table: &str, event: &str, condition: &str) -> String {
        let name = format!("rx_{}", uuid::Uuid::new_v4().simple());
        self.triggers.lock().await.push(name.clone());
        sqlx::raw_sql(&format!("CREATE TRIGGER {name} BEFORE {event} ON {table} FOR EACH ROW BEGIN IF {condition} THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='receive rollback injection'; END IF; END"))
            .execute(self.pool()).await.unwrap();
        name
    }

    async fn drop_trigger(&self, name: &str) {
        sqlx::raw_sql(&format!("DROP TRIGGER IF EXISTS {name}"))
            .execute(self.pool())
            .await
            .unwrap();
    }

    async fn cleanup(&self) -> Result<(), sqlx::Error> {
        self.pool().close().await;
        let url = std::env::var("RUST_PHASE2_DATABASE_URL").unwrap();
        let pool = MySqlPoolOptions::new()
            .max_connections(1)
            .connect(&url)
            .await?;
        for trigger in std::mem::take(&mut *self.triggers.lock().await) {
            sqlx::raw_sql(&format!("DROP TRIGGER IF EXISTS {trigger}"))
                .execute(&pool)
                .await?;
        }
        let mut tx = pool.begin().await?;
        for id in [&self.target, &self.unrelated] {
            delete_values(&mut tx, id).await.map_err(database_error)?;
            delete_pages(&mut tx, id).await.map_err(database_error)?;
            sqlx::query("DELETE FROM t_post_ticker_tape_train WHERE id=?")
                .bind(id)
                .execute(&mut *tx)
                .await?;
        }
        for (table, rows) in SETTINGS.into_iter().zip(&self.settings) {
            sqlx::query(&format!("DELETE FROM {table}"))
                .execute(&mut *tx)
                .await?;
            for row in rows {
                sqlx::query(&format!("INSERT INTO {table} (id,type,rate,text,create_time,`key`,value,dot) VALUES (?,?,?,?,?,?,?,?)"))
                    .bind(row.get::<Option<String>,_>("id")).bind(row.get::<Option<String>,_>("type"))
                    .bind(row.get::<Option<i32>,_>("rate")).bind(row.get::<Option<String>,_>("text"))
                    .bind(row.get::<Option<chrono::NaiveDateTime>,_>("create_time"))
                    .bind(row.get::<Option<String>,_>("key")).bind(row.get::<Option<String>,_>("value"))
                    .bind(row.get::<Option<i32>,_>("dot")).execute(&mut *tx).await?;
            }
        }
        for actor in [&self.owner, &self.other] {
            sqlx::query("DELETE FROM t_user WHERE id=?")
                .bind(&actor.id)
                .execute(&mut *tx)
                .await?;
        }
        tx.commit().await?;
        pool.close().await;
        Ok(())
    }
}

fn database_error(error: Error) -> sqlx::Error {
    match error {
        Error::Database(error) => error,
        other => sqlx::Error::Protocol(format!("{other:?}")),
    }
}

async fn with_fixture<F, Fut>(case: F)
where
    F: FnOnce(Arc<Fixture>) -> Fut + Send + 'static,
    Fut: Future<Output = ()> + Send + 'static,
{
    let _serial = CASE_LOCK.lock().await;
    let fixture = Arc::new(Fixture::new().await);
    let working = fixture.clone();
    let result = tokio::spawn(async move { case(working).await }).await;
    let cleanup = fixture.cleanup().await;
    match result {
        Ok(()) => cleanup.expect("receive fixture cleanup"),
        Err(error) => {
            if let Err(cleanup) = cleanup {
                eprintln!("receive cleanup also failed: {cleanup}");
            }
            if error.is_panic() {
                std::panic::resume_unwind(error.into_panic());
            }
            panic!("receive fixture task cancelled: {error}");
        }
    }
}

struct HandlerTask(tokio::task::JoinHandle<Response>);
impl HandlerTask {
    fn spawn(future: impl Future<Output = Response> + Send + 'static) -> Self {
        Self(tokio::spawn(future))
    }
    async fn join(mut self) -> Result<Response, String> {
        match tokio::time::timeout(Duration::from_secs(8), &mut self.0).await {
            Ok(result) => result.map_err(|error| error.to_string()),
            Err(_) => {
                self.0.abort();
                let outcome = (&mut self.0).await;
                Err(format!(
                    "receive handler timeout; aborted and awaited: {outcome:?}"
                ))
            }
        }
    }
    async fn cancel(mut self) {
        self.0.abort();
        let _ = (&mut self.0).await;
    }
}
impl Drop for HandlerTask {
    fn drop(&mut self) {
        self.0.abort();
    }
}

async fn invoke(state: AppState, user: AuthUser, id: String, operation: &str) -> Response {
    match operation {
        "reset" => {
            reset(
                State(state),
                Extension(user),
                Json(IdRequest { id: Some(id) }),
            )
            .await
        }
        "delete" => {
            delete(
                State(state),
                Extension(user),
                Query(PostTickerPageQuery {
                    train_id: Some(id),
                    page_number: None,
                }),
            )
            .await
        }
        "page" => {
            find_page(
                State(state),
                Extension(user),
                Query(PostTickerPageQuery {
                    train_id: Some(id),
                    page_number: Some(3),
                }),
            )
            .await
        }
        "upload" => {
            upload_result(
                State(state),
                Extension(user),
                Json(UploadRequest {
                    id: Some(id),
                    result: Some(vec![vec![
                        "0123".into(),
                        "45678".into(),
                        "89".into(),
                        "9999".into(),
                        "".into(),
                        "EXTRA".into(),
                    ]]),
                    images: Some(vec![Some("receive-page.png".into())]),
                }),
            )
            .await
        }
        _ => panic!("unknown receive operation"),
    }
}

async fn snapshot(fixture: &Fixture) -> Value {
    let train = sqlx::query("SELECT status,score,result,more_code,lack_code,error_code,more_group,lack_group FROM t_post_ticker_tape_train WHERE id=?")
        .bind(&fixture.target).fetch_optional(fixture.pool()).await.unwrap();
    let pages = sqlx::query("SELECT id,page_number,sort,`key`,value FROM t_post_ticker_tape_train_page WHERE train_id=? ORDER BY id")
        .bind(&fixture.target).fetch_all(fixture.pool()).await.unwrap();
    let values = sqlx::query("SELECT id,page_number,value,image FROM t_post_ticker_tape_train_page_value WHERE train_id=? ORDER BY id")
        .bind(&fixture.target).fetch_all(fixture.pool()).await.unwrap();
    json!({"train":train.map(|r| json!([r.get::<Option<i32>,_>("status"),r.get::<Option<String>,_>("score"),r.get::<Option<String>,_>("result"),r.get::<Option<i32>,_>("more_code"),r.get::<Option<i32>,_>("lack_code"),r.get::<Option<i32>,_>("error_code"),r.get::<Option<i32>,_>("more_group"),r.get::<Option<i32>,_>("lack_group")])),
        "pages":pages.iter().map(|r| json!([r.get::<String,_>("id"),r.get::<Option<i32>,_>("page_number"),r.get::<Option<i32>,_>("sort"),r.get::<Option<String>,_>("key"),r.get::<Option<String>,_>("value")])).collect::<Vec<_>>(),
        "values":values.iter().map(|r| json!([r.get::<String,_>("id"),r.get::<Option<i32>,_>("page_number"),r.get::<Option<String>,_>("value"),r.get::<Option<String>,_>("image")])).collect::<Vec<_>>()})
}

async fn wait_for_row_gate(pool: &MySqlPool, connection: u64, table: &str) -> bool {
    tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let waiting: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME=?")
                .bind(connection).bind(table).fetch_one(pool).await.unwrap();
            if waiting > 0 { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok()
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema lock tables"]
async fn receive_reset_and_delete_leave_unrelated_children_writable() {
    for operation in ["reset", "delete"] {
        with_fixture(move |fixture| async move {
            for (id, actor, prefix) in [
                (&fixture.target, &fixture.owner, "zzz"),
                (&fixture.unrelated, &fixture.other, "aaa"),
            ] {
                let scored = document(
                    invoke(fixture.state.clone(), actor.clone(), id.clone(), "upload").await,
                )
                .await;
                assert_eq!(scored["code"], 200);
                sqlx::query("UPDATE t_post_ticker_tape_train_page_value SET id=? WHERE train_id=?")
                    .bind(format!("{prefix}-{}-v", fixture.tag))
                    .bind(id)
                    .execute(fixture.pool())
                    .await
                    .unwrap();
            }
            let table = if operation == "reset" {
                "t_post_ticker_tape_train_page_value"
            } else {
                "t_post_ticker_tape_train_page"
            };
            let child = format!(
                "zzz-{}-{}",
                fixture.tag,
                if operation == "reset" { "v" } else { "0" }
            );
            let mut gate = fixture.pool().begin().await.unwrap();
            let connection: u64 = sqlx::query_scalar("SELECT CONNECTION_ID()")
                .fetch_one(&mut *gate)
                .await
                .unwrap();
            sqlx::query(&format!("SELECT id FROM {table} WHERE id=? FOR UPDATE"))
                .bind(child)
                .fetch_one(&mut *gate)
                .await
                .unwrap();
            let (state, user, id) = (
                fixture.state.clone(),
                fixture.owner.clone(),
                fixture.target.clone(),
            );
            let writer =
                HandlerTask::spawn(async move { invoke(state, user, id, operation).await });
            let reached = wait_for_row_gate(fixture.pool(), connection, table).await;
            let mut probe = fixture.pool().begin().await.unwrap();
            sqlx::query("SET SESSION innodb_lock_wait_timeout=1")
                .execute(&mut *probe)
                .await
                .unwrap();
            let mut outcomes = Vec::new();
            for (table, child) in [
                (
                    "t_post_ticker_tape_train_page_value",
                    format!("aaa-{}-v", fixture.tag),
                ),
                (
                    "t_post_ticker_tape_train_page",
                    format!("aaa-{}-0", fixture.tag),
                ),
            ] {
                outcomes.push(
                    sqlx::query(&format!(
                        "UPDATE {table} SET value='unrelated writable' WHERE id=?"
                    ))
                    .bind(child)
                    .execute(&mut *probe)
                    .await
                    .is_ok(),
                );
            }
            outcomes.push(
                sqlx::query(
                    "UPDATE t_post_ticker_tape_train SET name='unrelated writable' WHERE id=?",
                )
                .bind(&fixture.unrelated)
                .execute(&mut *probe)
                .await
                .is_ok(),
            );
            probe.rollback().await.unwrap();
            gate.rollback().await.unwrap();
            let result = writer.join().await;
            assert!(
                reached,
                "caller must reach child lock after taking its parent"
            );
            assert!(
                outcomes.into_iter().all(|ok| ok),
                "{operation} must not scan-lock another training's rows"
            );
            assert_eq!(document(result.unwrap()).await["code"], 200);
        })
        .await;
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo with CREATE TRIGGER privilege"]
async fn receive_page_upload_reset_and_delete_failures_roll_back_every_write() {
    for operation in ["page", "upload", "reset", "delete"] {
        with_fixture(move |fixture| async move {
            if operation == "page" {
                sqlx::query(
                    "UPDATE t_post_ticker_tape_train SET total_number=203,type=1 WHERE id=?",
                )
                .bind(&fixture.target)
                .execute(fixture.pool())
                .await
                .unwrap();
            }
            if matches!(operation, "reset" | "delete") {
                assert_eq!(
                    document(
                        invoke(
                            fixture.state.clone(),
                            fixture.owner.clone(),
                            fixture.target.clone(),
                            "upload"
                        )
                        .await
                    )
                    .await["code"],
                    200
                );
            }
            let before = snapshot(&fixture).await;
            let (table, event, condition) = match operation {
                "page" => (
                    "t_post_ticker_tape_train_page",
                    "INSERT",
                    format!(
                        "NEW.train_id='{}' AND NEW.page_number=3 AND NEW.sort=1",
                        fixture.target
                    ),
                ),
                "upload" => (
                    "t_post_ticker_tape_train_page",
                    "UPDATE",
                    format!("NEW.id='zzz-{}-1'", fixture.tag),
                ),
                "reset" => (
                    "t_post_ticker_tape_train",
                    "UPDATE",
                    format!("NEW.id='{}' AND NEW.status=0", fixture.target),
                ),
                _ => (
                    "t_post_ticker_tape_train_page",
                    "DELETE",
                    format!("OLD.id='zzz-{}-1'", fixture.tag),
                ),
            };
            let trigger = fixture.trigger(table, event, &condition).await;
            let (state, user, id) = (
                fixture.state.clone(),
                fixture.owner.clone(),
                fixture.target.clone(),
            );
            let failed =
                HandlerTask::spawn(async move { invoke(state, user, id, operation).await })
                    .join()
                    .await;
            fixture.drop_trigger(&trigger).await;
            let after = snapshot(&fixture).await;
            assert_eq!(document(failed.unwrap()).await["code"], 500);
            assert_eq!(
                after, before,
                "{operation} must restore source, raw pages and parent score/state"
            );
            let retry = document(
                invoke(
                    fixture.state.clone(),
                    fixture.owner.clone(),
                    fixture.target.clone(),
                    operation,
                )
                .await,
            )
            .await;
            assert_eq!(
                retry["code"], 200,
                "failed transaction must remain retryable"
            );
            if operation == "upload" {
                assert_eq!(retry["data"]["score"], "94");
                for (field, expected) in [
                    ("moreCode", 1),
                    ("lackCode", 2),
                    ("errorCode", 1),
                    ("moreGroup", 1),
                    ("lackGroup", 1),
                ] {
                    assert_eq!(retry["data"][field], expected);
                }
            }
        })
        .await;
    }
}

async fn replace(state: AppState, actor: AuthUser, post: bool, rows: Value) -> Response {
    replace_request(state, actor, post, json!({"paramList": rows})).await
}

async fn replace_request(state: AppState, actor: AuthUser, post: bool, body: Value) -> Response {
    let request = serde_json::from_value(body).unwrap();
    if post {
        setting_add_or_update(State(state), Extension(actor), Ok(Json(request))).await
    } else {
        crate::domain::ticker_tape::ticker_tape::setting_add_or_update(
            State(state),
            Extension(actor),
            Ok(Json(request)),
        )
        .await
    }
}

async fn read_settings(state: AppState, post: bool) -> Value {
    document(if post {
        setting_list(State(state)).await
    } else {
        crate::domain::ticker_tape::ticker_tape::settings(State(state)).await
    })
    .await
}

async fn entire_settings_set(pool: &MySqlPool, post: bool) -> Value {
    let table = SETTINGS[usize::from(post)];
    let rows = sqlx::query(&format!("SELECT id,type,rate,text,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s.%f') created,`key`,value,dot FROM {table}"))
        .fetch_all(pool).await.unwrap();
    json!(rows.iter().map(|row| json!({
        "id":row.get::<Option<String>,_>("id"),"type":row.get::<Option<String>,_>("type"),
        "rate":row.get::<Option<i32>,_>("rate"),"text":row.get::<Option<String>,_>("text"),
        "createTime":row.get::<Option<String>,_>("created"),"key":row.get::<Option<String>,_>("key"),
        "value":row.get::<Option<String>,_>("value"),"dot":row.get::<Option<i32>,_>("dot")
    })).collect::<Vec<_>>())
}

fn setting_rows(tag: &str, label: &str) -> Value {
    json!([{"type":format!("{tag}-{label}-1"),"rate":30,"text":"first"},{"type":format!("{tag}-{label}-2"),"rate":60,"text":"second"}])
}

async fn pending_named(
    pool: &MySqlPool,
    domain: &str,
    connection: Option<u64>,
    expected: i64,
) -> bool {
    tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let found: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.metadata_locks m JOIN performance_schema.threads t ON t.THREAD_ID=m.OWNER_THREAD_ID WHERE m.OBJECT_TYPE='USER LEVEL LOCK' AND m.LOCK_STATUS='PENDING' AND m.OBJECT_NAME=CONCAT('receive-settings:',LEFT(SHA2(CONCAT(DATABASE(),':',?),256),47)) AND (? IS NULL OR t.PROCESSLIST_ID=?)")
                .bind(domain).bind(connection).bind(connection).fetch_one(pool).await.unwrap();
            if found >= expected { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok()
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo with CREATE TRIGGER and performance_schema privileges"]
async fn receive_settings_two_admins_replace_one_whole_set_and_bad_insert_restores_it() {
    for post in [false, true] {
        with_fixture(move |fixture| async move {
            assert_eq!(
                document(
                    replace(
                        fixture.state.clone(),
                        fixture.owner.clone(),
                        post,
                        json!([])
                    )
                    .await
                )
                .await["code"],
                200
            );
            let domain = if post { "post" } else { "personal" };
            let gate = crate::domain::ticker_tape::ticker_tape::settings_connection(
                fixture.pool(),
                domain,
            )
            .await
            .unwrap();
            let mut writers = Vec::new();
            for (actor, label) in [(&fixture.owner, "A"), (&fixture.other, "B")] {
                let (state, actor, rows) = (
                    fixture.state.clone(),
                    actor.clone(),
                    setting_rows(&fixture.tag, label),
                );
                writers.push(HandlerTask::spawn(async move {
                    replace(state, actor, post, rows).await
                }));
            }
            let reached = pending_named(fixture.pool(), domain, None, 2).await;
            drop(gate);
            let mut results = Vec::new();
            for writer in writers {
                results.push(writer.join().await);
            }
            let current = read_settings(fixture.state.clone(), post).await;
            assert!(
                reached,
                "both admins must wait on the same empty-set resource"
            );
            for result in results {
                assert_eq!(document(result.unwrap()).await["code"], 200);
            }
            let types: Vec<&str> = current["data"]
                .as_array()
                .unwrap()
                .iter()
                .map(|row| row["type"].as_str().unwrap())
                .collect();
            let a = [
                format!("{}-A-1", fixture.tag),
                format!("{}-A-2", fixture.tag),
            ];
            let b = [
                format!("{}-B-1", fixture.tag),
                format!("{}-B-2", fixture.tag),
            ];
            assert!(
                types == a.iter().map(String::as_str).collect::<Vec<_>>()
                    || types == b.iter().map(String::as_str).collect::<Vec<_>>(),
                "replacement must expose one complete set in submitted order"
            );
            let table = SETTINGS[usize::from(post)];
            let trigger = fixture
                .trigger(
                    table,
                    "INSERT",
                    &format!("NEW.type='{}-FAIL-2'", fixture.tag),
                )
                .await;
            let failed = replace(
                fixture.state.clone(),
                fixture.owner.clone(),
                post,
                setting_rows(&fixture.tag, "FAIL"),
            )
            .await;
            fixture.drop_trigger(&trigger).await;
            let restored = read_settings(fixture.state.clone(), post).await;
            assert_eq!(document(failed).await["code"], 500);
            assert_eq!(
                restored, current,
                "bad second insert must restore prior IDs, order and all values"
            );
        })
        .await;
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema lock tables"]
async fn receive_settings_pool_one_waiter_cancellation_closes_named_lock_session() {
    for post in [false, true] {
        with_fixture(move |fixture| async move {
            let domain = if post { "post" } else { "personal" };
            let gate = crate::domain::ticker_tape::ticker_tape::settings_connection(
                fixture.pool(),
                domain,
            )
            .await
            .unwrap();
            let url = std::env::var("RUST_PHASE2_DATABASE_URL").unwrap();
            let bounded = MySqlPoolOptions::new()
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
            let mut state = fixture.state.clone();
            state.pool = Some(bounded.clone());
            let (waiting_state, actor) = (state.clone(), fixture.owner.clone());
            let waiter =
                HandlerTask::spawn(
                    async move { replace(waiting_state, actor, post, json!([])).await },
                );
            let reached = pending_named(fixture.pool(), domain, Some(initial), 1).await;
            let extra = bounded.acquire().await;
            let bounded_wait = matches!(extra, Err(sqlx::Error::PoolTimedOut));
            drop(extra);
            waiter.cancel().await;
            drop(gate);
            let actor = fixture.owner.clone();
            let rows = setting_rows(&fixture.tag, "NEXT");
            let next = HandlerTask::spawn(async move { replace(state, actor, post, rows).await })
                .join()
                .await;
            let mut probe = bounded.acquire().await.unwrap();
            let replacement: u64 = sqlx::query_scalar("SELECT CONNECTION_ID()")
                .fetch_one(&mut *probe)
                .await
                .unwrap();
            drop(probe);
            bounded.close().await;
            assert!(
                reached,
                "caller must reach GET_LOCK while retaining its sole permit"
            );
            assert!(
                bounded_wait,
                "named-lock waiters must not exceed pool capacity"
            );
            assert_eq!(
                document(next.unwrap()).await["code"],
                200,
                "next caller must use one session without reacquiring the pool"
            );
            assert_ne!(
                replacement, initial,
                "cancelled session must be closed, never recycled with a named lock"
            );
        })
        .await;
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema lock tables"]
async fn receive_reset_then_upload_reads_current_children_after_parent_lock() {
    with_fixture(|fixture| async move {
        assert_eq!(document(invoke(fixture.state.clone(),fixture.owner.clone(),fixture.target.clone(),"upload").await).await["code"],200);
        let raw: String = sqlx::query_scalar("SELECT id FROM t_post_ticker_tape_train_page_value WHERE train_id=?")
            .bind(&fixture.target).fetch_one(fixture.pool()).await.unwrap();
        let mut gate = fixture.pool().begin().await.unwrap();
        let connection: u64 = sqlx::query_scalar("SELECT CONNECTION_ID()").fetch_one(&mut *gate).await.unwrap();
        sqlx::query("SELECT id FROM t_post_ticker_tape_train_page_value WHERE id=? FOR UPDATE").bind(raw).fetch_one(&mut *gate).await.unwrap();
        let (state,user,id) = (fixture.state.clone(),fixture.owner.clone(),fixture.target.clone());
        let resetter = HandlerTask::spawn(async move { invoke(state,user,id,"reset").await });
        let reset_waiting = wait_for_row_gate(fixture.pool(),connection,"t_post_ticker_tape_train_page_value").await;
        let (state,user,id) = (fixture.state.clone(),fixture.owner.clone(),fixture.target.clone());
        let uploader = HandlerTask::spawn(async move { invoke(state,user,id,"upload").await });
        let parent_waiting = tokio::time::timeout(Duration::from_secs(5),async {
            loop {
                let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID WHERE b.OBJECT_NAME='t_post_ticker_tape_train' AND b.LOCK_DATA LIKE CONCAT('%',?,'%')")
                    .bind(&fixture.target).fetch_one(fixture.pool()).await.unwrap();
                if count > 0 { break; }
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
        }).await.is_ok();
        gate.rollback().await.unwrap();
        let reset_result = resetter.join().await;
        let upload_result = uploader.join().await;
        let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_post_ticker_tape_train_page_value WHERE train_id=?")
            .bind(&fixture.target).fetch_one(fixture.pool()).await.unwrap();
        assert!(reset_waiting && parent_waiting,"upload must wait for reset's parent before its first child read");
        assert_eq!(document(reset_result.unwrap()).await["code"],200);
        let scored = document(upload_result.unwrap()).await;
        assert_eq!(scored["code"],200,"post-reset upload cannot use a pre-lock repeatable-read snapshot");
        assert_eq!(scored["data"]["score"],"94");
        assert_eq!(count,1,"reset and retry leave exactly one current paper row");
    }).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema lock tables"]
async fn receive_concurrent_uploads_and_lazy_page_reads_share_one_parent() {
    for operation in ["upload", "page"] {
        with_fixture(move |fixture| async move {
            if operation == "page" {
                sqlx::query("UPDATE t_post_ticker_tape_train SET total_number=203,type=1 WHERE id=?")
                    .bind(&fixture.target).execute(fixture.pool()).await.unwrap();
            }
            let mut gate = fixture.pool().begin().await.unwrap();
            let connection: u64 = sqlx::query_scalar("SELECT CONNECTION_ID()").fetch_one(&mut *gate).await.unwrap();
            sqlx::query("SELECT id FROM t_post_ticker_tape_train WHERE id=? FOR UPDATE")
                .bind(&fixture.target).fetch_one(&mut *gate).await.unwrap();
            let mut writers = Vec::new();
            for _ in 0..2 {
                let (state,user,id) = (fixture.state.clone(),fixture.owner.clone(),fixture.target.clone());
                writers.push(HandlerTask::spawn(async move { invoke(state,user,id,operation).await }));
            }
            let waiting = tokio::time::timeout(Duration::from_secs(5),async {
                loop {
                    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME='t_post_ticker_tape_train'")
                        .bind(connection).fetch_one(fixture.pool()).await.unwrap();
                    if count >= 2 { break; }
                    tokio::time::sleep(Duration::from_millis(10)).await;
                }
            }).await.is_ok();
            gate.rollback().await.unwrap();
            let mut results = Vec::new();
            for writer in writers { results.push(writer.join().await); }
            assert!(waiting,"both callers must lock the parent before touching children");
            let mut documents = Vec::new();
            for result in results { documents.push(document(result.unwrap()).await); }
            if operation == "upload" {
                let mut codes: Vec<i64> = documents.iter().map(|doc| doc["code"].as_i64().unwrap()).collect();
                codes.sort_unstable();
                assert_eq!(codes,[200,208]);
                let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_post_ticker_tape_train_page_value WHERE train_id=?")
                    .bind(&fixture.target).fetch_one(fixture.pool()).await.unwrap();
                assert_eq!(count,1,"concurrent retries cannot duplicate paper rows");
            } else {
                assert_eq!(documents[0]["code"],200);
                assert_eq!(documents[1]["code"],200);
                assert_eq!(documents[0]["data"],documents[1]["data"]);
                let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_post_ticker_tape_train_page WHERE train_id=? AND page_number=3")
                    .bind(&fixture.target).fetch_one(fixture.pool()).await.unwrap();
                assert_eq!(count,3,"lazy reads are writers and cannot duplicate the third page");
            }
        }).await;
    }
}

#[test]
fn receive_settings_dto_keeps_absent_null_empty_and_malformed_lists_distinct() {
    for input in [json!({}), json!({"paramList":null})] {
        let request: crate::domain::ticker_tape::ticker_tape::SettingRequest =
            serde_json::from_value(input).unwrap();
        assert!(request.param_list.is_none());
        assert_eq!(request.replacement_rows().err(), Some("配置不能为空"));
        assert_eq!(
            serde_json::to_value(request).unwrap(),
            json!({"paramList":null})
        );
    }
    let request: crate::domain::ticker_tape::ticker_tape::SettingRequest =
        serde_json::from_value(json!({"paramList":[]})).unwrap();
    assert_eq!(request.param_list.as_ref().unwrap().len(), 0);
    assert!(
        request.replacement_rows().unwrap().is_empty(),
        "only an explicit [] permits an empty replacement"
    );
    assert_eq!(
        serde_json::to_value(request).unwrap(),
        json!({"paramList":[]})
    );
    for malformed in [
        json!({"paramList":"not-a-list"}),
        json!({"paramList":[{"type":"valid","rate":60},"bad-second-row"]}),
    ] {
        assert!(
            serde_json::from_value::<crate::domain::ticker_tape::ticker_tape::SettingRequest>(
                malformed
            )
            .is_err()
        );
    }
    let request: crate::domain::ticker_tape::ticker_tape::SettingRequest =
        serde_json::from_value(json!({"paramList":[{"type":"slow","rate":30,"text":"first"}]}))
            .unwrap();
    let row = &request.param_list.as_ref().unwrap()[0];
    assert!(row.id.is_none());
    assert!(
        row.create_time.is_none(),
        "omitted createTime copies null over Java entity's initialized value"
    );
    assert_eq!(
        serde_json::to_value(request).unwrap(),
        json!({"paramList":[{
            "id":null,"type":"slow","rate":30,"text":"first","createTime":null
        }]})
    );
    let request: crate::domain::ticker_tape::ticker_tape::SettingRequest =
        serde_json::from_value(json!({
            "ignoredTopLevel":"not-a-declared-field",
            "paramList":[{"id":"declared-id","type":"fast","rate":60,"text":"second",
                "createTime":"2026-10-09T12:34:56","ignoredRowField":"not-a-declared-field"},{}]
        }))
        .unwrap();
    assert_eq!(
        serde_json::to_value(request).unwrap(),
        json!({"paramList":[
            {"id":"declared-id","type":"fast","rate":60,"text":"second","createTime":"2026-10-09T12:34:56"},
            {"id":null,"type":null,"rate":null,"text":null,"createTime":null}
        ]}),
        "Java returns only paramList and its five declared nullable row fields"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn receive_settings_missing_and_null_retain_entire_set_but_explicit_empty_replaces() {
    for post in [false, true] {
        with_fixture(move |fixture| async move {
            assert_eq!(document(replace(fixture.state.clone(),fixture.owner.clone(),post,setting_rows(&fixture.tag,"KEEP")).await).await["code"],200);
            let table = SETTINGS[usize::from(post)];
            sqlx::query(&format!("UPDATE {table} SET `key`='legacy-key',value='legacy-value',dot=80,create_time='2026-10-09 12:34:56.123456'"))
                .execute(fixture.pool()).await.unwrap();
            let before = entire_settings_set(fixture.pool(),post).await;
            let domain = if post { "post" } else { "personal" };
            let gate = crate::domain::ticker_tape::ticker_tape::settings_connection(fixture.pool(),domain).await.unwrap();
            let mut outcomes = Vec::new();
            for body in [json!({}),json!({"paramList":null})] {
                let (state,actor) = (fixture.state.clone(),fixture.owner.clone());
                let response = HandlerTask::spawn(async move { replace_request(state,actor,post,body).await }).join().await;
                let current = entire_settings_set(fixture.pool(),post).await;
                outcomes.push((response,current));
            }
            drop(gate);
            for (response,current) in outcomes {
                let response = document(response.unwrap()).await;
                assert_eq!(response["code"],202,"missing/null list is an explicit safety rejection, not Java parity");
                assert_eq!(response["message"],"配置不能为空");
                assert_eq!(current,before,"retain every row ID, physical order and all eight columns before obtaining a write guard");
            }
            assert_eq!(document(replace(fixture.state.clone(),fixture.owner.clone(),post,json!([])).await).await["code"],200);
            assert_eq!(entire_settings_set(fixture.pool(),post).await,json!([]),"explicit [] remains an intentional whole-set replacement");
        }).await;
    }
}

const PAGE_ID_CASES: [(&str, Option<i32>); 13] = [
    ("72038297-ba31-4a31-8a31-ba3300000001", Some(72038297)),
    ("74804abc-ba31-4a31-8a31-ba3300000002", Some(74804)),
    ("6abc1234-ba31-4a31-8a31-ba3300000003", Some(6)),
    ("abcdfabc-ba31-4a31-8a31-ba3300000004", None),
    ("12345678-be31-4a31-8a31-ba3300000005", None),
    ("12345678-ba31-4a31-8a31-ba330000000e", None),
    ("12345678-ba31-4a31-8a31-ba330000000f", Some(12345678)),
    ("12345678-ba31-4a31-8a31-ba330000000d", Some(12345678)),
    ("0123abcd-ba31-4a31-8a31-ba3300000009", Some(123)),
    ("00000000-ba31-4a31-8a31-ba3300000010", Some(0)),
    ("99999999-ba31-4a31-8a31-ba3300000011", Some(99999999)),
    ("74804ABC-BA31-4A31-8A31-BA3300000012", Some(74804)),
    ("12345678-BA31-4A31-8A31-BA330000001E", None),
];

#[test]
fn receive_page_uuid_integer_projection_keeps_hutool_prefix_and_global_e_guard() {
    for (raw, expected) in PAGE_ID_CASES {
        assert_eq!(page_id_projection(raw), expected, "{raw}");
    }
    for (raw, expected) in [
        ("42", Some(42)),
        ("42F", Some(42)),
        ("0x10", Some(16)),
        ("0x1e", None),
        ("1e2", None),
        ("2147483648", Some(i32::MIN)),
        ("-2147483649", Some(i32::MAX)),
        ("+42", Some(42)),
        ("", None),
    ] {
        assert_eq!(page_id_projection(raw), expected, "{raw}");
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn receive_existing_and_lazy_source_ids_use_the_same_integer_projection() {
    with_fixture(|fixture| async move {
        let mut current = format!("zzz-{}-0",fixture.tag);
        for (raw, expected) in PAGE_ID_CASES {
            // Fixture-only PRIMARY identity changes; source text/answers/scores stay untouched.
            sqlx::query("UPDATE t_post_ticker_tape_train_page SET id=? WHERE id=? AND train_id=?")
                .bind(raw).bind(&current).bind(&fixture.target).execute(fixture.pool()).await.unwrap();
            current = raw.to_owned();
            let page = document(find_page(State(fixture.state.clone()),Extension(fixture.owner.clone()),
                Query(PostTickerPageQuery { train_id:Some(fixture.target.clone()),page_number:Some(1) })).await).await;
            assert_eq!(page["code"],200);
            assert_eq!(page["data"]["messageBody"][0]["id"],json!(expected),"{raw}");
            assert_eq!(page["data"]["messageBody"][0]["key"],"0123");
        }
        sqlx::query("UPDATE t_post_ticker_tape_train SET total_number=203,type=1 WHERE id=?")
            .bind(&fixture.target).execute(fixture.pool()).await.unwrap();
        let first = document(invoke(fixture.state.clone(),fixture.owner.clone(),fixture.target.clone(),"page").await).await;
        let raw: Vec<String> = sqlx::query_scalar("SELECT id FROM t_post_ticker_tape_train_page WHERE train_id=? AND page_number=3 ORDER BY sort")
            .bind(&fixture.target).fetch_all(fixture.pool()).await.unwrap();
        assert_eq!(raw.len(),3);
        for (entry,raw) in first["data"]["messageBody"].as_array().unwrap().iter().zip(raw) {
            assert_eq!(entry["id"],json!(page_id_projection(&raw)),"new lazy response must project its actual stored PRIMARY ID");
        }
        let reread = document(invoke(fixture.state.clone(),fixture.owner.clone(),fixture.target.clone(),"page").await).await;
        assert_eq!(reread["data"],first["data"],"existing and freshly generated source rows cannot use different ID conversions");
    }).await;
}
