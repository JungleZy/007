//! Isolated-DB regressions for the hand-key (generalTickerPat) training writers.
//!
//! The training child tables (members, saved pages, base text) are indexed by primary key only,
//! so a locking read, UPDATE or DELETE filtered by `train_id`/`user_id` next-key locks every row
//! it scans, including other trainings' rows. Every writer here must lock the training row by
//! primary key, read child ids plainly and write by primary key. Each test keeps a second,
//! earlier training's rows X-locked (so any scan from the start of the clustered index collides
//! with them, after a 2 s lock wait timeout) while a writer works on the later training.
use super::*;
use crate::AppConfig;
use sqlx::{MySql, MySqlPool, Transaction};
use std::sync::Arc;
use std::time::Duration;

const RULE: &str = r#"{"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":240,"r":2,"l":1},"skew":51,"code":{"dot":{"base":1000,"l":1,"r":1,"max":1},"dash":{"base":1000,"l":1,"r":1,"max":5}},"gap":{"little":{"base":1000,"l":1,"r":1,"max":1},"middle":{"base":1000,"l":1,"r":1,"max":1},"large":{"base":1000,"l":1,"r":1,"max":1}},"other":{"errorCode":{"l":1,"max":1},"quantoCode":{"l":1,"max":1},"quantoGroup":{"l":1,"max":1},"alterError":{"l":1,"max":1},"quantoRow":{"l":1,"max":1},"bunchGroup":{"l":1,"max":1}}}"#;

fn group() -> Value {
    json!({"id":"g","moresKey":json!(["E","E","E","E"]).to_string(),"patKeys":json!(["E","E","E","E"]).to_string(),
        "moresValue":json!([[0],[0],[0],[0]]).to_string(),
        "moresTime":json!([[100],[100],[100],[100]]).to_string(),
        "patLogs":json!([[{"name":"间隔","key":2,"value":700},{"name":"点","key":0,"value":100}],
            [{"name":"间隔","key":2,"value":300},{"name":"点","key":0,"value":100}],
            [{"name":"间隔","key":2,"value":300},{"name":"点","key":0,"value":100}],
            [{"name":"间隔","key":2,"value":300},{"name":"点","key":0,"value":100}]]).to_string()})
}

fn standards() -> Value {
    json!([{"dot":100,"line":300,"codeGap":100,"wordGap":300,"groupGap":700,"offSize":51}])
}

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

impl Fixture {
    fn student(&self, number: u8) -> String {
        format!("zzz-student{number}-{}", self.tag)
    }

    fn organizer(&self) -> String {
        format!("zzz-organizer-{}", self.tag)
    }

    async fn new(phase: Phase, with_pages: bool) -> Self {
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
        fixture.unrelated = fixture.training("aaa", phase, with_pages).await;
        fixture.target = fixture.training("zzz", phase, with_pages).await;
        fixture
    }

    async fn training(&self, side: &str, phase: Phase, with_pages: bool) -> i64 {
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
        let sql = format!("INSERT INTO general_ticker_pat (name,is_cable,code_sort,create_time,create_user,is_average,is_random,message_number,rule_content,rule_id,start_time,end_time,status,train_type,type,valid_time,rule_score,protocol_version) VALUES (?,1,0,NOW(),?,0,0,100,?,'rule',{start},{end},{status},0,1,0,100,1)");
        let name = format!("ticker lock regression {side}-{}", self.tag);
        let train = sqlx::query(&sql)
            .bind(name)
            .bind(format!("{side}-organizer-{}", self.tag))
            .bind(RULE)
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
        for (user, role) in members {
            let capture = if role == 0 { started } else { "NULL" };
            sqlx::query(&format!("INSERT INTO general_ticker_pat_train_user (train_id,user_id,role,attempt,is_finish,score,accuracy,error_number,create_time,capture_started_at) VALUES (?,?,?,0,NULL,0,'0.00',0,NOW(),{capture})"))
                .bind(train)
                .bind(user)
                .bind(role)
                .execute(&self.pool)
                .await
                .unwrap();
        }
        if with_pages {
            for sort in 0..3 {
                sqlx::query("INSERT INTO general_ticker_pat_train_page (id,train_id,floor_number,sort,mores_key,mores_value,mores_time,pat_keys) VALUES (?,?,1,?,?,'[]','[]','[]')")
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
            sqlx::query("INSERT INTO general_ticker_pat_train_user_value (train_id,user_id,floor_number,finish_info,message_body,standard,attempt,capture_intervals,received_at) VALUES (?,?,1,'{}',?,?,0,?,NOW(6))")
                .bind(train)
                .bind(owner)
                .bind(json!([group()]).to_string())
                .bind(standards().to_string())
                .bind(json!([{"startedMs":0,"endedMs":1000}]).to_string())
                .execute(&self.pool)
                .await
                .unwrap();
        }
        train
    }

    /// X-locks, by primary key, the training row and every child row of the unrelated training.
    async fn hold_unrelated(&self) -> Transaction<'static, MySql> {
        let mut hold = self.pool.begin().await.unwrap();
        sqlx::query("SELECT id FROM general_ticker_pat WHERE id=? FOR UPDATE")
            .bind(self.unrelated)
            .fetch_one(&mut *hold)
            .await
            .unwrap();
        for table in [
            "general_ticker_pat_train_user",
            "general_ticker_pat_train_page",
            "general_ticker_pat_train_user_value",
        ] {
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
            "SELECT CAST({column} AS CHAR) FROM general_ticker_pat_train_user WHERE train_id=? AND user_id=?"
        ))
        .bind(self.target)
        .bind(user)
        .fetch_one(&self.pool)
        .await
        .unwrap()
    }

    async fn train_status(&self) -> Option<i32> {
        sqlx::query_scalar("SELECT status FROM general_ticker_pat WHERE id=?")
            .bind(self.target)
            .fetch_one(&self.pool)
            .await
            .unwrap()
    }

    async fn cleanup(self) {
        for train in [self.unrelated, self.target] {
            for table in [
                "general_ticker_pat_train_user_value",
                "general_ticker_pat_train_page",
                "general_ticker_pat_train_user",
            ] {
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
            sqlx::query("DELETE FROM general_ticker_pat WHERE id=?")
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
    let fixture = Fixture::new(Phase::Running, false).await;
    let hold = fixture.hold_unrelated().await;
    let scan =
        sqlx::query("SELECT id FROM general_ticker_pat_train_user WHERE user_id=? FOR UPDATE")
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
    let fixture = Fixture::new(Phase::Running, false).await;
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
            Domain::Ticker,
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
async fn reset_locks_only_its_own_member_and_pages() {
    let fixture = Fixture::new(Phase::Running, true).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target,
    );
    let result = locked_beside(&fixture, || {
        participant_action(
            state,
            user,
            json!({"id": train.to_string(), "attempt": 0}),
            Domain::Ticker,
            true,
        )
    })
    .await;
    let attempt = fixture.member_column(&fixture.student(1), "attempt").await;
    let saved = fixture
        .count("general_ticker_pat_train_user_value", fixture.target)
        .await;
    let unrelated_saved = fixture
        .count("general_ticker_pat_train_user_value", fixture.unrelated)
        .await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(attempt.as_deref(), Some("1"));
    assert_eq!(saved, 0, "the reset page rows must be deleted");
    assert_eq!(unrelated_saved, 1, "the unrelated training keeps its page");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn upload_replaces_its_page_without_scanning_other_trainings() {
    let fixture = Fixture::new(Phase::Running, true).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target,
    );
    let body = json!({"trainId": train.to_string(), "floorNumber": 1, "attempt": 0, "messageBody": [group()],
        "standard": standards(), "finishInfo": "{}",
        "captureIntervals": [{"startedMs":0,"endedMs":1000},{"startedMs":2000,"endedMs":3000}]});
    let result = locked_beside(&fixture, || upload(state, user, body, Domain::Ticker)).await;
    let rows: Vec<String> = sqlx::query_scalar(
        "SELECT capture_intervals FROM general_ticker_pat_train_user_value WHERE train_id=?",
    )
    .bind(fixture.target)
    .fetch_all(&fixture.pool)
    .await
    .unwrap();
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(
        rows.len(),
        1,
        "the old page row is replaced, not duplicated"
    );
    assert!(
        rows[0].contains("2000"),
        "the extended timeline is stored: {rows:?}"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn finish_settles_and_writes_the_resolver_by_primary_key() {
    let fixture = Fixture::new(Phase::Running, true).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target,
    );
    let result = locked_beside(&fixture, || {
        finish_participant(
            state,
            user,
            json!({"id": train.to_string(), "attempt": 0}),
            Domain::Ticker,
        )
    })
    .await;
    let finished = fixture
        .member_column(&fixture.student(1), "is_finish")
        .await;
    let accuracy = fixture.member_column(&fixture.student(1), "accuracy").await;
    let resolver: Option<String> = sqlx::query_scalar(
        "SELECT resolver FROM general_ticker_pat_train_user_value WHERE train_id=?",
    )
    .bind(fixture.target)
    .fetch_one(&fixture.pool)
    .await
    .unwrap();
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(finished.as_deref(), Some("1"));
    assert_eq!(
        accuracy.as_deref(),
        Some("100.00"),
        "the one submitted group matches the first source group"
    );
    assert!(
        resolver.is_some_and(|text| text.contains("resolverMessage")),
        "Java writes the page resolver back at settlement"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn finish_inside_the_grace_window_settles_only_the_caller() {
    let fixture = Fixture::new(Phase::ClosingOpen, true).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target,
    );
    let result = locked_beside(&fixture, || {
        finish_participant(
            state,
            user,
            json!({"id": train.to_string(), "attempt": 0}),
            Domain::Ticker,
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
    assert_eq!((first.as_deref(), second.as_deref()), (Some("1"), None));
    assert_eq!(status, Some(3), "the other student is still unfinished");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn finish_after_the_grace_window_settles_every_student() {
    let fixture = Fixture::new(Phase::ClosingExpired, true).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(2)),
        fixture.target,
    );
    let result = locked_beside(&fixture, || {
        finish_participant(
            state,
            user,
            json!({"id": train.to_string(), "attempt": 0}),
            Domain::Ticker,
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
    let fixture = Fixture::new(Phase::ClosingExpired, true).await;
    let (pool, train) = (fixture.pool.clone(), fixture.target.to_string());
    let hold = fixture.hold_unrelated().await;
    let outcome = tokio::time::timeout(
        Duration::from_secs(30),
        recover_one(&pool, Domain::Ticker, &train),
    )
    .await
    .expect("recovery did not finish");
    hold.rollback().await.unwrap();
    let first = fixture
        .member_column(&fixture.student(1), "is_finish")
        .await;
    let second = fixture
        .member_column(&fixture.student(2), "is_finish")
        .await;
    let status = fixture.train_status().await;
    fixture.cleanup().await;
    assert!(outcome.is_ok(), "{outcome:?}");
    assert_eq!(
        (first.as_deref(), second.as_deref()),
        (Some("1"), Some("1"))
    );
    assert_eq!(status, Some(2));
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn starting_the_room_stamps_only_its_own_students() {
    let fixture = Fixture::new(Phase::Idle, false).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.organizer()),
        fixture.target,
    );
    let result = locked_beside(&fixture, || {
        ticker_socket_update(state, user, train, Some(1))
    })
    .await;
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
async fn stopping_the_room_counts_unfinished_students_without_locking_them() {
    // Both students are unfinished: the room goes to the closing status 3, not straight to 2.
    let fixture = Fixture::new(Phase::Running, false).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.organizer()),
        fixture.target,
    );
    let result = locked_beside(&fixture, || {
        ticker_socket_update(state, user, train, Some(2))
    })
    .await;
    let status = fixture.train_status().await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(status, Some(3));

    // Everyone already finished: the stop completes the training.
    let fixture = Fixture::new(Phase::Running, false).await;
    for number in [1, 2] {
        sqlx::query(
            "UPDATE general_ticker_pat_train_user SET is_finish=1 WHERE train_id=? AND user_id=?",
        )
        .bind(fixture.target)
        .bind(fixture.student(number))
        .execute(&fixture.pool)
        .await
        .unwrap();
    }
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.organizer()),
        fixture.target,
    );
    let result = locked_beside(&fixture, || {
        ticker_socket_update(state, user, train, Some(2))
    })
    .await;
    let status = fixture.train_status().await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(status, Some(2));
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn delete_removes_only_its_own_children() {
    let fixture = Fixture::new(Phase::Running, true).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.organizer()),
        fixture.target.to_string(),
    );
    let result = locked_beside(&fixture, || delete(state, user, train, Domain::Ticker)).await;
    let remaining: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM general_ticker_pat WHERE id=?")
        .bind(fixture.target)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
    let mut children = 0;
    for table in [
        "general_ticker_pat_train_user",
        "general_ticker_pat_train_page",
        "general_ticker_pat_train_user_value",
    ] {
        children += fixture.count(table, fixture.target).await;
    }
    let unrelated = fixture
        .count("general_ticker_pat_train_user", fixture.unrelated)
        .await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(remaining, 0);
    assert_eq!(children, 0);
    assert_eq!(unrelated, 2, "the unrelated training keeps its members");
}
