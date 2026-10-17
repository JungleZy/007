//! Isolated-DB regressions for the radiotelephone (报话) and military-term (军语) writers.
//!
//! `t_radiotelephone_train.active_session_id`, `t_post_military_term_train_test_paper.train_id`
//! and `t_military_term_data.parent_id/sort` have no index, so a locking read, UPDATE or DELETE
//! filtered by them next-key locks every row it scans, other users' rows included. Writers must
//! lock the parent/user row by primary key, read ids plainly and write by primary key; readers
//! must not lock. Each test keeps an earlier, unrelated user's rows X-locked (so a scan from the
//! start of the clustered index collides with them, after a 2 s lock wait timeout) while the
//! endpoint works on the later user's rows.
use crate::platform::auth::AuthUser;
use crate::platform::config::AppConfig;
use crate::platform::state::AppState;
use axum::extract::{Extension, Query, State};
use axum::response::Response;
use axum::Json;
use serde_json::{json, Value};
use sqlx::{MySql, MySqlPool, Transaction};
use std::sync::Arc;
use std::time::Duration;

struct Fixture {
    state: AppState,
    pool: MySqlPool,
    tag: String,
}

impl Fixture {
    async fn new() -> Self {
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
        Self { state, pool, tag }
    }

    /// Ids sort `aaa-*` (unrelated) before `zzz-*` (target) in every primary key.
    fn id(&self, side: &str, name: &str) -> String {
        format!("{side}-{name}-{}", self.tag)
    }

    fn user(&self, side: &str) -> AuthUser {
        AuthUser {
            id: self.id(side, "user"),
            is_admin: false,
        }
    }

    async fn exec(&self, sql: &str, binds: &[&str]) {
        let mut query = sqlx::query(sql);
        for bind in binds {
            query = query.bind(*bind);
        }
        query.execute(&self.pool).await.unwrap();
    }

    /// X-locks the given rows by primary key in one transaction.
    async fn hold(&self, table: &str, ids: &[String]) -> Transaction<'static, MySql> {
        let mut hold = self.pool.begin().await.unwrap();
        for id in ids {
            sqlx::query(&format!("SELECT id FROM {table} WHERE id=? FOR UPDATE"))
                .bind(id)
                .fetch_one(&mut *hold)
                .await
                .unwrap();
        }
        hold
    }

    async fn scalar(&self, sql: &str, bind: &str) -> String {
        sqlx::query_scalar::<_, Option<String>>(sql)
            .bind(bind)
            .fetch_one(&self.pool)
            .await
            .unwrap()
            .unwrap_or_default()
    }

    async fn cleanup(self) {
        for side in ["aaa", "zzz"] {
            for table in [
                "t_post_military_term_train_test_paper",
                "t_military_term_data",
            ] {
                let ids: Vec<String> =
                    sqlx::query_scalar(&format!("SELECT id FROM {table} WHERE id LIKE ?"))
                        .bind(format!("{side}-%-{}", self.tag))
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
            for table in [
                "t_post_military_term_train",
                "t_radiotelephone_train",
                "t_user",
            ] {
                let ids: Vec<String> =
                    sqlx::query_scalar(&format!("SELECT id FROM {table} WHERE id LIKE ?"))
                        .bind(format!("{side}-%-{}", self.tag))
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
    }
}

async fn document(response: Response) -> Value {
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    serde_json::from_slice(&bytes).unwrap()
}

async fn within<Fut: std::future::Future<Output = Response>>(operation: Fut) -> Value {
    document(
        tokio::time::timeout(Duration::from_secs(30), operation)
            .await
            .expect("endpoint did not finish"),
    )
    .await
}

// ---------- radiotelephone (t_radiotelephone_train) ----------

impl Fixture {
    /// Two users, each with a type-0 aggregate row; the unrelated one has a running session.
    async fn radio(&self) -> String {
        let session = self.id("aaa", "session");
        for side in ["aaa", "zzz"] {
            self.exec(
                "INSERT INTO t_user (id,user_name) VALUES (?,'radio regression')",
                &[&self.id(side, "user")],
            )
            .await;
            self.exec(
                "INSERT INTO t_radiotelephone_train (id,user_id,type,total_time,total_count) VALUES (?,?,0,'0',0)",
                &[&self.id(side, "agg"), &self.id(side, "user")],
            )
            .await;
        }
        self.exec(
            "UPDATE t_radiotelephone_train SET active_session_id=?,session_started_at=NOW(6),active_millis=0 WHERE id=?",
            &[&session, &self.id("aaa", "agg")],
        )
        .await;
        session
    }

    async fn hold_unrelated_radio(
        &self,
    ) -> (Transaction<'static, MySql>, Transaction<'static, MySql>) {
        (
            self.hold("t_user", &[self.id("aaa", "user")]).await,
            self.hold("t_radiotelephone_train", &[self.id("aaa", "agg")])
                .await,
        )
    }
}

fn lifecycle(
    session: &str,
) -> Json<crate::domain::radiotelephone::radiotelephone::LifecycleRequest> {
    Json(serde_json::from_value(json!({"type": 0, "sessionId": session})).unwrap())
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn the_held_unrelated_session_row_really_blocks_a_session_scan() {
    // Canary: a locking read by the unindexed active_session_id scans the whole table and so
    // collides with the held unrelated rows. Without this the other tests could pass vacuously.
    let fixture = Fixture::new().await;
    fixture.radio().await;
    let (user, aggregate) = fixture.hold_unrelated_radio().await;
    let scan = sqlx::query(
        "SELECT user_id FROM t_radiotelephone_train WHERE active_session_id=? LIMIT 1 FOR UPDATE",
    )
    .bind("no-such-session")
    .fetch_optional(&fixture.pool)
    .await;
    aggregate.rollback().await.unwrap();
    user.rollback().await.unwrap();
    fixture.cleanup().await;
    assert!(scan.is_err(), "the session scan must wait on the held rows");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn pause_with_an_unknown_session_answers_208_without_scanning_other_users() {
    let fixture = Fixture::new().await;
    fixture.radio().await;
    let (user, aggregate) = fixture.hold_unrelated_radio().await;
    let document = within(crate::domain::radiotelephone::radiotelephone::pause(
        State(fixture.state.clone()),
        Extension(fixture.user("zzz")),
        lifecycle("no-such-session"),
    ))
    .await;
    aggregate.rollback().await.unwrap();
    user.rollback().await.unwrap();
    fixture.cleanup().await;
    assert_eq!(document["code"], 208, "{document}");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn pause_with_another_users_session_answers_207_without_waiting_for_that_row() {
    let fixture = Fixture::new().await;
    let foreign = fixture.radio().await;
    let (user, aggregate) = fixture.hold_unrelated_radio().await;
    let document = within(crate::domain::radiotelephone::radiotelephone::pause(
        State(fixture.state.clone()),
        Extension(fixture.user("zzz")),
        lifecycle(&foreign),
    ))
    .await;
    aggregate.rollback().await.unwrap();
    user.rollback().await.unwrap();
    fixture.cleanup().await;
    assert_eq!(document["code"], 207, "{document}");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn list_without_a_body_does_not_wait_for_a_locked_row() {
    let fixture = Fixture::new().await;
    fixture.radio().await;
    // Another connection holds the caller's own aggregate row; a pure read must not queue on it.
    let hold = fixture
        .hold("t_radiotelephone_train", &[fixture.id("zzz", "agg")])
        .await;
    let document = within(crate::domain::radiotelephone::radiotelephone::list(
        State(fixture.state.clone()),
        Extension(fixture.user("zzz")),
        axum::body::Bytes::new(),
    ))
    .await;
    hold.rollback().await.unwrap();
    fixture.cleanup().await;
    assert_eq!(document["code"], 200, "{document}");
    assert_eq!(document["data"].as_array().map(Vec::len), Some(1));
}

// ---------- military training (t_post_military_term_train[_test_paper]) ----------

impl Fixture {
    async fn military(&self, started: bool) {
        for side in ["aaa", "zzz"] {
            self.exec(
                "INSERT INTO t_user (id,user_name) VALUES (?,'military regression')",
                &[&self.id(side, "user")],
            )
            .await;
            let start = if started {
                "DATE_SUB(NOW(),INTERVAL 10 SECOND)"
            } else {
                "NULL"
            };
            self.exec(
                &format!("INSERT INTO t_post_military_term_train (id,user_id,types,name,status,total_number,correct_number,error_number,accuracy,duration,score,create_time,start_time) VALUES (?,?,'[]','m',{},3,0,0,0,0,0,NOW(),{start})", u8::from(started)),
                &[&self.id(side, "train"), &self.id(side, "user")],
            )
            .await;
            for name in ["p1", "p2", "p3"] {
                self.exec(
                    "INSERT INTO t_post_military_term_train_test_paper (id,train_id,title,`option`,correct_answer,user_answer) VALUES (?,?,'t','{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}','A',NULL)",
                    &[&self.id(side, name), &self.id(side, "train")],
                )
                .await;
            }
        }
    }

    async fn hold_unrelated_military(
        &self,
    ) -> (Transaction<'static, MySql>, Transaction<'static, MySql>) {
        (
            self.hold("t_post_military_term_train", &[self.id("aaa", "train")])
                .await,
            self.hold(
                "t_post_military_term_train_test_paper",
                &["p1", "p2", "p3"].map(|name| self.id("aaa", name)),
            )
            .await,
        )
    }

    async fn papers(&self, side: &str) -> i64 {
        sqlx::query_scalar(
            "SELECT COUNT(*) FROM t_post_military_term_train_test_paper WHERE train_id=?",
        )
        .bind(self.id(side, "train"))
        .fetch_one(&self.pool)
        .await
        .unwrap()
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn the_held_unrelated_papers_really_block_a_train_id_scan() {
    let fixture = Fixture::new().await;
    fixture.military(false).await;
    let (train, papers) = fixture.hold_unrelated_military().await;
    let scan = sqlx::query(
        "SELECT id FROM t_post_military_term_train_test_paper WHERE train_id=? FOR UPDATE",
    )
    .bind(fixture.id("zzz", "train"))
    .fetch_all(&fixture.pool)
    .await;
    papers.rollback().await.unwrap();
    train.rollback().await.unwrap();
    fixture.cleanup().await;
    assert!(
        scan.is_err(),
        "the train_id scan must wait on the held rows"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn military_delete_removes_only_its_own_papers_without_scanning_other_trains() {
    let fixture = Fixture::new().await;
    fixture.military(false).await;
    let (train, papers) = fixture.hold_unrelated_military().await;
    let document = within(crate::domain::radiotelephone::post_military::delete(
        State(fixture.state.clone()),
        Extension(fixture.user("zzz")),
        Query(serde_json::from_value(json!({"trainId": fixture.id("zzz", "train")})).unwrap()),
    ))
    .await;
    papers.rollback().await.unwrap();
    train.rollback().await.unwrap();
    let (own, other) = (fixture.papers("zzz").await, fixture.papers("aaa").await);
    let train_left: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM t_post_military_term_train WHERE id=?")
            .bind(fixture.id("zzz", "train"))
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
    fixture.cleanup().await;
    assert_eq!(document["code"], 200, "{document}");
    assert_eq!((own, other, train_left), (0, 3, 0));
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn military_finish_rounds_before_multiplying_and_does_not_scan_other_trains() {
    let fixture = Fixture::new().await;
    fixture.military(true).await;
    let (train, papers) = fixture.hold_unrelated_military().await;
    let document = within(crate::domain::radiotelephone::post_military::finish(
        State(fixture.state.clone()),
        Extension(fixture.user("zzz")),
        Json(json!({"id": fixture.id("zzz", "train"), "testPaperList": [
            {"id": fixture.id("zzz", "p1"), "userAnswer": "A"},
            {"id": fixture.id("zzz", "p2"), "userAnswer": "A"},
            {"id": fixture.id("zzz", "p3"), "userAnswer": "B"}]})),
    ))
    .await;
    papers.rollback().await.unwrap();
    train.rollback().await.unwrap();
    let stored = fixture
        .scalar(
            "SELECT CONCAT(accuracy,'/',score) FROM t_post_military_term_train WHERE id=?",
            &fixture.id("zzz", "train"),
        )
        .await;
    fixture.cleanup().await;
    assert_eq!(document["code"], 200, "{document}");
    // Java: round(2/3,2)*100 and round(2/3,3)*100, not round(2/3*100, 2).
    assert_eq!(stored, "67.00/66.70");
    assert_eq!(document["data"]["accuracy"], json!(67.0));
    assert_eq!(document["data"]["score"], json!(66.7));
}

// ---------- military term data tree (t_military_term_data) ----------

impl Fixture {
    async fn tree(&self) {
        for side in ["aaa", "zzz"] {
            self.exec(
                "INSERT INTO t_military_term_data (id,parent_id,`key`,value,sort) VALUES (?,'0','root',NULL,1)",
                &[&self.id(side, "root")],
            )
            .await;
            for (sort, name) in ["c1", "c2", "c3"].iter().enumerate() {
                self.exec(
                    &format!("INSERT INTO t_military_term_data (id,parent_id,`key`,value,sort) VALUES (?,?,?,?,{sort})"),
                    &[
                        &self.id(side, name),
                        &self.id(side, "root"),
                        &format!("{side}-{name}"),
                        &format!("{side}-{name}-value-{}", self.tag),
                    ],
                )
                .await;
            }
        }
    }

    async fn hold_unrelated_tree(&self) -> Transaction<'static, MySql> {
        self.hold(
            "t_military_term_data",
            &["root", "c1", "c2", "c3"].map(|name| self.id("aaa", name)),
        )
        .await
    }

    async fn sorts(&self, side: &str) -> Vec<(String, i32)> {
        sqlx::query_as::<_, (String, i32)>(
            "SELECT `key`,sort FROM t_military_term_data WHERE parent_id=? ORDER BY sort",
        )
        .bind(self.id(side, "root"))
        .fetch_all(&self.pool)
        .await
        .unwrap()
    }
}

fn term(
    id: String,
) -> Result<
    Json<crate::domain::radiotelephone::military_term_data::Term>,
    axum::extract::rejection::JsonRejection,
> {
    Ok(Json(serde_json::from_value(json!({"id": id})).unwrap()))
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn mtd_delete_shifts_only_its_siblings_without_scanning_other_parents() {
    let fixture = Fixture::new().await;
    fixture.tree().await;
    let hold = fixture.hold_unrelated_tree().await;
    let document = within(crate::domain::radiotelephone::military_term_data::delete(
        State(fixture.state.clone()),
        term(fixture.id("zzz", "c1")),
    ))
    .await;
    hold.rollback().await.unwrap();
    let (own, other) = (fixture.sorts("zzz").await, fixture.sorts("aaa").await);
    fixture.cleanup().await;
    assert_eq!(document["code"], 200, "{document}");
    assert_eq!(
        own,
        vec![("zzz-c2".to_owned(), 0), ("zzz-c3".to_owned(), 1)]
    );
    assert_eq!(other.len(), 3);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn mtd_move_reorders_only_its_siblings_without_scanning_other_parents() {
    let fixture = Fixture::new().await;
    fixture.tree().await;
    let hold = fixture.hold_unrelated_tree().await;
    let body = Json(
        serde_json::from_value(
            json!({"sourceId": fixture.id("zzz", "c3"), "targetId": fixture.id("zzz", "c1")}),
        )
        .unwrap(),
    );
    let document = within(
        crate::domain::radiotelephone::military_term_data::move_term(
            State(fixture.state.clone()),
            Ok(body),
        ),
    )
    .await;
    hold.rollback().await.unwrap();
    let own = fixture.sorts("zzz").await;
    fixture.cleanup().await;
    assert_eq!(document["code"], 200, "{document}");
    assert_eq!(
        own,
        vec![
            ("zzz-c3".to_owned(), 0),
            ("zzz-c1".to_owned(), 1),
            ("zzz-c2".to_owned(), 2)
        ]
    );
}
