use super::*;
use std::{sync::Arc, time::Duration};

struct Fixture {
    state: AppState,
    user: AuthUser,
    admin: AuthUser,
    exam_id: String,
    candidate_id: String,
    role_id: String,
    assignment_id: String,
}

impl Fixture {
    async fn new(prefix: &str, exam_state: i32, self_testing: i32) -> Self {
        let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("isolated scratch database URL");
        assert!(url.ends_with("/rust_scratch_lockdemo"));
        let pool = sqlx::mysql::MySqlPoolOptions::new()
            .max_connections(8)
            .connect(&url)
            .await
            .unwrap();
        let user = AuthUser {
            id: uuid::Uuid::new_v4().to_string(),
            is_admin: false,
        };
        let admin = AuthUser {
            id: uuid::Uuid::new_v4().to_string(),
            is_admin: true,
        };
        for actor in [&user, &admin] {
            sqlx::query("INSERT INTO t_user (id,user_account,user_name,status) VALUES (?,?,?,0)")
                .bind(&actor.id)
                .bind(&actor.id)
                .bind("exam writer regression")
                .execute(&pool)
                .await
                .unwrap();
        }
        let role_id = uuid::Uuid::new_v4().to_string();
        let assignment_id = uuid::Uuid::new_v4().to_string();
        sqlx::query("INSERT INTO t_role (id,is_admin) VALUES (?,0)")
            .bind(&role_id)
            .execute(&pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO t_user_role (id,user_id,role_id) VALUES (?,?,?)")
            .bind(&assignment_id)
            .bind(&admin.id)
            .bind(&role_id)
            .execute(&pool)
            .await
            .unwrap();
        let exam_id = uuid::Uuid::new_v4().to_string();
        let candidate_id = format!("{prefix}-{}", uuid::Uuid::new_v4());
        sqlx::query("INSERT INTO t_theory_knowledge_exam (id,title,create_user_id,state) VALUES (?,'writer regression',?,?)")
            .bind(&exam_id).bind(&user.id).bind(exam_state).execute(&pool).await.unwrap();
        sqlx::query("INSERT INTO t_theory_knowledge_exam_user (id,exam_id,user_id,state,score,is_self_testing) VALUES (?,?,?,1,0,?)")
            .bind(&candidate_id).bind(&exam_id).bind(&user.id).bind(self_testing).execute(&pool).await.unwrap();
        sqlx::query("INSERT INTO t_theory_knowledge_exam_test_paper (id,exam_id,single_choice_list,multiple_choice_list,judge_list,completion_list,short_answer) VALUES (?,?,'[]','[]','[]','[]','[]')")
            .bind(uuid::Uuid::new_v4().to_string()).bind(&exam_id).execute(&pool).await.unwrap();
        let state = AppState {
            config: Arc::new(crate::AppConfig::from_env().unwrap()),
            pool: Some(pool),
            database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
        };
        Self {
            state,
            user,
            admin,
            exam_id,
            candidate_id,
            role_id,
            assignment_id,
        }
    }

    fn pool(&self) -> &MySqlPool {
        self.state.pool.as_ref().unwrap()
    }

    fn formal_body(&self) -> Value {
        json!({"id":self.exam_id,"title":"rebuilt","duration":"30","stuId":[self.user.id],
            "testPaper":{"name":"rebuilt","total":0,"passMark":0,"singleChoice":[],"multipleChoice":[],"judge":[],"completion":[],"shortAnswer":[]}})
    }

    async fn cleanup(self) {
        let mut transaction = self.pool().begin().await.unwrap();
        delete_exam_children(&mut transaction, &self.exam_id)
            .await
            .unwrap();
        sqlx::query("DELETE FROM t_theory_knowledge_exam WHERE id=?")
            .bind(&self.exam_id)
            .execute(&mut *transaction)
            .await
            .unwrap();
        for (table, id) in [
            ("t_user_role", &self.assignment_id),
            ("t_role", &self.role_id),
            ("t_user", &self.user.id),
            ("t_user", &self.admin.id),
        ] {
            sqlx::query(&format!("DELETE FROM {table} WHERE id=?"))
                .bind(id)
                .execute(&mut *transaction)
                .await
                .unwrap();
        }
        transaction.commit().await.unwrap();
    }
}

async fn response_value(response: Response) -> Value {
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    serde_json::from_slice(&bytes).unwrap()
}

async fn gate_connection(transaction: &mut Transaction<'_, MySql>) -> u64 {
    sqlx::query_scalar("SELECT CONNECTION_ID()")
        .fetch_one(&mut **transaction)
        .await
        .unwrap()
}

async fn wait_for_gate(pool: &MySqlPool, connection: u64, table: &str, count: i64) -> bool {
    tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let waiting: i64 = sqlx::query_scalar("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME=?")
                .bind(connection).bind(table).fetch_one(pool).await.unwrap();
            if waiting == count { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok()
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
async fn two_admins_create_generated_exams_without_gap_upgrade() {
    {
        let first = Fixture::new("creation-a", 1, 1).await;
        let second = Fixture::new("creation-b", 1, 1).await;
        let mut gate = first.pool().begin().await.unwrap();
        let connection = gate_connection(&mut gate).await;
        // Cover every insertion gap, including the supremum. Both writers must
        // reach their parent INSERT before releasing the gate. The old locking
        // misses own compatible gap locks and deadlock on their INSERT upgrades.
        sqlx::query("SELECT id FROM t_theory_knowledge_exam WHERE id >= '' FOR UPDATE")
            .fetch_all(&mut *gate)
            .await
            .unwrap();
        let mut writers = Vec::new();
        for fixture in [&first, &second] {
            let state = fixture.state.clone();
            let admin = fixture.admin.clone();
            let mut body = fixture.formal_body();
            body.as_object_mut().unwrap().remove("id");
            writers.push(tokio::spawn(async move {
                save_formal(State(state), Extension(admin), Json(body)).await
            }));
        }
        let waiting = wait_for_gate(first.pool(), connection, "t_theory_knowledge_exam", 2).await;
        gate.commit().await.unwrap();
        let mut results = Vec::new();
        for writer in writers {
            results.push(
                response_value(
                    tokio::time::timeout(Duration::from_secs(5), writer)
                        .await
                        .expect("creation must not deadlock")
                        .unwrap(),
                )
                .await,
            );
        }
        for fixture in [&first, &second] {
            let ids = sqlx::query_scalar::<_, String>(
                "SELECT id FROM t_theory_knowledge_exam WHERE create_user_id=?",
            )
            .bind(&fixture.admin.id)
            .fetch_all(fixture.pool())
            .await
            .unwrap();
            assert_eq!(ids.len(), 1, "each administrator creates exactly one exam");
            for id in ids {
                let mut transaction = fixture.pool().begin().await.unwrap();
                delete_exam_children(&mut transaction, &id).await.unwrap();
                sqlx::query("DELETE FROM t_theory_knowledge_exam WHERE id=?")
                    .bind(id)
                    .execute(&mut *transaction)
                    .await
                    .unwrap();
                transaction.commit().await.unwrap();
            }
        }
        first.cleanup().await;
        second.cleanup().await;
        assert!(waiting, "both parent inserts must reach the gap gate");
        for result in results {
            assert_eq!(result["code"], 200, "{result}");
        }
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn supplied_absent_exam_is_not_resurrected() {
    let fixture = Fixture::new("stale-editor", 1, 1).await;
    let body = fixture.formal_body();
    let deletion = response_value(
        delete_exam(
            State(fixture.state.clone()),
            Extension(fixture.admin.clone()),
            Json(json!({"examId":fixture.exam_id})),
        )
        .await,
    )
    .await;
    assert_eq!(deletion["code"], 200);
    let result = response_value(
        save_formal(
            State(fixture.state.clone()),
            Extension(fixture.admin.clone()),
            Json(body),
        )
        .await,
    )
    .await;
    assert_eq!(result["code"], 202, "{result}");
    assert_eq!(result["message"], "未查询到考试");
    for table in [
        "t_theory_knowledge_exam",
        "t_theory_knowledge_exam_user",
        "t_theory_knowledge_exam_test_paper",
    ] {
        let column = if table == "t_theory_knowledge_exam" {
            "id"
        } else {
            "exam_id"
        };
        let count: i64 =
            sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {table} WHERE {column}=?"))
                .bind(&fixture.exam_id)
                .fetch_one(fixture.pool())
                .await
                .unwrap();
        assert_eq!(count, 0, "stale save must not recreate {table}");
    }
    fixture.cleanup().await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
async fn delete_vs_finish_and_edit_wait_on_parent_without_child_locks() {
    for edit in [false, true] {
        let fixture = Fixture::new("race", 2, 0).await;
        let mut gate = fixture.pool().begin().await.unwrap();
        let connection = gate_connection(&mut gate).await;
        locked_exam(&mut gate, &fixture.exam_id).await.unwrap();
        let (state, admin, id) = (
            fixture.state.clone(),
            fixture.admin.clone(),
            fixture.exam_id.clone(),
        );
        let deletion = tokio::spawn(async move {
            delete_exam(State(state), Extension(admin), Json(json!({"examId":id}))).await
        });
        let (state, user, id) = (
            fixture.state.clone(),
            fixture.user.clone(),
            fixture.exam_id.clone(),
        );
        let writer = tokio::spawn(async move {
            if edit {
                self_test(
                    State(state),
                    Extension(user),
                    Json(json!({"id":id,"title":"edited","testPaper":{"singleChoice":[]}})),
                )
                .await
            } else {
                finish_self_test(
                    State(state),
                    Extension(user),
                    Json(json!({"examId":id,"content":"{}"})),
                )
                .await
            }
        });
        let waiting = wait_for_gate(fixture.pool(), connection, "t_theory_knowledge_exam", 2).await;
        let child_locks: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.data_locks l WHERE l.ENGINE_TRANSACTION_ID IN (SELECT w.REQUESTING_ENGINE_TRANSACTION_ID FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=?) AND l.OBJECT_NAME IN ('t_theory_knowledge_exam_user','t_theory_knowledge_exam_test_paper') AND l.LOCK_TYPE='RECORD' AND l.LOCK_STATUS='GRANTED'")
            .bind(connection).fetch_one(fixture.pool()).await.unwrap();
        gate.commit().await.unwrap();
        let (deleted, written) = tokio::time::timeout(Duration::from_secs(5), async {
            tokio::join!(deletion, writer)
        })
        .await
        .expect("delete and writer must not deadlock");
        let deleted = response_value(deleted.unwrap()).await;
        let written = response_value(written.unwrap()).await;
        let mut remaining = Vec::new();
        for (table, column) in [
            ("t_theory_knowledge_exam", "id"),
            ("t_theory_knowledge_exam_user", "exam_id"),
            ("t_theory_knowledge_exam_test_paper", "exam_id"),
        ] {
            remaining.push(
                sqlx::query_scalar::<_, i64>(&format!(
                    "SELECT COUNT(*) FROM {table} WHERE {column}=?"
                ))
                .bind(&fixture.exam_id)
                .fetch_one(fixture.pool())
                .await
                .unwrap(),
            );
        }
        fixture.cleanup().await;
        assert!(waiting, "both writers must wait on the parent PK");
        assert_eq!(child_locks, 0, "no child lock may precede the parent lock");
        assert_eq!(deleted["code"], 200, "{deleted}");
        assert!(
            matches!(written["code"].as_i64(), Some(200 | 202)),
            "{written}"
        );
        assert_eq!(
            remaining,
            [0, 0, 0],
            "delete must leave no orphan or resurrected answer"
        );
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
async fn unrelated_exam_candidates_stay_writable_during_each_writer() {
    for operation in ["end", "grade", "rebuild", "delete"] {
        let target = Fixture::new("zzz", if operation == "end" { 2 } else { 1 }, 1).await;
        let unrelated = Fixture::new("aaa", 1, 1).await;
        let mut gate = target.pool().begin().await.unwrap();
        let connection = gate_connection(&mut gate).await;
        sqlx::query("SELECT id FROM t_theory_knowledge_exam_user WHERE id=? FOR UPDATE")
            .bind(&target.candidate_id)
            .fetch_one(&mut *gate)
            .await
            .unwrap();
        let (state, admin, id, uid, body) = (
            target.state.clone(),
            target.admin.clone(),
            target.exam_id.clone(),
            target.user.id.clone(),
            target.formal_body(),
        );
        let writer = tokio::spawn(async move {
            match operation {
                "end" => {
                    teacher_start(
                        State(state),
                        Extension(admin),
                        Json(json!({"examId":id,"type":"3"})),
                    )
                    .await
                }
                "grade" => {
                    teacher_upload_score(
                        State(state),
                        Extension(admin),
                        Json(json!({"examId":id,"list":[{"user_id":uid,"score":10,"content":{}}]})),
                    )
                    .await
                }
                "rebuild" => save_formal(State(state), Extension(admin), Json(body)).await,
                "delete" => {
                    delete_exam(State(state), Extension(admin), Json(json!({"examId":id}))).await
                }
                _ => unreachable!(),
            }
        });
        let waiting =
            wait_for_gate(target.pool(), connection, "t_theory_knowledge_exam_user", 1).await;
        let parent_locks: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM performance_schema.data_locks l WHERE l.ENGINE_TRANSACTION_ID IN (SELECT w.REQUESTING_ENGINE_TRANSACTION_ID FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=?) AND l.OBJECT_NAME='t_theory_knowledge_exam' AND l.LOCK_TYPE='RECORD' AND l.LOCK_STATUS='GRANTED' AND l.LOCK_MODE LIKE 'X%'")
            .bind(connection).fetch_one(target.pool()).await.unwrap();
        let mut other = unrelated.pool().begin().await.unwrap();
        let writable = matches!(
            tokio::time::timeout(
                Duration::from_millis(800),
                sqlx::query("UPDATE t_theory_knowledge_exam_user SET score=7 WHERE id=?")
                    .bind(&unrelated.candidate_id)
                    .execute(&mut *other),
            )
            .await,
            Ok(Ok(_))
        );
        gate.commit().await.unwrap();
        other.rollback().await.unwrap();
        let response = tokio::time::timeout(Duration::from_secs(5), writer)
            .await
            .expect("writer must complete");
        let response = response_value(response.unwrap()).await;
        target.cleanup().await;
        unrelated.cleanup().await;
        assert!(waiting, "{operation} must reach target candidate gate");
        assert_eq!(
            parent_locks, 1,
            "{operation} must hold its parent PK before waiting for a child"
        );
        assert!(
            writable,
            "{operation} must not lock another exam's scanned candidates"
        );
        assert_eq!(response["code"], 200, "{operation}: {response}");
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and MySQL performance_schema lock tables"]
async fn formal_rebuild_checks_latest_answers_after_parent_lock() {
    let fixture = Fixture::new("guard", 1, 1).await;
    let mut gate = fixture.pool().begin().await.unwrap();
    let connection = gate_connection(&mut gate).await;
    locked_exam(&mut gate, &fixture.exam_id).await.unwrap();
    let (state, admin, body) = (
        fixture.state.clone(),
        fixture.admin.clone(),
        fixture.formal_body(),
    );
    let writer =
        tokio::spawn(async move { save_formal(State(state), Extension(admin), Json(body)).await });
    let waiting = wait_for_gate(fixture.pool(), connection, "t_theory_knowledge_exam", 1).await;
    sqlx::query("UPDATE t_theory_knowledge_exam_user SET content='{}' WHERE id=?")
        .bind(&fixture.candidate_id)
        .execute(&mut *gate)
        .await
        .unwrap();
    gate.commit().await.unwrap();
    let response = response_value(
        tokio::time::timeout(Duration::from_secs(5), writer)
            .await
            .unwrap()
            .unwrap(),
    )
    .await;
    let title: String = sqlx::query_scalar("SELECT title FROM t_theory_knowledge_exam WHERE id=?")
        .bind(&fixture.exam_id)
        .fetch_one(fixture.pool())
        .await
        .unwrap();
    let content: Option<String> =
        sqlx::query_scalar("SELECT content FROM t_theory_knowledge_exam_user WHERE id=?")
            .bind(&fixture.candidate_id)
            .fetch_optional(fixture.pool())
            .await
            .unwrap()
            .flatten();
    fixture.cleanup().await;
    assert!(waiting, "rebuild must first wait on the parent PK");
    assert_eq!(response["code"], 202, "{response}");
    assert_eq!(title, "writer regression");
    assert_eq!(content.as_deref(), Some("{}"));
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and permission to create failure-injection triggers"]
async fn child_delete_failures_roll_back_delete_and_formal_rebuild() {
    for rebuild in [false, true] {
        let fixture = Fixture::new("failure", 1, 1).await;
        let trigger = format!("exam_delete_failure_{}", uuid::Uuid::new_v4().simple());
        sqlx::raw_sql(&format!("CREATE TRIGGER {trigger} BEFORE DELETE ON t_theory_knowledge_exam_user FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='exam child delete injection'"))
            .execute(fixture.pool()).await.unwrap();
        let response = if rebuild {
            save_formal(
                State(fixture.state.clone()),
                Extension(fixture.admin.clone()),
                Json(fixture.formal_body()),
            )
            .await
        } else {
            delete_exam(
                State(fixture.state.clone()),
                Extension(fixture.admin.clone()),
                Json(json!({"examId":fixture.exam_id})),
            )
            .await
        };
        sqlx::raw_sql(&format!("DROP TRIGGER {trigger}"))
            .execute(fixture.pool())
            .await
            .unwrap();
        let response = response_value(response).await;
        let title: Option<String> =
            sqlx::query_scalar("SELECT title FROM t_theory_knowledge_exam WHERE id=?")
                .bind(&fixture.exam_id)
                .fetch_optional(fixture.pool())
                .await
                .unwrap()
                .flatten();
        let mut counts = Vec::new();
        for table in [
            "t_theory_knowledge_exam_test_paper",
            "t_theory_knowledge_exam_user",
        ] {
            counts.push(
                sqlx::query_scalar::<_, i64>(&format!(
                    "SELECT COUNT(*) FROM {table} WHERE exam_id=?"
                ))
                .bind(&fixture.exam_id)
                .fetch_one(fixture.pool())
                .await
                .unwrap(),
            );
        }
        fixture.cleanup().await;
        assert_eq!(response["code"], 500, "{response}");
        assert_eq!(title.as_deref(), Some("writer regression"));
        assert_eq!(
            counts,
            [1, 1],
            "child-delete failure must not partially commit"
        );
    }
}
