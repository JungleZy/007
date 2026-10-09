use super::*;
use crate::AppConfig;
use sqlx::{MySql, MySqlPool, Transaction};
use std::{sync::Arc, time::Duration};

const TRAIN: &str = "t_post_telegraph_key_pat_train";
const SOURCE: &str = "t_post_telegraph_key_pat_train_page";
const RAW: &str = "t_post_telegraph_key_pat_train_raw_page";
const DERIVED: &str = "t_post_telegraph_key_pat_train_page_value";
const MORE: &str = "t_post_telegraph_key_pat_train_more";
const KEY: &str = r#"["A","B","C","D"]"#;

fn rule_content() -> String {
    json!({"rateUnit":"FOUR_CHARACTER_GROUPS_PER_MINUTE","wpm":{"base":1,"r":0,"l":0},"other":{"errorCode":1,"muchLessCode":1,"muchLessLine":1,"muchLessGroups":1,"alterError":1,"bunchGroup":1,"lessGap":1}}).to_string()
}

struct Fixture {
    state: AppState,
    pool: MySqlPool,
    owner: String,
    target: String,
    unrelated: String,
    rule: String,
    cable: String,
    other_cable: String,
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
        let owner = format!("electronic-owner-{suffix}");
        let target = format!("zzz-electronic-{suffix}");
        let unrelated = format!("aaa-electronic-{suffix}");
        let rule = format!("electronic-rule-{suffix}");
        let cable = format!("zzz-cable-{suffix}");
        let other_cable = format!("aaa-cable-{suffix}");
        sqlx::query("INSERT INTO t_grading_rule (id,type,title,score,status,is_default,content) VALUES (?,3,'electronic lock regression',100,0,1,?)")
            .bind(&rule).bind(rule_content()).execute(&pool).await.unwrap();
        for id in [&target, &unrelated] {
            sqlx::query("INSERT INTO t_post_telegraph_key_pat_train (id,title,is_cable,status,total_number,create_user_id,attempt,protocol_version,message_type,rule_id,rule_content,full_score,score,begin_time,create_time) VALUES (?,'electronic lock regression',0,1,1,?,0,1,1,?,?,100,100,DATE_SUB(NOW(6),INTERVAL 300 SECOND),NOW())")
                .bind(id).bind(&owner).bind(&rule).bind(rule_content()).execute(&pool).await.unwrap();
            for table in [SOURCE, DERIVED] {
                sqlx::query(&format!("INSERT INTO {table} (id,train_id,page_number,sort,`key`,value,time) VALUES (?,?,1,0,?,'[]','[]')"))
                    .bind(id).bind(id).bind(KEY).execute(&pool).await.unwrap();
            }
            sqlx::query("INSERT INTO t_post_telegraph_key_pat_train_more (id,train_id,page_number,more_group,more_line) VALUES (?,?,1,'[]','[]')")
                .bind(id).bind(id).execute(&pool).await.unwrap();
        }
        for id in [&cable, &other_cable] {
            sqlx::query("INSERT INTO t_cable (id,title,floor_count,group_count,code_count) VALUES (?,'electronic snapshot regression',1,1,4)")
                .bind(id).execute(&pool).await.unwrap();
            sqlx::query("INSERT INTO t_cable_floor (id,cable_id,floor_number,sort,mores_key) VALUES (?,?,0,0,?)")
                .bind(id).bind(id).bind(KEY).execute(&pool).await.unwrap();
        }
        let state = AppState {
            config: Arc::new(AppConfig::from_env().unwrap()),
            pool: Some(pool.clone()),
            database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
        };
        let fixture = Self {
            state,
            pool,
            owner,
            target,
            unrelated,
            rule,
            cable,
            other_cable,
        };
        for id in [&fixture.target, &fixture.unrelated] {
            let response = document(
                finish_page(
                    State(fixture.state.clone()),
                    Extension(fixture.actor()),
                    Json(page_request(id, 1, 0, 0, 60_000)),
                )
                .await,
            )
            .await;
            assert_eq!(response["code"], 200, "{response}");
            assert_eq!(
                response["data"]["messageVO"][0]["id"],
                Value::Null,
                "submitted source ID must be cleared"
            );
            sqlx::query("UPDATE t_post_telegraph_key_pat_train_raw_page SET id=? WHERE train_id=?")
                .bind(id)
                .bind(id)
                .execute(&fixture.pool)
                .await
                .unwrap();
        }
        fixture
    }

    fn actor(&self) -> AuthUser {
        AuthUser {
            id: self.owner.clone(),
            is_admin: false,
        }
    }

    async fn cleanup(self) {
        let ids: Vec<String> = sqlx::query_scalar(
            "SELECT id FROM t_post_telegraph_key_pat_train WHERE create_user_id=? ORDER BY id",
        )
        .bind(&self.owner)
        .fetch_all(&self.pool)
        .await
        .unwrap();
        for id in ids {
            let response = document(
                delete(
                    State(self.state.clone()),
                    Extension(self.actor()),
                    Query(DeleteQuery { train_id: Some(id) }),
                )
                .await,
            )
            .await;
            assert_eq!(response["code"], 200, "{response}");
        }
        for id in [&self.cable, &self.other_cable] {
            sqlx::query("DELETE FROM t_cable_floor WHERE id=?")
                .bind(id)
                .execute(&self.pool)
                .await
                .unwrap();
            sqlx::query("DELETE FROM t_cable WHERE id=?")
                .bind(id)
                .execute(&self.pool)
                .await
                .unwrap();
        }
        sqlx::query("DELETE FROM t_grading_rule WHERE id=?")
            .bind(&self.rule)
            .execute(&self.pool)
            .await
            .unwrap();
    }
}

fn action_request(id: &str, attempt: i32) -> ActionRequest {
    ActionRequest {
        id: Some(id.to_owned()),
        protocol_version: Some(1),
        attempt: Some(attempt),
    }
}

fn page_request(id: &str, page: i32, attempt: i32, started: i64, ended: i64) -> FinishPageRequest {
    serde_json::from_value(json!({"id":id,"pageNumber":page,"attempt":attempt,"protocolVersion":1,"value":[{"id":id,"trainId":id,"pageNumber":page,"sort":0,"key":KEY,"value":KEY,"time":"[\"100\",\"100\",\"100\",\"100\"]"}],"captureIntervals":[{"startedMs":started,"endedMs":ended}]})).unwrap()
}

async fn document(response: Response) -> Value {
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    serde_json::from_slice(&bytes).unwrap()
}

async fn lock_gate(pool: &MySqlPool, table: &str, id: &str) -> (Transaction<'static, MySql>, u64) {
    let mut transaction = pool.begin().await.unwrap();
    let connection = sqlx::query_scalar("SELECT CONNECTION_ID()")
        .fetch_one(&mut *transaction)
        .await
        .unwrap();
    sqlx::query(&format!("SELECT id FROM {table} WHERE id=? FOR UPDATE"))
        .bind(id)
        .fetch_one(&mut *transaction)
        .await
        .unwrap();
    (transaction, connection)
}

async fn waiting(pool: &MySqlPool, connection: u64, table: &str, count: i64) -> bool {
    tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let waiters: i64 = sqlx::query_scalar("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME=?")
                .bind(connection).bind(table).fetch_one(pool).await.unwrap();
            if waiters >= count { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok()
}

async fn invoke(state: AppState, user: AuthUser, operation: &str, id: String) -> Response {
    match operation {
        "finish" => finish(State(state), Extension(user), Json(action_request(&id, 0))).await,
        "reset" => reset(State(state), Extension(user), Json(action_request(&id, 0))).await,
        "delete" => {
            delete(
                State(state),
                Extension(user),
                Query(DeleteQuery { train_id: Some(id) }),
            )
            .await
        }
        "finishPage" => {
            let mut request = page_request(&id, 1, 0, 0, 60_000);
            request
                .capture_intervals
                .as_mut()
                .unwrap()
                .push(CaptureInterval {
                    started_ms: 60_000,
                    ended_ms: 120_000,
                });
            finish_page(State(state), Extension(user), Json(request)).await
        }
        _ => panic!("unknown operation"),
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema"]
async fn electronic_writers_leave_other_train_children_writable() {
    for operation in ["finish", "reset", "delete", "finishPage"] {
        let fixture = Fixture::new().await;
        let blocking_table = match operation {
            "finish" => DERIVED,
            "reset" => MORE,
            "delete" => SOURCE,
            "finishPage" => RAW,
            _ => unreachable!(),
        };
        let (gate, connection) = lock_gate(&fixture.pool, blocking_table, &fixture.target).await;
        let state = fixture.state.clone();
        let actor = fixture.actor();
        let id = fixture.target.to_uppercase();
        let writer = tokio::spawn(async move { invoke(state, actor, operation, id).await });
        let reached = waiting(&fixture.pool, connection, blocking_table, 1).await;
        let mut other = fixture.pool.begin().await.unwrap();
        sqlx::query("SET innodb_lock_wait_timeout=1")
            .execute(&mut *other)
            .await
            .unwrap();
        let mut writable = true;
        for table in [TRAIN, SOURCE, RAW, DERIVED, MORE] {
            writable &= sqlx::query(&format!("UPDATE {table} SET id=id WHERE id=?"))
                .bind(&fixture.unrelated)
                .execute(&mut *other)
                .await
                .is_ok();
        }
        writable &= sqlx::query("UPDATE t_cable_floor SET id=id WHERE id=?")
            .bind(&fixture.other_cable)
            .execute(&mut *other)
            .await
            .is_ok();
        other.rollback().await.unwrap();
        gate.rollback().await.unwrap();
        let response = document(
            tokio::time::timeout(Duration::from_secs(10), writer)
                .await
                .unwrap()
                .unwrap(),
        )
        .await;
        fixture.cleanup().await;
        assert!(reached, "{operation} did not reach its exact child gate");
        assert!(writable, "{operation} locked unrelated parent/children");
        assert_eq!(response["code"], 200, "{operation}: {response}");
        if operation == "finish" {
            assert_eq!(response["data"]["score"].as_f64(), Some(100.0));
            assert_eq!(response["data"]["duration"], "60");
            assert_eq!(response["data"]["speed"], "1");
            assert_eq!(response["data"]["errorNumber"], 0);
        }
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema"]
async fn electronic_queued_finish_reads_current_parent_attempt_and_raw_pages() {
    let fixture = Fixture::new().await;
    let (mut gate, connection) = lock_gate(&fixture.pool, TRAIN, &fixture.target).await;
    let state = fixture.state.clone();
    let user = fixture.actor();
    let id = fixture.target.clone();
    let stale = tokio::spawn(async move {
        finish(State(state), Extension(user), Json(action_request(&id, 0))).await
    });
    let reached = waiting(&fixture.pool, connection, TRAIN, 1).await;
    sqlx::query("UPDATE t_post_telegraph_key_pat_train SET attempt=1 WHERE id=?")
        .bind(&fixture.target)
        .execute(&mut *gate)
        .await
        .unwrap();
    sqlx::query("UPDATE t_post_telegraph_key_pat_train_raw_page SET attempt=1 WHERE id=?")
        .bind(&fixture.target)
        .execute(&mut *gate)
        .await
        .unwrap();
    gate.commit().await.unwrap();
    let stale_response = document(stale.await.unwrap()).await;
    assert!(reached);
    assert_eq!(stale_response["code"], 208, "{stale_response}");
    let (mut gate, connection) = lock_gate(&fixture.pool, TRAIN, &fixture.target).await;
    let state = fixture.state.clone();
    let user = fixture.actor();
    let id = fixture.target.to_uppercase();
    let current = tokio::spawn(async move {
        finish(State(state), Extension(user), Json(action_request(&id, 1))).await
    });
    let reached = waiting(&fixture.pool, connection, TRAIN, 1).await;
    let mut changed = page_request(&fixture.target, 1, 1, 0, 60_000);
    changed.value.as_mut().unwrap()[0].value = Some(r#"["A","B","C","X"]"#.to_owned());
    let value = canonical_messages(&changed, &fixture.target, 1).unwrap();
    sqlx::query(
        "UPDATE t_post_telegraph_key_pat_train_raw_page SET value=?,received_at=NOW(6) WHERE id=?",
    )
    .bind(json!(value).to_string())
    .bind(&fixture.target)
    .execute(&mut *gate)
    .await
    .unwrap();
    sqlx::query("UPDATE t_grading_rule SET score=1,content=? WHERE id=?")
        .bind(rule_content().replace("\"errorCode\":1", "\"errorCode\":50"))
        .bind(&fixture.rule)
        .execute(&mut *gate)
        .await
        .unwrap();
    gate.commit().await.unwrap();
    let response = document(current.await.unwrap()).await;
    let raw: String =
        sqlx::query_scalar("SELECT value FROM t_post_telegraph_key_pat_train_raw_page WHERE id=?")
            .bind(&fixture.target)
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
    let derived: String = sqlx::query_scalar(
        "SELECT value FROM t_post_telegraph_key_pat_train_page_value WHERE train_id=? AND sort=0",
    )
    .bind(&fixture.target)
    .fetch_one(&fixture.pool)
    .await
    .unwrap();
    fixture.cleanup().await;
    assert!(reached);
    assert_eq!(response["code"], 200, "{response}");
    assert_eq!(response["data"]["attempt"], 1);
    assert_eq!(response["data"]["errorNumber"], 1);
    assert_eq!(
        response["data"]["score"].as_f64(),
        Some(99.0),
        "frozen rule, not edited live rule"
    );
    assert_eq!(response["data"]["duration"], "60");
    assert!(raw.contains('X'));
    assert!(derived.contains('X'));
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema"]
async fn electronic_queued_resets_delete_current_children_once() {
    let fixture = Fixture::new().await;
    let (mut gate, connection) = lock_gate(&fixture.pool, TRAIN, &fixture.target).await;
    let mut writers = Vec::new();
    for _ in 0..2 {
        let state = fixture.state.clone();
        let actor = fixture.actor();
        let id = fixture.target.to_uppercase();
        writers.push(tokio::spawn(async move {
            reset(State(state), Extension(actor), Json(action_request(&id, 0))).await
        }));
    }
    let reached = waiting(&fixture.pool, connection, TRAIN, 2).await;
    sqlx::query("INSERT INTO t_post_telegraph_key_pat_train_raw_page (id,train_id,page_number,attempt,value,capture_intervals,received_at) VALUES (?,?,2,0,'[]','[]',NOW(6))")
        .bind(format!("{}-late", fixture.target)).bind(&fixture.target).execute(&mut *gate).await.unwrap();
    gate.commit().await.unwrap();
    let mut codes = Vec::new();
    for writer in writers {
        codes.push(
            document(writer.await.unwrap()).await["code"]
                .as_i64()
                .unwrap(),
        );
    }
    codes.sort();
    let row = sqlx::query("SELECT attempt,status FROM t_post_telegraph_key_pat_train WHERE id=?")
        .bind(&fixture.target)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
    for table in [RAW, DERIVED, MORE] {
        let remaining: i64 =
            sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {table} WHERE train_id=?"))
                .bind(&fixture.target)
                .fetch_one(&fixture.pool)
                .await
                .unwrap();
        assert_eq!(remaining, 0, "reset did not delete current {table} rows");
    }
    let sources: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM t_post_telegraph_key_pat_train_page WHERE train_id=?",
    )
    .bind(&fixture.target)
    .fetch_one(&fixture.pool)
    .await
    .unwrap();
    fixture.cleanup().await;
    assert!(reached);
    assert_eq!(codes, [200, 208]);
    assert_eq!(row.get::<i32, _>("attempt"), 1);
    assert_eq!(row.get::<Option<i32>, _>("status"), Some(0));
    assert_eq!(sources, 1, "reset preserves frozen source");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema"]
async fn electronic_fixed_cable_snapshot_sees_replacement_after_rule_queue() {
    let fixture = Fixture::new().await;
    let (gate, connection) = lock_gate(&fixture.pool, "t_grading_rule", &fixture.rule).await;
    let state = fixture.state.clone();
    let actor = fixture.actor();
    let request = serde_json::from_value(json!({"title":"queued cable","isCable":1,"cableId":fixture.cable.to_uppercase(),"startPage":1,"totalNumber":100,"messageType":1,"ruleId":fixture.rule})).unwrap();
    let writer =
        tokio::spawn(async move { add(State(state), Extension(actor), Json(request)).await });
    let reached = waiting(&fixture.pool, connection, "t_grading_rule", 1).await;
    let mut replacement = fixture.pool.begin().await.unwrap();
    sqlx::query("SELECT id FROM t_cable WHERE id=? FOR UPDATE")
        .bind(&fixture.cable)
        .fetch_one(&mut *replacement)
        .await
        .unwrap();
    sqlx::query("UPDATE t_cable_floor SET mores_key='[\"E\",\"F\",\"G\",\"H\"]' WHERE id=?")
        .bind(&fixture.cable)
        .execute(&mut *replacement)
        .await
        .unwrap();
    replacement.commit().await.unwrap();
    // Keep an uncommitted unrelated floor write while the actual add reads the table.
    let (other_gate, _) = lock_gate(&fixture.pool, "t_cable_floor", &fixture.other_cable).await;
    gate.rollback().await.unwrap();
    let result = tokio::time::timeout(Duration::from_secs(5), writer).await;
    other_gate.rollback().await.unwrap();
    let response = document(
        result
            .expect("add must not wait on unrelated cable floors")
            .unwrap(),
    )
    .await;
    let id = response["data"]["id"].as_str().unwrap();
    let key: String = sqlx::query_scalar(
        "SELECT `key` FROM t_post_telegraph_key_pat_train_page WHERE train_id=?",
    )
    .bind(id)
    .fetch_one(&fixture.pool)
    .await
    .unwrap();
    fixture.cleanup().await;
    assert!(reached);
    assert_eq!(response["code"], 200, "{response}");
    assert_eq!(key, r#"["E","F","G","H"]"#);
}

async fn fail_trigger(fixture: &Fixture, table: &str, event: &str, train_id: &str) -> String {
    let name = format!("electronic_fail_{}", uuid::Uuid::new_v4().simple());
    let row = if event == "DELETE" { "OLD" } else { "NEW" };
    // IDs come only from this isolated fixture, not from HTTP inputs.
    sqlx::raw_sql(&format!("CREATE TRIGGER {name} BEFORE {event} ON {table} FOR EACH ROW BEGIN IF {row}.train_id='{train_id}' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='electronic child failure regression'; END IF; END"))
        .execute(&fixture.pool).await.unwrap();
    name
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and CREATE TRIGGER permission"]
async fn electronic_child_failures_roll_back_settlement_reset_and_delete() {
    for (operation, table, event) in [
        ("finish", DERIVED, "INSERT"),
        ("reset", MORE, "DELETE"),
        ("delete", SOURCE, "DELETE"),
    ] {
        let fixture = Fixture::new().await;
        let before: String = sqlx::query_scalar(
            "SELECT value FROM t_post_telegraph_key_pat_train_raw_page WHERE id=?",
        )
        .bind(&fixture.target)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
        let trigger = fail_trigger(&fixture, table, event, &fixture.target).await;
        let response = document(
            invoke(
                fixture.state.clone(),
                fixture.actor(),
                operation,
                fixture.target.clone(),
            )
            .await,
        )
        .await;
        sqlx::raw_sql(&format!("DROP TRIGGER {trigger}"))
            .execute(&fixture.pool)
            .await
            .unwrap();
        let row = sqlx::query(
            "SELECT status,attempt,score FROM t_post_telegraph_key_pat_train WHERE id=?",
        )
        .bind(&fixture.target)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
        for child in [SOURCE, RAW, DERIVED, MORE] {
            let count: i64 =
                sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {child} WHERE id=?"))
                    .bind(&fixture.target)
                    .fetch_one(&fixture.pool)
                    .await
                    .unwrap();
            assert_eq!(count, 1, "{operation} lost acknowledged {child}");
        }
        let after: String = sqlx::query_scalar(
            "SELECT value FROM t_post_telegraph_key_pat_train_raw_page WHERE id=?",
        )
        .bind(&fixture.target)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
        fixture.cleanup().await;
        assert_eq!(response["code"], 500, "{operation}: {response}");
        assert_eq!(row.get::<Option<i32>, _>("status"), Some(1));
        assert_eq!(row.get::<i32, _>("attempt"), 0);
        assert_eq!(
            row.get::<Option<Decimal>, _>("score"),
            Some(Decimal::from(100))
        );
        assert_eq!(before, after);
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and CREATE TRIGGER permission"]
async fn electronic_lazy_source_and_raw_page_commit_or_roll_back_together() {
    let fixture = Fixture::new().await;
    sqlx::query("UPDATE t_post_telegraph_key_pat_train SET total_number=201 WHERE id=?")
        .bind(&fixture.target)
        .execute(&fixture.pool)
        .await
        .unwrap();
    for failing_table in [SOURCE, RAW] {
        let trigger = fail_trigger(&fixture, failing_table, "INSERT", &fixture.target).await;
        let response = document(
            finish_page(
                State(fixture.state.clone()),
                Extension(fixture.actor()),
                Json(page_request(&fixture.target, 3, 0, 60_000, 120_000)),
            )
            .await,
        )
        .await;
        sqlx::raw_sql(&format!("DROP TRIGGER {trigger}"))
            .execute(&fixture.pool)
            .await
            .unwrap();
        let source_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_post_telegraph_key_pat_train_page WHERE train_id=? AND page_number=3")
            .bind(&fixture.target).fetch_one(&fixture.pool).await.unwrap();
        let raw_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_post_telegraph_key_pat_train_raw_page WHERE train_id=? AND page_number=3")
            .bind(&fixture.target).fetch_one(&fixture.pool).await.unwrap();
        assert_eq!(response["code"], 500, "{failing_table}: {response}");
        assert_eq!(
            source_count, 0,
            "source generation must roll back when raw persistence fails"
        );
        assert_eq!(
            raw_count, 0,
            "raw persistence must not precede successful source generation"
        );
    }
    let response = document(
        finish_page(
            State(fixture.state.clone()),
            Extension(fixture.actor()),
            Json(page_request(
                &fixture.target.to_uppercase(),
                3,
                0,
                60_000,
                120_000,
            )),
        )
        .await,
    )
    .await;
    let source_count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_post_telegraph_key_pat_train_page WHERE train_id=? AND page_number=3")
        .bind(&fixture.target).fetch_one(&fixture.pool).await.unwrap();
    let canonical: String = sqlx::query_scalar("SELECT train_id FROM t_post_telegraph_key_pat_train_raw_page WHERE train_id=? AND page_number=3")
        .bind(&fixture.target).fetch_one(&fixture.pool).await.unwrap();
    assert_eq!(canonical, fixture.target);
    fixture.cleanup().await;
    assert_eq!(response["code"], 200, "{response}");
    assert_eq!(source_count, 1);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn electronic_lifecycle_keeps_final_score_reset_source_and_delete_children() {
    let fixture = Fixture::new().await;
    sqlx::query("UPDATE t_post_telegraph_key_pat_train SET is_cable=1,total_number=100 WHERE id=?")
        .bind(&fixture.target)
        .execute(&fixture.pool)
        .await
        .unwrap();
    let final_response = document(
        finish(
            State(fixture.state.clone()),
            Extension(fixture.actor()),
            Json(action_request(&fixture.target, 0)),
        )
        .await,
    )
    .await;
    assert_eq!(final_response["code"], 200, "{final_response}");
    assert_eq!(final_response["data"]["score"].as_f64(), Some(100.0));
    let detail = document(
        post_telegraph::detail(
            State(fixture.state.clone()),
            Extension(fixture.actor()),
            Json(post_telegraph::IdRequest {
                id: Some(fixture.target.clone()),
            }),
        )
        .await,
    )
    .await;
    assert_eq!(detail["code"], 200, "{detail}");
    assert_eq!(
        detail["data"]["totalNumber"], 1,
        "fixed training reports copied source count, not requested groups"
    );
    assert_eq!(detail["data"]["pageNumber"], 1);
    assert_eq!(detail["data"]["moreGroupLine"], Value::Null);
    assert_eq!(
        detail["data"]["pageAnalyzeVOS"],
        json!([{"pageNumber":1,"patNumber":4,"totalTime":60000}])
    );
    assert_eq!(
        detail["data"]["savedPages"][0]["messageVO"][0]["id"],
        Value::Null
    );
    assert_eq!(
        detail["data"]["savedPages"][0]["resolverMessage"],
        Value::Null
    );
    let replay = document(
        finish_page(
            State(fixture.state.clone()),
            Extension(fixture.actor()),
            Json(page_request(&fixture.target, 1, 0, 0, 60_000)),
        )
        .await,
    )
    .await;
    assert_eq!(
        replay["code"], 200,
        "confirmed identical pages may replay after finish"
    );
    let mut changed = page_request(&fixture.target, 1, 0, 0, 60_000);
    changed.value.as_mut().unwrap()[0].value = Some(r#"["A","B","C","X"]"#.to_owned());
    let rejected = document(
        finish_page(
            State(fixture.state.clone()),
            Extension(fixture.actor()),
            Json(changed),
        )
        .await,
    )
    .await;
    assert_eq!(rejected["code"], 202, "{rejected}");
    let persisted: Option<Decimal> =
        sqlx::query_scalar("SELECT score FROM t_post_telegraph_key_pat_train WHERE id=?")
            .bind(&fixture.target)
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
    assert_eq!(persisted, Some(Decimal::from(100)));
    let foreign = AuthUser {
        id: format!("foreign-{}", fixture.owner),
        is_admin: true,
    };
    let denied = document(
        reset(
            State(fixture.state.clone()),
            Extension(foreign),
            Json(action_request(&fixture.target, 0)),
        )
        .await,
    )
    .await;
    assert_eq!(
        denied["code"], 207,
        "administrator is not owner in this personal domain"
    );
    let reset_response = document(
        reset(
            State(fixture.state.clone()),
            Extension(fixture.actor()),
            Json(action_request(&fixture.target.to_uppercase(), 0)),
        )
        .await,
    )
    .await;
    assert_eq!(reset_response["code"], 200, "{reset_response}");
    assert_eq!(reset_response["data"]["attempt"], 1);
    assert_eq!(reset_response["data"]["score"].as_f64(), Some(100.0));
    let stale = document(
        finish_page(
            State(fixture.state.clone()),
            Extension(fixture.actor()),
            Json(page_request(&fixture.target, 1, 0, 0, 60_000)),
        )
        .await,
    )
    .await;
    assert_eq!(stale["code"], 208, "{stale}");
    let remaining_sources: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM t_post_telegraph_key_pat_train_page WHERE train_id=?",
    )
    .bind(&fixture.target)
    .fetch_one(&fixture.pool)
    .await
    .unwrap();
    assert_eq!(remaining_sources, 1);
    for table in [RAW, DERIVED, MORE] {
        let count: i64 =
            sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {table} WHERE train_id=?"))
                .bind(&fixture.target)
                .fetch_one(&fixture.pool)
                .await
                .unwrap();
        assert_eq!(count, 0, "reset must clear {table}");
    }
    let deleted = document(
        delete(
            State(fixture.state.clone()),
            Extension(fixture.actor()),
            Query(DeleteQuery {
                train_id: Some(fixture.target.to_uppercase()),
            }),
        )
        .await,
    )
    .await;
    assert_eq!(deleted["code"], 200, "{deleted}");
    assert_eq!(deleted["data"], true);
    for table in [TRAIN, SOURCE, RAW, DERIVED, MORE] {
        let column = if table == TRAIN { "id" } else { "train_id" };
        let count: i64 =
            sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {table} WHERE {column}=?"))
                .bind(&fixture.target)
                .fetch_one(&fixture.pool)
                .await
                .unwrap();
        assert_eq!(count, 0, "delete must remove {table}");
    }
    fixture.cleanup().await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn electronic_existing_java_uuid_aliases_reject_foreign_captures_and_settle() {
    let mut fixture = Fixture::new().await;
    let canonical_id = uuid::Uuid::new_v4().to_string();
    let previous_id = &fixture.target;
    let mut transaction = fixture.pool.begin().await.unwrap();
    sqlx::query("UPDATE t_post_telegraph_key_pat_train SET id=? WHERE id=?")
        .bind(&canonical_id)
        .bind(previous_id)
        .execute(&mut *transaction)
        .await
        .unwrap();
    for table in [SOURCE, RAW, DERIVED, MORE] {
        sqlx::query(&format!("UPDATE {table} SET train_id=? WHERE id=?"))
            .bind(&canonical_id)
            .bind(previous_id)
            .execute(&mut *transaction)
            .await
            .unwrap();
    }
    transaction.commit().await.unwrap();
    fixture.target = canonical_id;

    let mut stored = json!([{"id":null,"trainId":fixture.unrelated,"pageNumber":1,"sort":0,"key":KEY,"value":KEY,"time":"[\"100\",\"100\",\"100\",\"100\"]"}]);
    let raw_id: String = sqlx::query_scalar(
        "SELECT id FROM t_post_telegraph_key_pat_train_raw_page WHERE train_id=? AND page_number=1",
    )
    .bind(&fixture.target)
    .fetch_one(&fixture.pool)
    .await
    .unwrap();
    sqlx::query("UPDATE t_post_telegraph_key_pat_train_raw_page SET value=? WHERE id=?")
        .bind(stored.to_string())
        .bind(&raw_id)
        .execute(&fixture.pool)
        .await
        .unwrap();
    let foreign_replay = document(
        finish_page(
            State(fixture.state.clone()),
            Extension(fixture.actor()),
            Json(page_request(&fixture.target, 1, 0, 0, 60_000)),
        )
        .await,
    )
    .await;
    assert_eq!(
        foreign_replay["code"], 202,
        "replay must not accept an existing capture from another parent: {foreign_replay}"
    );
    let rejected = document(
        finish(
            State(fixture.state.clone()),
            Extension(fixture.actor()),
            Json(action_request(&fixture.target, 0)),
        )
        .await,
    )
    .await;
    assert_eq!(
        rejected["code"], 202,
        "distinct-parent raw capture must not settle: {rejected}"
    );
    let status: Option<i32> =
        sqlx::query_scalar("SELECT status FROM t_post_telegraph_key_pat_train WHERE id=?")
            .bind(&fixture.target)
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
    assert_eq!(status, Some(1));
    let derived: Option<String> = sqlx::query_scalar(
        "SELECT value FROM t_post_telegraph_key_pat_train_page_value WHERE train_id=? AND sort=0",
    )
    .bind(&fixture.target)
    .fetch_one(&fixture.pool)
    .await
    .unwrap();
    assert_eq!(
        derived.as_deref(),
        Some("[]"),
        "rejected foreign capture must retain acknowledged derived state"
    );

    stored[0]["trainId"] = json!(fixture.target.to_uppercase());
    let raw_before = stored.to_string();
    sqlx::query("UPDATE t_post_telegraph_key_pat_train_raw_page SET value=? WHERE id=?")
        .bind(&raw_before)
        .bind(&raw_id)
        .execute(&fixture.pool)
        .await
        .unwrap();
    let snapshot_sql = "SELECT id,value,capture_intervals,attempt,DATE_FORMAT(received_at,'%Y-%m-%d %H:%i:%s.%f') FROM t_post_telegraph_key_pat_train_raw_page WHERE id=?";
    let before: (String, String, String, i32, String) = sqlx::query_as(snapshot_sql)
        .bind(&raw_id)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
    for request_id in [&fixture.target, &fixture.target.to_uppercase()] {
        let replay = document(
            finish_page(
                State(fixture.state.clone()),
                Extension(fixture.actor()),
                Json(page_request(request_id, 1, 0, 0, 60_000)),
            )
            .await,
        )
        .await;
        assert_eq!(
            replay["code"], 200,
            "identical Java raw alias retry must succeed: {replay}"
        );
        assert_eq!(
            replay["data"]["messageVO"][0]["trainId"],
            fixture.target.to_uppercase(),
            "replay must return the immutable saved alias"
        );
        let after: (String, String, String, i32, String) = sqlx::query_as(snapshot_sql)
            .bind(&raw_id)
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
        assert_eq!(
            after, before,
            "retry must not update raw identity, content, timeline, attempt or receipt time"
        );
    }
    let mut changed = page_request(&fixture.target, 1, 0, 0, 60_000);
    changed.value.as_mut().unwrap()[0].value = Some(r#"["A","B","C","X"]"#.to_owned());
    let changed_replay = document(
        finish_page(
            State(fixture.state.clone()),
            Extension(fixture.actor()),
            Json(changed),
        )
        .await,
    )
    .await;
    assert_eq!(
        changed_replay["code"], 202,
        "alias equivalence must not erase non-ID content differences: {changed_replay}"
    );
    let after: (String, String, String, i32, String) = sqlx::query_as(snapshot_sql)
        .bind(&raw_id)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
    assert_eq!(
        after, before,
        "conflicting retry must not change the saved raw page"
    );
    let settled = document(
        finish(
            State(fixture.state.clone()),
            Extension(fixture.actor()),
            Json(action_request(&fixture.target.to_uppercase(), 0)),
        )
        .await,
    )
    .await;
    let raw_after: String =
        sqlx::query_scalar("SELECT value FROM t_post_telegraph_key_pat_train_raw_page WHERE id=?")
            .bind(&raw_id)
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
    let derived_parent: String = sqlx::query_scalar("SELECT train_id FROM t_post_telegraph_key_pat_train_page_value WHERE train_id=? AND sort=0")
        .bind(&fixture.target).fetch_one(&fixture.pool).await.unwrap();
    assert_eq!(
        settled["code"], 200,
        "existing Java UUID alias must settle: {settled}"
    );
    assert_eq!(settled["data"]["score"].as_f64(), Some(100.0));
    assert_eq!(settled["data"]["duration"], "60");
    assert_eq!(settled["data"]["speed"], "1");
    assert_eq!(settled["data"]["errorNumber"], 0);
    assert_eq!(
        raw_before, raw_after,
        "immutable Java capture must not be rewritten to canonicalize embedded IDs"
    );
    assert_eq!(
        derived_parent, fixture.target,
        "new derived rows use authoritative canonical parent"
    );
    fixture.cleanup().await;
}
