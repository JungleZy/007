//! Isolated-DB regressions for the group telex (generalTelexPat) training writers and readers.
//!
//! The three child tables (members, source pages, values) are indexed by primary key only, so a
//! locking read, UPDATE or DELETE filtered by `train_id`/`user_id` next-key locks every row it
//! scans, including other trainings' rows. Every telex writer must lock the training row by
//! primary key, read child ids plainly and write by primary key. Each test keeps a second,
//! earlier training's rows X-locked (any scan from the start of the clustered index collides
//! with them after a 2 s lock wait timeout) while the operation works on the later training.
//! Reads (getPage of an existing page, findPage) must take no row lock at all.
use crate::domain::general_train::general_train::*;
use crate::platform::auth::AuthUser;
use crate::platform::config::AppConfig;
use crate::platform::state::AppState;
use axum::extract::Extension;
use axum::extract::State;
use axum::response::Response;
use axum::Json;
use serde_json::json;
use serde_json::Value;
use sqlx::{MySql, MySqlPool, Transaction};
use std::sync::Arc;
use std::time::Duration;

const RULE: &str = r#"{"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":100,"r":1,"l":1},"other":{"errorCode":3,"muchLessCode":2,"muchLessLine":2,"muchLessGroups":2,"correctMistakes":2,"lessPage":7,"lessReturnLine":2,"errorPage":3,"nonStandart":1,"alterError":2}}"#;
const RULE_WITHOUT_ALTER_ERROR: &str = r#"{"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":100,"r":1,"l":1},"other":{"errorCode":3,"muchLessCode":2,"muchLessLine":2,"muchLessGroups":2,"correctMistakes":2,"lessPage":7,"lessReturnLine":2,"errorPage":3,"nonStandart":1}}"#;

const CHILDREN: [&str; 3] = [
    "general_telex_pat_user",
    "general_telex_pat_page",
    "general_telex_pat_user_value",
];

#[derive(Clone, Copy, PartialEq)]
enum Phase {
    Idle,
    Running,
    ClosingExpired,
}

struct Fixture {
    state: AppState,
    pool: MySqlPool,
    tag: String,
    /// The earlier, unrelated training whose rows stay locked.
    unrelated: String,
    /// The training the writer works on.
    target: String,
}

impl Fixture {
    fn student(&self, number: u8) -> String {
        format!("zzz-student{number}-{}", self.tag)
    }

    fn organizer(&self) -> String {
        format!("zzz-organizer-{}", self.tag)
    }

    async fn new(phase: Phase, with_pages: bool, total_number: i64) -> Self {
        Self::with_rule(phase, with_pages, total_number, RULE).await
    }

    async fn with_rule(phase: Phase, with_pages: bool, total_number: i64, rule: &str) -> Self {
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
            unrelated: String::new(),
            target: String::new(),
        };
        // Created first, so every row of the unrelated training sorts before the target's rows.
        fixture.unrelated = fixture
            .training("aaa", phase, with_pages, total_number, rule)
            .await;
        fixture.target = fixture
            .training("zzz", phase, with_pages, total_number, rule)
            .await;
        fixture
    }

    async fn training(
        &self,
        side: &str,
        phase: Phase,
        with_pages: bool,
        total_number: i64,
        rule: &str,
    ) -> String {
        let (status, start, end) = match phase {
            Phase::Idle => (0, "NULL", "NULL"),
            Phase::Running => (1, "DATE_SUB(NOW(6),INTERVAL 1 MINUTE)", "NULL"),
            Phase::ClosingExpired => (
                2,
                "DATE_SUB(NOW(6),INTERVAL 5 MINUTE)",
                "DATE_SUB(NOW(6),INTERVAL 3 MINUTE)",
            ),
        };
        // The ids sort the unrelated training first.
        let train = format!("{side}-train-{}", self.tag);
        let sql = format!("INSERT INTO general_telex_pat (id,title,is_cable,create_time,create_user,type,pat_type,rule_content,rule_id,rule_score,start_time,end_time,status,total_number,train_type,valid_time,protocol_version) VALUES (?,?,0,NOW(),?,0,2,?,'rule',100,{start},{end},{status},?,0,0,1)");
        sqlx::query(&sql)
            .bind(&train)
            .bind(format!("telex lock regression {side}-{}", self.tag))
            .bind(format!("{side}-organizer-{}", self.tag))
            .bind(rule)
            .bind(total_number)
            .execute(&self.pool)
            .await
            .unwrap();
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
        for (user, role) in &members {
            let capture = if *role == 0 { started } else { "NULL" };
            sqlx::query(&format!("INSERT INTO general_telex_pat_user (id,train_id,user_id,role,attempt,is_finish,score,accuracy,error_number,create_time,capture_started_at) VALUES (?,?,?,?,0,0,0,0,0,NOW(),{capture})"))
                .bind(format!("{side}-member-{user}"))
                .bind(&train)
                .bind(user)
                .bind(role)
                .execute(&self.pool)
                .await
                .unwrap();
        }
        if with_pages {
            for sort in 0..10 {
                let key = format!("{}", 1001 + sort);
                sqlx::query("INSERT INTO general_telex_pat_page (id,train_id,page_number,sort,`key`) VALUES (?,?,1,?,?)")
                    .bind(format!("{side}-page-{sort}-{}", self.tag))
                    .bind(&train)
                    .bind(sort as i32)
                    .bind(key)
                    .execute(&self.pool)
                    .await
                    .unwrap();
            }
            let owner = if side == "zzz" {
                self.student(1)
            } else {
                format!("aaa-student-{}", self.tag)
            };
            sqlx::query("INSERT INTO general_telex_pat_user_value (id,train_id,user_id,page_number,sort,`value`,attempt,capture_intervals,received_at) VALUES (?,?,?,1,-1,'1001 9999 1003 1004 1005 1006 1007 1008 1009 1010',0,'[{\"startedMs\":0,\"endedMs\":1000}]',NOW(6))")
                .bind(format!("{side}-raw-{}", self.tag))
                .bind(&train)
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
        sqlx::query("SELECT id FROM general_telex_pat WHERE id=? FOR UPDATE")
            .bind(&self.unrelated)
            .fetch_one(&mut *hold)
            .await
            .unwrap();
        for table in CHILDREN {
            let ids: Vec<String> = sqlx::query_scalar(&format!(
                "SELECT CAST(id AS CHAR) FROM {table} WHERE train_id=? ORDER BY id"
            ))
            .bind(&self.unrelated)
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

    async fn count(&self, table: &str, train: &str) -> i64 {
        sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {table} WHERE train_id=?"))
            .bind(train)
            .fetch_one(&self.pool)
            .await
            .unwrap()
    }

    async fn member_column(&self, user: &str, column: &str) -> Option<String> {
        sqlx::query_scalar(&format!(
            "SELECT CAST({column} AS CHAR) FROM general_telex_pat_user WHERE train_id=? AND user_id=?"
        ))
        .bind(&self.target)
        .bind(user)
        .fetch_one(&self.pool)
        .await
        .unwrap()
    }

    async fn train_status(&self) -> Option<i32> {
        sqlx::query_scalar("SELECT status FROM general_telex_pat WHERE id=?")
            .bind(&self.target)
            .fetch_one(&self.pool)
            .await
            .unwrap()
    }

    async fn analysis_rows(&self, user: &str) -> i64 {
        sqlx::query_scalar("SELECT COUNT(*) FROM general_telex_pat_user_value WHERE train_id=? AND user_id=? AND sort>-1")
            .bind(&self.target)
            .bind(user)
            .fetch_one(&self.pool)
            .await
            .unwrap()
    }

    async fn cleanup(self) {
        for train in [&self.unrelated, &self.target] {
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
            sqlx::query("DELETE FROM general_telex_pat WHERE id=?")
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
    let scan = sqlx::query("SELECT id FROM general_telex_pat_user WHERE user_id=? FOR UPDATE")
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
        fixture.target.clone(),
    );
    let result = locked_beside(&fixture, || {
        participant_action(
            state,
            user,
            json!({"trainId": train, "attempt": 0}),
            Domain::Telex,
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
async fn start_and_finish_by_a_non_student_are_207() {
    let fixture = Fixture::new(Phase::Running, true, 100).await;
    let outsider = format!("zzz-outsider-{}", fixture.tag);
    let mut codes = Vec::new();
    for who in [fixture.organizer(), outsider] {
        let start = document(
            participant_action(
                fixture.state.clone(),
                fixture.user(who.clone()),
                json!({"trainId": fixture.target, "attempt": 0}),
                Domain::Telex,
                false,
            )
            .await,
        )
        .await;
        let finish = document(
            finish_participant(
                fixture.state.clone(),
                fixture.user(who),
                json!({"trainId": fixture.target, "attempt": 0}),
                Domain::Telex,
            )
            .await,
        )
        .await;
        codes.push((start["code"].clone(), finish["code"].clone()));
    }
    fixture.cleanup().await;
    assert_eq!(codes, vec![(json!(207), json!(207)); 2]);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn upload_extends_its_page_and_derives_by_primary_key() {
    let fixture = Fixture::new(Phase::Running, true, 100).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target.clone(),
    );
    let body = json!({"trainId": train, "pageNumber": 1, "attempt": 0, "protocolVersion": 1,
        "patValue": "1001 1002 1003 1004",
        "captureIntervals": [{"startedMs":0,"endedMs":1000},{"startedMs":2000,"endedMs":3000}]});
    let result = locked_beside(&fixture, || upload(state, user, body, Domain::Telex)).await;
    let raw: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM general_telex_pat_user_value WHERE train_id=? AND sort=-1",
    )
    .bind(&fixture.target)
    .fetch_one(&fixture.pool)
    .await
    .unwrap();
    let active = fixture
        .member_column(&fixture.student(1), "active_millis")
        .await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(raw, 1, "the old raw row is replaced, not duplicated");
    assert_eq!(active.as_deref(), Some("2000"));
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn finish_writes_the_analysis_rows_and_never_generates_missing_pages() {
    let fixture = Fixture::new(Phase::Running, true, 250).await;
    // A stale analysis row of the same student is replaced; the raw row stays.
    sqlx::query("INSERT INTO general_telex_pat_user_value (id,train_id,user_id,page_number,sort,`key`,`value`) VALUES (?,?,?,1,7,'old','old')")
        .bind(format!("zzz-stale-{}", fixture.tag))
        .bind(&fixture.target)
        .bind(fixture.student(1))
        .execute(&fixture.pool)
        .await
        .unwrap();
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target.clone(),
    );
    let result = locked_beside(&fixture, || {
        finish_participant(
            state,
            user,
            json!({"trainId": train, "attempt": 0}),
            Domain::Telex,
        )
    })
    .await;
    let finished = fixture
        .member_column(&fixture.student(1), "is_finish")
        .await;
    let other = fixture
        .member_column(&fixture.student(2), "is_finish")
        .await;
    let error_number = fixture
        .member_column(&fixture.student(1), "error_number")
        .await;
    let analysis = fixture.analysis_rows(&fixture.student(1)).await;
    let stale: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM general_telex_pat_user_value WHERE id=?")
            .bind(format!("zzz-stale-{}", fixture.tag))
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
    let raw: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM general_telex_pat_user_value WHERE train_id=? AND sort=-1",
    )
    .bind(&fixture.target)
    .fetch_one(&fixture.pool)
    .await
    .unwrap();
    let pages = fixture
        .count("general_telex_pat_page", &fixture.target)
        .await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(finished.as_deref(), Some("1"));
    assert_eq!(other.as_deref(), Some("0"), "only the caller settles");
    assert_eq!(
        error_number.as_deref(),
        Some("0"),
        "Java never writes errorNumber"
    );
    assert_eq!(analysis, 10, "one analysis row per source group");
    assert_eq!(stale, 0);
    assert_eq!(raw, 1);
    assert_eq!(pages, 10, "no page is generated at finish");
    assert_eq!(result["data"][0]["accuracy"], "90.00", "{result}");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn a_rule_without_alter_error_settles_with_correct_mistakes() {
    let fixture = Fixture::with_rule(Phase::Running, true, 100, RULE_WITHOUT_ALTER_ERROR).await;
    let result = document(
        finish_participant(
            fixture.state.clone(),
            fixture.user(fixture.student(1)),
            json!({"trainId": fixture.target, "attempt": 0}),
            Domain::Telex,
        )
        .await,
    )
    .await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn recovery_settles_an_expired_closing_training_without_generating_pages() {
    let fixture = Fixture::new(Phase::ClosingExpired, true, 250).await;
    let hold = fixture.hold_unrelated().await;
    let recovered = tokio::time::timeout(
        Duration::from_secs(30),
        recover_one(&fixture.pool, Domain::Telex, &fixture.target),
    )
    .await;
    hold.rollback().await.unwrap();
    let first = fixture
        .member_column(&fixture.student(1), "is_finish")
        .await;
    let second = fixture
        .member_column(&fixture.student(2), "is_finish")
        .await;
    let pages = fixture
        .count("general_telex_pat_page", &fixture.target)
        .await;
    fixture.cleanup().await;
    recovered.expect("recovery did not finish").unwrap();
    assert_eq!(
        (first.as_deref(), second.as_deref()),
        (Some("1"), Some("1"))
    );
    assert_eq!(pages, 10, "recovery never generates missing pages");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn starting_the_room_stamps_only_its_own_students() {
    let fixture = Fixture::new(Phase::Idle, false, 100).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.organizer()),
        fixture.target.clone(),
    );
    let result = locked_beside(&fixture, || {
        set_status(state, user, train, 1, Domain::Telex)
    })
    .await;
    let stamped = vec![
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
        "{stamped:?}"
    );
    assert!(stamped[2].is_none(), "the organizer is not stamped");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn delete_removes_only_its_own_children() {
    let fixture = Fixture::new(Phase::Running, true, 100).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.organizer()),
        fixture.target.clone(),
    );
    let result = locked_beside(&fixture, || delete(state, user, train, Domain::Telex)).await;
    let remaining: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM general_telex_pat WHERE id=?")
        .bind(&fixture.target)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
    let mut children = 0;
    for table in CHILDREN {
        children += fixture.count(table, &fixture.target).await;
    }
    let unrelated = fixture
        .count("general_telex_pat_user", &fixture.unrelated)
        .await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(remaining, 0);
    assert_eq!(children, 0);
    assert_eq!(unrelated, 2, "the unrelated training keeps its members");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn reading_an_existing_page_takes_no_row_lock() {
    let fixture = Fixture::new(Phase::Running, true, 100).await;
    let mut hold = fixture.pool.begin().await.unwrap();
    sqlx::query("SELECT id FROM general_telex_pat WHERE id=? FOR UPDATE")
        .bind(&fixture.target)
        .fetch_one(&mut *hold)
        .await
        .unwrap();
    let get_page = document(
        telex_get_page(
            State(fixture.state.clone()),
            Extension(fixture.user(fixture.student(1))),
            Json(json!({"trainId": fixture.target, "pageNumber": 1})),
        )
        .await,
    )
    .await;
    let find_page = document(
        page_response(
            fixture.state.clone(),
            fixture.user(fixture.student(1)),
            json!({"trainId": fixture.target, "pageNumber": 1}),
            Domain::Telex,
            false,
        )
        .await,
    )
    .await;
    hold.rollback().await.unwrap();
    fixture.cleanup().await;
    assert_eq!(get_page["code"], 200, "{get_page}");
    assert_eq!(find_page["code"], 200, "{find_page}");
    assert!(find_page["data"]["messageContent"][0]
        .get("trainId")
        .is_none());
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn a_missing_page_is_generated_under_the_training_lock_beside_other_trainings() {
    let fixture = Fixture::new(Phase::Running, true, 250).await;
    let (state, user, train) = (
        fixture.state.clone(),
        fixture.user(fixture.student(1)),
        fixture.target.clone(),
    );
    let result = locked_beside(&fixture, || {
        telex_get_page(
            State(state),
            Extension(user),
            Json(json!({"trainId": train, "pageNumber": 3})),
        )
    })
    .await;
    let generated: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM general_telex_pat_page WHERE train_id=? AND page_number=3 AND `value` IS NULL")
        .bind(&fixture.target)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(
        generated, 50,
        "page 3 of 250 groups has 50 groups, stored without a value"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn a_non_member_reading_a_missing_page_gets_it_without_any_write() {
    let fixture = Fixture::new(Phase::Running, false, 250).await;
    let outsider = format!("zzz-outsider-{}", fixture.tag);
    let result = document(
        telex_get_page(
            State(fixture.state.clone()),
            Extension(fixture.user(outsider)),
            Json(json!({"trainId": fixture.target, "pageNumber": 3})),
        )
        .await,
    )
    .await;
    let stored: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM general_telex_pat_page WHERE train_id=?")
            .bind(&fixture.target)
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(
        result["data"]["messageVO"].as_array().map(Vec::len),
        Some(50),
        "the response still shows the 50 groups of page 3"
    );
    assert_eq!(stored, 0, "a non-member read must not persist pages");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn pat_detail_of_a_non_member_reading_oneself_is_202() {
    let fixture = Fixture::new(Phase::Running, true, 100).await;
    let outsider = format!("zzz-outsider-{}", fixture.tag);
    let result = document(
        members(
            fixture.state.clone(),
            fixture.user(outsider.clone()),
            json!({"trainId": fixture.target, "userId": outsider}),
            Domain::Telex,
        )
        .await,
    )
    .await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 202, "{result}");
}
