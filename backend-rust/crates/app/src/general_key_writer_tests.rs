//! Isolated-DB regressions for the electronic-key (generalKeyPat) training writers and readers.
//!
//! The child tables (members, saved pages, typed values, "more" and resolver rows) are indexed by
//! primary key only, so a locking read, UPDATE or DELETE filtered by `train_id`/`user_id`
//! next-key locks every row it scans, including other trainings' rows. Every writer here must
//! lock the training row by primary key, read child ids plainly and write by primary key. Each
//! test keeps a second, earlier training's rows X-locked (so any scan from the start of the
//! clustered index collides with them, after a 2 s lock wait timeout) while the operation works
//! on the later training. Read-only endpoints (export, findPage) must take no row lock at all.
use super::*;
use crate::AppConfig;
use sqlx::{MySql, MySqlPool, Transaction};
use std::sync::Arc;
use std::time::Duration;

const RULE: &str = r#"{"content":"{\"rateUnit\":\"FOUR_CHARACTER_GROUPS_PER_MINUTE\",\"wpm\":{\"base\":100,\"r\":0,\"l\":0},\"other\":{\"errorCode\":1,\"muchLessCode\":1,\"muchLessLine\":1,\"muchLessGroups\":1,\"alterError\":1,\"bunchGroup\":1,\"lessGap\":1}}","id":"rule","score":100}"#;

const CHILDREN: [&str; 5] = [
    "general_key_pat_user",
    "general_key_pat_page",
    "general_key_pat_user_value",
    "general_key_pat_train_more",
    "general_key_pat_user_value_resolver",
];

#[derive(Clone, Copy, PartialEq)]
enum Phase {
    Idle,
    Running,
    ClosingOpen,
    ClosingExpired,
}

struct Fixture {
    state: AppState,
    pool: MySqlPool,
    tag: String,
    /// The earlier, unrelated training whose rows stay locked.
    unrelated: i64,
    /// The training the writer works on.
    target: i64,
}

fn captured(page: u32) -> String {
    json!({page.to_string(): {"attempt": 0, "intervals": [{"startedMs": 0, "endedMs": 1000}], "receivedAt": 1}})
        .to_string()
}

impl Fixture {
    fn student(&self, number: u8) -> String {
        format!("zzz-student{number}-{}", self.tag)
    }

    fn organizer(&self) -> String {
        format!("zzz-organizer-{}", self.tag)
    }

    async fn new(phase: Phase, with_pages: bool, total_number: i64) -> Self {
        let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("scratch database URL");
        assert!(url.ends_with("/rust_scratch_lockdemo"));
        let pool = sqlx::mysql::MySqlPoolOptions::new()
            .max_connections(8)
            .after_connect(|connection, _| {
                Box::pin(async move {
                    sqlx::query("SET SESSION innodb_lock_wait_timeout=2")
                        .execute(&mut *connection)
                        .await?;
                    Ok(())
                })
            })
            .connect(&url)
            .await
            .unwrap();
        let tag = uuid::Uuid::new_v4().simple().to_string()[..12].to_owned();
        let state = AppState {
            config: Arc::new(AppConfig::from_env().unwrap()),
            pool: Some(pool.clone()),
            database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
        };
        let mut fixture = Self {
            state,
            pool,
            tag,
            unrelated: 0,
            target: 0,
        };
        // Created first, so every row of the unrelated training sorts before the target's rows.
        fixture.unrelated = fixture
            .training("aaa", phase, with_pages, total_number)
            .await;
        fixture.target = fixture
            .training("zzz", phase, with_pages, total_number)
            .await;
        fixture
    }

    async fn training(&self, side: &str, phase: Phase, with_pages: bool, total_number: i64) -> i64 {
        let (status, start, end) = match phase {
            Phase::Idle => (0, "NULL", "NULL"),
            Phase::Running => (1, "DATE_SUB(NOW(6),INTERVAL 1 MINUTE)", "NULL"),
            Phase::ClosingOpen => (3, "DATE_SUB(NOW(6),INTERVAL 1 MINUTE)", "NOW(6)"),
            Phase::ClosingExpired => (
                3,
                "DATE_SUB(NOW(6),INTERVAL 5 MINUTE)",
                "DATE_SUB(NOW(6),INTERVAL 3 MINUTE)",
            ),
        };
        let sql = format!("INSERT INTO general_key_pat (title,is_cable,create_time,create_user,is_average,is_random,message_type,rule_content,rule_id,start_time,end_time,status,total_number,train_type,valid_time,protocol_version) VALUES (?,0,NOW(),?,0,1,0,?,'rule',{start},{end},{status},?,0,0,1)");
        let train = sqlx::query(&sql)
            .bind(format!("key lock regression {side}-{}", self.tag))
            .bind(format!("{side}-organizer-{}", self.tag))
            .bind(RULE)
            .bind(total_number)
            .execute(&self.pool)
            .await
            .unwrap()
            .last_insert_id() as i64;
        let members: Vec<(String, i32)> = if side == "zzz" {
            vec![
                (self.student(1), 0),
                (self.student(2), 0),
                (self.organizer(), 1),
            ]
        } else {
            vec![
                (format!("aaa-student-{}", self.tag), 0),
                (format!("aaa-organizer-{}", self.tag), 1),
            ]
        };
        let started = if phase == Phase::Idle {
            "NULL"
        } else {
            "'2020-01-01 00:00:00'"
        };
        for (index, (user, role)) in members.iter().enumerate() {
            let capture = if *role == 0 { started } else { "NULL" };
            let pages = if with_pages && *role == 0 && index == 0 {
                captured(1)
            } else {
                "{}".to_owned()
            };
            sqlx::query(&format!("INSERT INTO general_key_pat_user (train_id,user_id,role,attempt,is_finish,score,accuracy,error_number,create_time,capture_started_at,capture_pages) VALUES (?,?,?,0,0,0,'0.00',0,NOW(),{capture},?)"))
                .bind(train)
                .bind(user)
                .bind(role)
                .bind(pages)
                .execute(&self.pool)
                .await
                .unwrap();
        }
        if with_pages {
            for sort in 0..3 {
                sqlx::query("INSERT INTO general_key_pat_page (id,train_id,page_number,sort,`key`,`value`,`time`) VALUES (?,?,1,?,?,'[]','[]')")
                    .bind(format!("{side}-page-{sort}-{}", self.tag))
                    .bind(train)
                    .bind(sort)
                    .bind(json!(["E", "E", "E", "E"]).to_string())
                    .execute(&self.pool)
                    .await
                    .unwrap();
            }
            let owner = if side == "zzz" {
                self.student(1)
            } else {
                format!("aaa-student-{}", self.tag)
            };
            sqlx::query("INSERT INTO general_key_pat_user_value (train_id,user_id,page_number,sort,`key`,`value`,`time`) VALUES (?,?,1,0,?,?,'[1,1,1,1]')")
                .bind(train)
                .bind(&owner)
                .bind(json!(["E", "E", "E", "E"]).to_string())
                .bind(json!(["E", "E", "E", "E"]).to_string())
                .execute(&self.pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO general_key_pat_train_more (train_id,user_id,more_group,more_line,page_number) VALUES (?,?,'[]','[]',1)")
                .bind(train)
                .bind(&owner)
                .execute(&self.pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO general_key_pat_user_value_resolver (train_id,user_id,page_number,sort,`key`,`value`,`time`) VALUES (?,?,1,0,'[]','[]','[]')")
                .bind(train)
                .bind(&owner)
                .execute(&self.pool)
                .await
                .unwrap();
        }
        train
    }

    /// X-locks, by primary key, the training row and every child row of the unrelated training.
    async fn hold_unrelated(&self) -> Transaction<'static, MySql> {
        let mut hold = self.pool.begin().await.unwrap();
        sqlx::query("SELECT id FROM general_key_pat WHERE id=? FOR UPDATE")
            .bind(self.unrelated)
            .fetch_one(&mut *hold)
            .await
            .unwrap();
        for table in CHILDREN {
            let ids: Vec<String> = sqlx::query_scalar(&format!(
                "SELECT CAST(id AS CHAR) FROM {table} WHERE train_id=? ORDER BY id"
            ))
            .bind(self.unrelated)
            .fetch_all(&self.pool)
            .await
            .unwrap();
            for id in ids {
                sqlx::query(&format!("SELECT id FROM {table} WHERE id=? FOR UPDATE"))
                    .bind(id)
                    .fetch_one(&mut *hold)
                    .await
                    .unwrap();
            }
        }
        hold
    }

    fn user(&self, id: String) -> AuthUser {
        AuthUser {
            id,
            is_admin: false,
        }
    }

    async fn count(&self, table: &str, train: i64) -> i64 {
        sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {table} WHERE train_id=?"))
            .bind(train)
            .fetch_one(&self.pool)
            .await
            .unwrap()
    }

    async fn member_column(&self, user: &str, column: &str) -> Option<String> {
        sqlx::query_scalar(&format!(
            "SELECT CAST({column} AS CHAR) FROM general_key_pat_user WHERE train_id=? AND user_id=?"
        ))
        .bind(self.target)
        .bind(user)
        .fetch_one(&self.pool)
        .await
        .unwrap()
    }

    async fn train_status(&self) -> Option<i32> {
        sqlx::query_scalar("SELECT status FROM general_key_pat WHERE id=?")
            .bind(self.target)
            .fetch_one(&self.pool)
            .await
            .unwrap()
    }

    async fn cleanup(self) {
        for train in [self.unrelated, self.target] {
            for table in CHILDREN {
                let ids: Vec<String> = sqlx::query_scalar(&format!(
                    "SELECT CAST(id AS CHAR) FROM {table} WHERE train_id=? ORDER BY id"
                ))
                .bind(train)
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
            sqlx::query("DELETE FROM general_key_pat WHERE id=?")
                .bind(train)
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

/// Runs `operation` against the target training while the unrelated training stays locked, and
/// returns the response document. The caller asserts the effect; cleanup always happens.
async fn locked_beside<F, Fut>(fixture: &Fixture, operation: F) -> Value
where
    F: FnOnce() -> Fut,
    Fut: std::future::Future<Output = Response>,
{
    let hold = fixture.hold_unrelated().await;
    let result = tokio::time::timeout(Duration::from_secs(30), operation()).await;
    hold.rollback().await.unwrap();
    document(result.expect("writer did not finish")).await
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn the_unrelated_training_lock_really_blocks_a_member_table_scan() {
    // Canary: a locking read filtered by user id scans the whole member table and so collides
    // with the held unrelated rows. Without this the other tests could pass vacuously.
    let fixture = Fixture::new(Phase::Running, false, 100).await;
    let hold = fixture.hold_unrelated().await;
    let scan = sqlx::query("SELECT id FROM general_key_pat_user WHERE user_id=? FOR UPDATE")
        .bind(fixture.student(1))
        .fetch_all(&fixture.pool)
        .await;
    hold.rollback().await.unwrap();
    fixture.cleanup().await;
    assert!(
        scan.is_err(),
        "the member table scan must wait on the held rows"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn start_locks_only_its_own_member() {
    let fixture = Fixture::new(Phase::Running, false, 100).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target,
    );
    let result = locked_beside(&fixture, || {
        participant_action(
            state,
            user,
            json!({"trainId": train.to_string(), "attempt": 0}),
            Domain::Key,
            false,
        )
    })
    .await;
    let started = fixture
        .member_column(&fixture.student(1), "is_finish")
        .await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(started.as_deref(), Some("2"));
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn reset_clears_its_own_values_more_and_resolver_rows_only() {
    let fixture = Fixture::new(Phase::Running, true, 100).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target,
    );
    let result = locked_beside(&fixture, || {
        participant_action(
            state,
            user,
            json!({"trainId": train.to_string(), "attempt": 0}),
            Domain::Key,
            true,
        )
    })
    .await;
    let attempt = fixture.member_column(&fixture.student(1), "attempt").await;
    let mut own = 0;
    let mut unrelated = 0;
    for table in &CHILDREN[2..] {
        own += fixture.count(table, fixture.target).await;
        unrelated += fixture.count(table, fixture.unrelated).await;
    }
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(attempt.as_deref(), Some("1"));
    assert_eq!(own, 0, "value, more and resolver rows are deleted");
    assert_eq!(unrelated, 3, "the unrelated training keeps its rows");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn upload_replaces_its_page_without_scanning_other_trainings() {
    let fixture = Fixture::new(Phase::Running, true, 100).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target,
    );
    let body = json!({"trainId": train.to_string(), "pageNumber": 1, "attempt": 0,
        "pageValue": [{"sort": 0, "key": json!(["E","E","E","E"]).to_string(), "value": json!(["E","E","E","E"]).to_string(), "time": "[1,1,1,1]"}],
        "captureIntervals": [{"startedMs":0,"endedMs":1000},{"startedMs":2000,"endedMs":3000}]});
    let result = locked_beside(&fixture, || upload(state, user, body, Domain::Key)).await;
    let rows = fixture
        .count("general_key_pat_user_value", fixture.target)
        .await;
    let pages = fixture
        .member_column(&fixture.student(1), "capture_pages")
        .await
        .unwrap();
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(rows, 1, "the old page row is replaced, not duplicated");
    assert!(
        pages.contains("2000"),
        "the extended timeline is stored: {pages}"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn upload_locks_the_member_before_it_touches_the_values() {
    // Lock order is training -> member -> values. Hold the member row from outside: the upload
    // must stop at the member lock without having X-locked (deleted) the page's value row yet.
    // With the old order (values first) the NOWAIT probe below fails on the deleted row.
    let fixture = Fixture::new(Phase::Running, true, 100).await;
    let mut hold = fixture.pool.begin().await.unwrap();
    sqlx::query("SELECT id FROM general_key_pat_user WHERE train_id=? AND user_id=? FOR UPDATE")
        .bind(fixture.target)
        .bind(fixture.student(1))
        .fetch_one(&mut *hold)
        .await
        .unwrap();
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target,
    );
    let body = json!({"trainId": train.to_string(), "pageNumber": 1, "attempt": 0,
        "pageValue": [{"sort": 0, "key": json!(["E","E","E","E"]).to_string(), "value": json!(["E","E","E","E"]).to_string(), "time": "[1,1,1,1]"}],
        "captureIntervals": [{"startedMs":0,"endedMs":1000},{"startedMs":2000,"endedMs":3000}]});
    let running = tokio::spawn(async move { upload(state, user, body, Domain::Key).await });
    tokio::time::sleep(Duration::from_millis(600)).await;
    let value_id: String = sqlx::query_scalar(
        "SELECT CAST(id AS CHAR) FROM general_key_pat_user_value WHERE train_id=? ORDER BY id LIMIT 1",
    )
    .bind(fixture.target)
    .fetch_one(&fixture.pool)
    .await
    .unwrap();
    let probe =
        sqlx::query("SELECT id FROM general_key_pat_user_value WHERE id=? FOR UPDATE NOWAIT")
            .bind(&value_id)
            .fetch_optional(&fixture.pool)
            .await;
    hold.rollback().await.unwrap();
    let result = document(running.await.unwrap()).await;
    fixture.cleanup().await;
    assert!(
        probe.is_ok(),
        "the value row must still be free while the upload waits for the member lock: {probe:?}"
    );
    assert_eq!(result["code"], 200, "{result}");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn finish_settles_by_primary_key_and_never_generates_missing_pages() {
    let fixture = Fixture::new(Phase::Running, true, 250).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target,
    );
    let result = locked_beside(&fixture, || {
        finish_participant(
            state,
            user,
            json!({"trainId": train.to_string(), "attempt": 0}),
            Domain::Key,
        )
    })
    .await;
    let finished = fixture
        .member_column(&fixture.student(1), "is_finish")
        .await;
    let other = fixture
        .member_column(&fixture.student(2), "is_finish")
        .await;
    let pages = fixture.count("general_key_pat_page", fixture.target).await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(finished.as_deref(), Some("1"));
    assert_eq!(
        other.as_deref(),
        Some("0"),
        "only the caller settles in the grace window"
    );
    assert_eq!(
        pages, 3,
        "Java settles with the pages it has; none are generated"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn missing_pages_are_charged_twice_exactly_like_java() {
    // 250 groups, nothing captured: Java's loop adds 10+10+5 lack lines and the closed formula
    // adds 2*100+50 groups and 2*10+5-1 lines, so 49 lack lines and 250 lack groups.
    let fixture = Fixture::new(Phase::Running, false, 250).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target,
    );
    let result = finish_participant(
        state,
        user,
        json!({"trainId": train.to_string(), "attempt": 0}),
        Domain::Key,
    )
    .await;
    let result = document(result).await;
    let deduct = fixture
        .member_column(&fixture.student(1), "deduct_info")
        .await
        .unwrap();
    let pages = fixture.count("general_key_pat_page", fixture.target).await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    let deduct: Value = serde_json::from_str(&deduct).unwrap();
    assert_eq!(deduct["lackLineNumber"], 49, "{deduct}");
    assert_eq!(deduct["lackGroupNumber"], 250, "{deduct}");
    assert_eq!(deduct["lackLineScore"], "-49", "{deduct}");
    assert_eq!(pages, 0, "no page is generated by settlement");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn finish_after_the_grace_window_settles_every_student() {
    let fixture = Fixture::new(Phase::ClosingExpired, true, 100).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target,
    );
    let result = locked_beside(&fixture, || {
        finish_participant(
            state,
            user,
            json!({"trainId": train.to_string(), "attempt": 0}),
            Domain::Key,
        )
    })
    .await;
    let first = fixture
        .member_column(&fixture.student(1), "is_finish")
        .await;
    let second = fixture
        .member_column(&fixture.student(2), "is_finish")
        .await;
    let status = fixture.train_status().await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(
        (first.as_deref(), second.as_deref()),
        (Some("1"), Some("1"))
    );
    assert_eq!(status, Some(2));
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn recovery_settles_an_expired_closing_training_by_primary_key() {
    let fixture = Fixture::new(Phase::ClosingExpired, true, 100).await;
    let hold = fixture.hold_unrelated().await;
    let recovered = tokio::time::timeout(
        Duration::from_secs(30),
        recover_one(&fixture.pool, Domain::Key, &fixture.target.to_string()),
    )
    .await;
    hold.rollback().await.unwrap();
    let first = fixture
        .member_column(&fixture.student(1), "is_finish")
        .await;
    let second = fixture
        .member_column(&fixture.student(2), "is_finish")
        .await;
    let status = fixture.train_status().await;
    let pages = fixture.count("general_key_pat_page", fixture.target).await;
    fixture.cleanup().await;
    recovered.expect("recovery did not finish").unwrap();
    assert_eq!(
        (first.as_deref(), second.as_deref()),
        (Some("1"), Some("1"))
    );
    assert_eq!(status, Some(2));
    assert_eq!(pages, 3, "recovery never generates missing pages either");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn starting_the_room_stamps_only_its_own_students() {
    let fixture = Fixture::new(Phase::Idle, false, 100).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.organizer()),
        fixture.target.to_string(),
    );
    let result = locked_beside(&fixture, || set_status(state, user, train, 1, Domain::Key)).await;
    let stamped: Vec<Option<String>> = vec![
        fixture
            .member_column(&fixture.student(1), "capture_started_at")
            .await,
        fixture
            .member_column(&fixture.student(2), "capture_started_at")
            .await,
        fixture
            .member_column(&fixture.organizer(), "capture_started_at")
            .await,
    ];
    let status = fixture.train_status().await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(status, Some(1));
    assert!(
        stamped[0].is_some() && stamped[0] == stamped[1],
        "both students share the start time: {stamped:?}"
    );
    assert!(stamped[2].is_none(), "the organizer is not stamped");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn restarting_an_ended_training_is_a_202_like_java() {
    let fixture = Fixture::new(Phase::ClosingOpen, false, 100).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.organizer()),
        fixture.target.to_string(),
    );
    let result = document(set_status(state, user, train, 1, Domain::Key).await).await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 202, "{result}");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn delete_removes_only_its_own_children() {
    let fixture = Fixture::new(Phase::Running, true, 100).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.organizer()),
        fixture.target.to_string(),
    );
    let result = locked_beside(&fixture, || delete(state, user, train, Domain::Key)).await;
    let remaining: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM general_key_pat WHERE id=?")
        .bind(fixture.target)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
    let mut children = 0;
    for table in CHILDREN {
        children += fixture.count(table, fixture.target).await;
    }
    let unrelated = fixture
        .count("general_key_pat_user", fixture.unrelated)
        .await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(remaining, 0);
    assert_eq!(children, 0);
    assert_eq!(unrelated, 2, "the unrelated training keeps its members");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn delete_waits_for_a_room_that_is_being_opened() {
    // Java RoomLifecycleLocks: while a socket holds the room's lifecycle lock (role lookup up to
    // registration), the delete may not run, so the room cannot be left open after the delete.
    let fixture = Fixture::new(Phase::Running, false, 100).await;
    let guard = crate::ws::general_room_lifecycle("key", &fixture.target.to_string())
        .await
        .expect("key is a general kind");
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.organizer()),
        fixture.target.to_string(),
    );
    let deleting = tokio::spawn(async move { delete(state, user, train, Domain::Key).await });
    tokio::time::sleep(Duration::from_millis(500)).await;
    let early = deleting.is_finished();
    let still_there: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM general_key_pat WHERE id=?")
        .bind(fixture.target)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
    drop(guard);
    let result = document(deleting.await.unwrap()).await;
    fixture.cleanup().await;
    assert!(!early, "delete must wait for the room lifecycle lock");
    assert_eq!(still_there, 1);
    assert_eq!(result["code"], 200, "{result}");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn export_takes_no_row_lock() {
    // Another transaction holds the training row X-locked (a finishing student): a read-only
    // export must neither wait for it nor lock anything itself.
    let fixture = Fixture::new(Phase::Running, true, 100).await;
    let mut hold = fixture.pool.begin().await.unwrap();
    sqlx::query("SELECT id FROM general_key_pat WHERE id=? FOR UPDATE")
        .bind(fixture.target)
        .fetch_one(&mut *hold)
        .await
        .unwrap();
    let organizer = fixture.user(fixture.organizer());
    let single = export(&fixture.pool, &organizer, Some(&fixture.target.to_string())).await;
    let batch = export(&fixture.pool, &organizer, None).await;
    hold.rollback().await.unwrap();
    fixture.cleanup().await;
    let single = single.expect("single export must not wait for the training row lock");
    assert_eq!(single["pageDto"].as_array().map(Vec::len), Some(3));
    batch.expect("batch export must not wait for the training row lock");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn find_page_takes_no_row_lock() {
    let fixture = Fixture::new(Phase::Running, true, 100).await;
    let mut hold = fixture.pool.begin().await.unwrap();
    sqlx::query("SELECT id FROM general_key_pat WHERE id=? FOR UPDATE")
        .bind(fixture.target)
        .fetch_one(&mut *hold)
        .await
        .unwrap();
    let result = document(
        page_response(
            fixture.state.clone(),
            fixture.user(fixture.student(1)),
            json!({"trainId": fixture.target.to_string(), "pageNumber": 1}),
            Domain::Key,
            false,
        )
        .await,
    )
    .await;
    hold.rollback().await.unwrap();
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    let rows = result["data"]["messageContent"].as_array().unwrap();
    assert_eq!(rows.len(), 3);
    assert!(
        rows[0].get("trainId").is_none(),
        "Java's page DTO has no trainId"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn import_never_locks_t_user_by_account() {
    // The account column is unindexed: a locking read by account would scan (and lock) all of
    // t_user. An unrelated user row is held X-locked; the import of an archive naming an
    // existing account must still go through, keep that user, and default isCable to 0.
    let fixture = Fixture::new(Phase::Idle, false, 100).await;
    let account = format!("zzz-import-{}", fixture.tag);
    let bystander = format!("aaa-bystander-{}", fixture.tag);
    for (id, name) in [(&bystander, bystander.clone()), (&account, account.clone())] {
        sqlx::query("INSERT INTO t_user (id,user_account,user_name,status) VALUES (?,?,?,0)")
            .bind(id)
            .bind(name)
            .bind("import regression")
            .execute(&fixture.pool)
            .await
            .unwrap();
    }
    let mut hold = fixture.pool.begin().await.unwrap();
    sqlx::query("SELECT id FROM t_user WHERE id=? FOR UPDATE")
        .bind(&bystander)
        .fetch_one(&mut *hold)
        .await
        .unwrap();
    let archive = json!({
        "trainDto": {"protocolVersion": 1, "title": format!("import {}", fixture.tag), "totalNumber": 100,
            "ruleId": "rule", "ruleContent": RULE, "createUser": "old", "status": 0, "validTime": 0,
            "messageType": 0, "isAverage": 0, "isRandom": 1},
        "pageDto": [], "userDto": [], "userValueDto": [], "moreDto": [],
        "users": [{"id": "old", "userAccount": account, "userName": "import regression"}]
    });
    let admin = AuthUser {
        id: "admin".into(),
        is_admin: true,
    };
    let imported = import(&fixture.pool, &admin, archive, false).await;
    hold.rollback().await.unwrap();
    let rows: Vec<(i32, Option<i32>, String)> =
        sqlx::query_as("SELECT id, is_cable, create_user FROM general_key_pat WHERE title=?")
            .bind(format!("import {}", fixture.tag))
            .fetch_all(&fixture.pool)
            .await
            .unwrap();
    for (id, _, _) in &rows {
        sqlx::query("DELETE FROM general_key_pat WHERE id=?")
            .bind(id)
            .execute(&fixture.pool)
            .await
            .unwrap();
    }
    for id in [&bystander, &account] {
        sqlx::query("DELETE FROM t_user WHERE id=?")
            .bind(id)
            .execute(&fixture.pool)
            .await
            .unwrap();
    }
    fixture.cleanup().await;
    imported.expect("the import must not wait on unrelated t_user rows");
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0].1, Some(0), "Java's entity default is isCable=0");
    assert_eq!(
        rows[0].2, account,
        "the existing account was reused, not duplicated"
    );
}
