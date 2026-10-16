use super::*;
use sqlx::{MySql, MySqlPool, Transaction};
use std::{sync::Arc, time::Duration};

const RULE: &str = r#"{"rateUnit":"CHARACTERS_PER_MINUTE","wpm":{"base":480,"r":0,"l":1},"other":{"errorCode":5,"muchLessCode":2,"muchLessLine":3,"muchLessGroups":2,"correctMistakes":1,"lessPage":1,"lessReturnLine":1,"errorPage":1,"nonStandart":1}}"#;

struct Fixture {
    state: AppState,
    pool: MySqlPool,
    owner: AuthUser,
    target: String,
    unrelated: String,
    rule: String,
}

impl Fixture {
    async fn new() -> Self {
        let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("isolated scratch database URL");
        assert!(url.ends_with("/rust_scratch_lockdemo"));
        let pool = sqlx::mysql::MySqlPoolOptions::new()
            .max_connections(12)
            .acquire_timeout(Duration::from_secs(2))
            .connect(&url)
            .await
            .unwrap();
        let suffix = uuid::Uuid::new_v4();
        let owner = AuthUser {
            id: format!("telex-owner-{suffix}"),
            is_admin: false,
        };
        let target = format!("zzz-telex-{suffix}");
        let unrelated = format!("aaa-telex-{suffix}");
        let rule = format!("telex-rule-{suffix}");
        sqlx::query("INSERT INTO t_user (id,user_name) VALUES (?,'telex regression')")
            .bind(&owner.id)
            .execute(&pool)
            .await
            .unwrap();
        sqlx::query("INSERT INTO t_grading_rule (id,type,title,score,content) VALUES (?,2,'telex regression',100,?)")
            .bind(&rule).bind(RULE).execute(&pool).await.unwrap();
        for id in [&target, &unrelated] {
            sqlx::query("INSERT INTO t_post_telex_pat_train (id,name,is_cable,type,train_type,group_number,pat_type,rule_id,rule_content,create_user,status,protocol_version,attempt,score,accuracy,speed,total_speed,valid_time,error_number,pause_intervals,`change`,start_time,create_time) VALUES (?,'telex regression',1,0,0,100,0,?,?,?,1,1,0,'100','100.00','480','480',1,0,'[]',0,DATE_SUB(NOW(3),INTERVAL 20 SECOND),NOW(3))")
                .bind(id).bind(&rule).bind(RULE).bind(&owner.id).execute(&pool).await.unwrap();
            for (sort, key) in [(0, "1234"), (1, "5678")] {
                sqlx::query("INSERT INTO t_post_telex_pat_train_page (id,train_id,`key`,value,page_number,sort) VALUES (?,?,?,'confirmed',1,?)")
                    .bind(format!("{id}-page-{sort}")).bind(id).bind(key).bind(sort).execute(&pool).await.unwrap();
            }
            sqlx::query("INSERT INTO t_post_telex_pat_train_page_value (id,train_id,page_number,pat_value,capture_intervals,attempt,received_at) VALUES (?,?,1,'1234 5678','[{\"startedMs\":0,\"endedMs\":1000}]',0,NOW(3))")
                .bind(format!("{id}-raw-0")).bind(id).execute(&pool).await.unwrap();
        }
        let state = AppState {
            config: Arc::new(crate::platform::config::AppConfig::from_env().unwrap()),
            pool: Some(pool.clone()),
            database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
        };
        Self {
            state,
            pool,
            owner,
            target,
            unrelated,
            rule,
        }
    }

    async fn cleanup(self) {
        let ids: Vec<String> = sqlx::query_scalar(
            "SELECT id FROM t_post_telex_pat_train WHERE create_user=? ORDER BY id",
        )
        .bind(&self.owner.id)
        .fetch_all(&self.pool)
        .await
        .unwrap();
        for id in ids {
            let mut tx = self.pool.begin().await.unwrap();
            sqlx::query("SELECT id FROM t_post_telex_pat_train WHERE id=? FOR UPDATE")
                .bind(&id)
                .fetch_one(&mut *tx)
                .await
                .unwrap();
            mutate_children(&mut tx, &id, true).await.unwrap();
            sqlx::query("DELETE FROM t_post_telex_pat_train WHERE id=?")
                .bind(id)
                .execute(&mut *tx)
                .await
                .unwrap();
            tx.commit().await.unwrap();
        }
        sqlx::query("DELETE FROM t_grading_rule WHERE id=?")
            .bind(&self.rule)
            .execute(&self.pool)
            .await
            .unwrap();
        sqlx::query("DELETE FROM t_user WHERE id=?")
            .bind(&self.owner.id)
            .execute(&self.pool)
            .await
            .unwrap();
    }
}

async fn document(response: Response) -> Value {
    response_document(response)
        .await
        .expect("valid telex response")
}

async fn response_document(response: Response) -> Result<Value, String> {
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .map_err(|error| format!("telex response body failed: {error}"))?;
    serde_json::from_slice(&bytes).map_err(|error| format!("invalid telex response JSON: {error}"))
}

async fn invoke(
    state: AppState,
    owner: AuthUser,
    id: String,
    attempt: i32,
    op: &'static str,
) -> Response {
    if op == "delete" {
        return delete(
            State(state),
            Extension(owner),
            Query(serde_json::from_value(json!({"trainId":id})).unwrap()),
        )
        .await;
    }
    if op == "capture" {
        return crate::post_telex::finish_page(State(state), Extension(owner), Json(json!({
            "trainId":id,"pageNumber":1,"attempt":attempt,"patValue":"1234 5678 9999",
            "captureIntervals":[{"startedMs":0,"endedMs":1000},{"startedMs":1000,"endedMs":2000}]
        }))).await;
    }
    let body = serde_json::from_value(json!({"id":id,"attempt":attempt})).unwrap();
    action(
        state,
        owner,
        body,
        match op {
            "reset" => Action::Reset,
            "finish" => Action::Finish,
            "begin" => Action::Begin,
            "pause" => Action::Pause,
            "resume" => Action::Resume,
            _ => panic!("unknown telex operation"),
        },
    )
    .await
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
            let writers: i64 = sqlx::query_scalar("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME=?")
                .bind(connection).bind(table).fetch_one(pool).await.unwrap();
            if writers >= count { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok()
}

async fn joined(mut writer: tokio::task::JoinHandle<Response>) -> Result<Value, String> {
    let response = match tokio::time::timeout(Duration::from_secs(5), &mut writer).await {
        Ok(result) => result.map_err(|error| format!("telex handler task failed: {error}"))?,
        Err(_) => {
            writer.abort();
            let _ = writer.await;
            return Err("telex handler did not finish within five seconds".into());
        }
    };
    response_document(response).await
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn reset_delete_leave_unrelated_train_and_children_writable() {
    for op in ["reset", "delete"] {
        for (table, suffix) in [
            ("t_post_telex_pat_train_page", "page-1"),
            ("t_post_telex_pat_train_page_value", "raw-0"),
        ] {
            let fixture = Fixture::new().await;
            let (gate, connection) = gate(
                &fixture.pool,
                table,
                &format!("{}-{suffix}", fixture.target),
            )
            .await;
            let (state, owner, id) = (
                fixture.state.clone(),
                fixture.owner.clone(),
                fixture.target.to_uppercase(),
            );
            let writer = tokio::spawn(async move { invoke(state, owner, id, 0, op).await });
            let reached = waiting(&fixture.pool, connection, table, 1).await;
            let mut other = fixture.pool.begin().await.unwrap();
            sqlx::query("SET innodb_lock_wait_timeout=1")
                .execute(&mut *other)
                .await
                .unwrap();
            let mut writable = true;
            for (table, id) in [
                ("t_post_telex_pat_train", fixture.unrelated.clone()),
                (
                    "t_post_telex_pat_train_page",
                    format!("{}-page-0", fixture.unrelated),
                ),
                (
                    "t_post_telex_pat_train_page_value",
                    format!("{}-raw-0", fixture.unrelated),
                ),
            ] {
                writable &= sqlx::query(&format!("UPDATE {table} SET id=id WHERE id=?"))
                    .bind(id)
                    .execute(&mut *other)
                    .await
                    .is_ok();
            }
            other.rollback().await.unwrap();
            gate.commit().await.unwrap();
            let reply = joined(writer).await;
            let parent =
                sqlx::query("SELECT status,attempt FROM t_post_telex_pat_train WHERE id=?")
                    .bind(&fixture.target)
                    .fetch_optional(&fixture.pool)
                    .await
                    .unwrap();
            let raw: i64 = sqlx::query_scalar(
                "SELECT COUNT(*) FROM t_post_telex_pat_train_page_value WHERE train_id=?",
            )
            .bind(&fixture.target)
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
            let pages: i64 = sqlx::query_scalar(
                "SELECT COUNT(*) FROM t_post_telex_pat_train_page WHERE train_id=?",
            )
            .bind(&fixture.target)
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
            let uncleared: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_post_telex_pat_train_page WHERE train_id=? AND value IS NOT NULL")
                .bind(&fixture.target).fetch_one(&fixture.pool).await.unwrap();
            fixture.cleanup().await;
            let reply = reply.expect("telex writer must finish");
            assert!(reached, "{op} never reached {table} PK gate");
            assert!(
                writable,
                "{op} scan locked unrelated train/children at {table}"
            );
            assert_eq!(reply["code"], 200, "{reply}");
            assert_eq!(raw, 0);
            if op == "reset" {
                let parent = parent.unwrap();
                assert_eq!(parent.get::<Option<i32>, _>("status"), Some(0));
                assert_eq!(parent.get::<i32, _>("attempt"), 1);
                assert_eq!(pages, 2);
                assert_eq!(uncleared, 0);
            } else {
                assert!(parent.is_none());
                assert_eq!(pages, 0);
            }
        }
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn queued_reset_rejects_every_stale_attempt_then_accepts_current_capture() {
    let fixture = Fixture::new().await;
    let table = "t_post_telex_pat_train";
    let (gate, connection) = gate(&fixture.pool, table, &fixture.target).await;
    let (state, owner, id) = (
        fixture.state.clone(),
        fixture.owner.clone(),
        fixture.target.to_uppercase(),
    );
    let first = tokio::spawn(async move { invoke(state, owner, id, 0, "reset").await });
    let mut reached = waiting(&fixture.pool, connection, table, 1).await;
    let mut stale = Vec::new();
    for (index, op) in ["capture", "finish", "reset", "pause", "resume", "begin"]
        .into_iter()
        .enumerate()
    {
        let (state, owner, id) = (
            fixture.state.clone(),
            fixture.owner.clone(),
            fixture.target.to_uppercase(),
        );
        stale.push(tokio::spawn(async move {
            invoke(state, owner, id, 0, op).await
        }));
        reached &= waiting(&fixture.pool, connection, table, index as i64 + 2).await;
    }
    gate.commit().await.unwrap();
    let reset = joined(first).await;
    let mut rejected = Vec::new();
    for writer in stale {
        rejected.push(joined(writer).await);
    }
    let begin = document(
        invoke(
            fixture.state.clone(),
            fixture.owner.clone(),
            fixture.target.clone(),
            1,
            "begin",
        )
        .await,
    )
    .await;
    sqlx::query("UPDATE t_post_telex_pat_train SET start_time=DATE_SUB(NOW(3),INTERVAL 20 SECOND) WHERE id=?")
        .bind(&fixture.target).execute(&fixture.pool).await.unwrap();
    let capture = document(crate::post_telex::finish_page(State(fixture.state.clone()), Extension(fixture.owner.clone()), Json(json!({
        "trainId":fixture.target.to_uppercase(),"pageNumber":1,"attempt":1,"patValue":"1234 5678",
        "captureIntervals":[{"startedMs":0,"endedMs":1000}]
    }))).await).await;
    let stored = sqlx::query(
        "SELECT train_id,attempt,pat_value FROM t_post_telex_pat_train_page_value WHERE train_id=?",
    )
    .bind(&fixture.target)
    .fetch_optional(&fixture.pool)
    .await
    .unwrap();
    let finish = document(
        invoke(
            fixture.state.clone(),
            fixture.owner.clone(),
            fixture.target.clone(),
            1,
            "finish",
        )
        .await,
    )
    .await;
    fixture.cleanup().await;
    let reset = reset.expect("queued reset must finish");
    let stored = stored.expect("current capture must exist");
    assert!(
        reached,
        "all lifecycle and capture requests must queue behind train PK"
    );
    assert_eq!(reset["code"], 200, "{reset}");
    for reply in rejected {
        let reply = reply.expect("queued stale handler must finish");
        assert_eq!(reply["code"], 208, "{reply}");
    }
    assert_eq!(begin["code"], 200, "{begin}");
    assert_eq!(capture["code"], 200, "{capture}");
    assert_eq!(
        stored.get::<String, _>("train_id"),
        reset["data"]["id"].as_str().unwrap()
    );
    assert_eq!(stored.get::<Option<i32>, _>("attempt"), Some(1));
    assert_eq!(
        stored.get::<Option<String>, _>("pat_value").as_deref(),
        Some("1234 5678")
    );
    assert_eq!(finish["code"], 200, "{finish}");
    assert_eq!(finish["data"]["score"], "100");
    assert_eq!(finish["data"]["speed"], "480");
    assert_eq!(finish["data"]["validTime"], 1);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn queued_finish_freezes_valid_score_and_rejects_reset_and_changed_capture() {
    let fixture = Fixture::new().await;
    let table = "t_post_telex_pat_train";
    let (gate, connection) = gate(&fixture.pool, table, &fixture.target).await;
    let (state, owner, id) = (
        fixture.state.clone(),
        fixture.owner.clone(),
        fixture.target.clone(),
    );
    let first = tokio::spawn(async move { invoke(state, owner, id, 0, "finish").await });
    let mut reached = waiting(&fixture.pool, connection, table, 1).await;
    let mut stale = Vec::new();
    for (index, op) in ["reset", "capture"].into_iter().enumerate() {
        let (state, owner, id) = (
            fixture.state.clone(),
            fixture.owner.clone(),
            fixture.target.clone(),
        );
        stale.push(tokio::spawn(async move {
            invoke(state, owner, id, 0, op).await
        }));
        reached &= waiting(&fixture.pool, connection, table, index as i64 + 2).await;
    }
    gate.commit().await.unwrap();
    let finished = joined(first).await;
    let mut replies = Vec::new();
    for writer in stale {
        replies.push(joined(writer).await);
    }
    let retry = document(
        invoke(
            fixture.state.clone(),
            fixture.owner.clone(),
            fixture.target.clone(),
            0,
            "finish",
        )
        .await,
    )
    .await;
    let exact = document(
        crate::post_telex::finish_page(
            State(fixture.state.clone()),
            Extension(fixture.owner.clone()),
            Json(json!({
                "trainId":fixture.target,"pageNumber":1,"attempt":0,"patValue":"1234 5678",
                "captureIntervals":[{"startedMs":0,"endedMs":1000}]
            })),
        )
        .await,
    )
    .await;
    let persisted = sqlx::query(
        "SELECT status,attempt,score,speed,valid_time FROM t_post_telex_pat_train WHERE id=?",
    )
    .bind(&fixture.target)
    .fetch_one(&fixture.pool)
    .await
    .unwrap();
    fixture.cleanup().await;
    let finished = finished.expect("queued finish must complete");
    assert!(reached);
    assert_eq!(finished["code"], 200, "{finished}");
    for reply in replies {
        let reply = reply.expect("queued terminal handler must finish");
        assert_eq!(reply["code"], 208, "{reply}");
    }
    assert_eq!(retry["code"], 200, "{retry}");
    assert_eq!(exact["code"], 200, "{exact}");
    assert_eq!(persisted.get::<Option<i32>, _>("status"), Some(3));
    assert_eq!(persisted.get::<i32, _>("attempt"), 0);
    assert_eq!(
        persisted.get::<Option<String>, _>("score").as_deref(),
        Some("100")
    );
    assert_eq!(
        persisted.get::<Option<String>, _>("speed").as_deref(),
        Some("480")
    );
    assert_eq!(persisted.get::<Option<i32>, _>("valid_time"), Some(1));
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and CREATE TRIGGER privilege"]
async fn child_delete_failure_rolls_back_reset_delete_and_attempt() {
    for op in ["reset", "delete"] {
        let fixture = Fixture::new().await;
        sqlx::query("INSERT INTO t_post_telex_pat_train_page_value (id,train_id,page_number,pat_value,capture_intervals,attempt,received_at) VALUES (?,?,2,'second','[]',0,NOW(3))")
            .bind(format!("{}-raw-1", fixture.target)).bind(&fixture.target).execute(&fixture.pool).await.unwrap();
        let trigger = format!("telex_delete_{}", uuid::Uuid::new_v4().simple());
        sqlx::raw_sql(&format!("CREATE TRIGGER {trigger} BEFORE DELETE ON t_post_telex_pat_train_page_value FOR EACH ROW BEGIN IF OLD.id='{}-raw-1' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='telex rollback regression'; END IF; END", fixture.target))
            .execute(&fixture.pool).await.unwrap();
        let (state, owner, id) = (
            fixture.state.clone(),
            fixture.owner.clone(),
            fixture.target.clone(),
        );
        let reply = joined(tokio::spawn(async move {
            invoke(state, owner, id, 0, op).await
        }))
        .await;
        sqlx::raw_sql(&format!("DROP TRIGGER {trigger}"))
            .execute(&fixture.pool)
            .await
            .unwrap();
        let mut tx = fixture.pool.begin().await.unwrap();
        let parent = sqlx::query(
            "SELECT status,attempt,score FROM t_post_telex_pat_train WHERE id=? FOR UPDATE",
        )
        .bind(&fixture.target)
        .fetch_one(&mut *tx)
        .await
        .unwrap();
        let pages: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_post_telex_pat_train_page WHERE train_id=? AND value='confirmed'")
            .bind(&fixture.target).fetch_one(&mut *tx).await.unwrap();
        let raw: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM t_post_telex_pat_train_page_value WHERE train_id=?",
        )
        .bind(&fixture.target)
        .fetch_one(&mut *tx)
        .await
        .unwrap();
        tx.commit().await.unwrap();
        fixture.cleanup().await;
        let reply = reply.expect("faulted delete handler must finish");
        assert_eq!(reply["code"], 500, "{reply}");
        assert_eq!(parent.get::<Option<i32>, _>("status"), Some(1));
        assert_eq!(parent.get::<i32, _>("attempt"), 0);
        assert_eq!(
            parent.get::<Option<String>, _>("score").as_deref(),
            Some("100")
        );
        assert_eq!(
            pages, 2,
            "{op} must restore previously cleared/deleted source children"
        );
        assert_eq!(
            raw, 2,
            "{op} must restore earlier successful child deletion"
        );
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and CREATE TRIGGER privilege"]
async fn lazy_finish_page_generation_failure_rolls_back_capture_and_totals() {
    let fixture = Fixture::new().await;
    sqlx::query("UPDATE t_post_telex_pat_train SET is_cable=0,group_number=201 WHERE id=?")
        .bind(&fixture.target)
        .execute(&fixture.pool)
        .await
        .unwrap();
    let trigger = format!("telex_generate_{}", uuid::Uuid::new_v4().simple());
    sqlx::raw_sql(&format!("CREATE TRIGGER {trigger} BEFORE INSERT ON t_post_telex_pat_train_page FOR EACH ROW BEGIN IF NEW.train_id='{}' AND NEW.page_number=3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='telex generation rollback regression'; END IF; END", fixture.target))
        .execute(&fixture.pool).await.unwrap();
    let (state, owner, id) = (
        fixture.state.clone(),
        fixture.owner.clone(),
        fixture.target.clone(),
    );
    let reply = joined(tokio::spawn(async move {
        crate::post_telex::finish_page(
            State(state),
            Extension(owner),
            Json(json!({
                "trainId":id,"pageNumber":3,"attempt":0,"patValue":"9999",
                "captureIntervals":[{"startedMs":2000,"endedMs":3000}]
            })),
        )
        .await
    }))
    .await;
    sqlx::raw_sql(&format!("DROP TRIGGER {trigger}"))
        .execute(&fixture.pool)
        .await
        .unwrap();
    let mut tx = fixture.pool.begin().await.unwrap();
    let parent = sqlx::query(
        "SELECT speed,total_speed,valid_time FROM t_post_telex_pat_train WHERE id=? FOR UPDATE",
    )
    .bind(&fixture.target)
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    let raw: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM t_post_telex_pat_train_page_value WHERE train_id=? AND page_number=3",
    )
    .bind(&fixture.target)
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    let pages: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM t_post_telex_pat_train_page WHERE train_id=? AND page_number=3",
    )
    .bind(&fixture.target)
    .fetch_one(&mut *tx)
    .await
    .unwrap();
    tx.commit().await.unwrap();
    fixture.cleanup().await;
    let reply = reply.expect("faulted lazy generation handler must finish");
    assert_eq!(reply["code"], 500, "{reply}");
    assert_eq!(raw, 0);
    assert_eq!(pages, 0);
    assert_eq!(
        parent.get::<Option<String>, _>("speed").as_deref(),
        Some("480")
    );
    assert_eq!(
        parent.get::<Option<String>, _>("total_speed").as_deref(),
        Some("480")
    );
    assert_eq!(parent.get::<Option<i32>, _>("valid_time"), Some(1));
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn fixed_cable_creation_reads_complete_snapshot_after_queued_rule_lock() {
    let fixture = Fixture::new().await;
    let type_id = uuid::Uuid::new_v4().to_string();
    let cable_id = uuid::Uuid::new_v4().to_string();
    let unrelated_cable = uuid::Uuid::new_v4().to_string();
    let unrelated_floor = uuid::Uuid::new_v4().to_string();
    sqlx::query("INSERT INTO t_cable_type (id,title) VALUES (?,'telex snapshot regression')")
        .bind(&type_id)
        .execute(&fixture.pool)
        .await
        .unwrap();
    let cable = |group: &str| json!({"id":cable_id,"typeId":type_id,"title":"telex snapshot regression","scope":1,"codeType":0,"codeSort":0,"floors":[[group.chars().map(|c| c.to_string()).collect::<Vec<_>>()],[group.chars().map(|c| c.to_string()).collect::<Vec<_>>()]]});
    let initial =
        document(crate::cable::save(State(fixture.state.clone()), Json(cable("1234"))).await).await;
    sqlx::query("INSERT INTO t_cable (id,title,type_id) VALUES (?,'unrelated',?)")
        .bind(&unrelated_cable)
        .bind(&type_id)
        .execute(&fixture.pool)
        .await
        .unwrap();
    sqlx::query("INSERT INTO t_cable_floor (id,cable_id,type_id,floor_number,sort,mores_key) VALUES (?,?,?,0,0,'[\"A\"]')")
        .bind(&unrelated_floor).bind(&unrelated_cable).bind(&type_id).execute(&fixture.pool).await.unwrap();
    let (gate, connection) = gate(&fixture.pool, "t_grading_rule", &fixture.rule).await;
    let (state, owner) = (fixture.state.clone(), fixture.owner.clone());
    let body = serde_json::from_value(json!({"name":"queued cable train","isCable":1,"cableId":cable_id,"startPage":1,"type":0,"groupNumber":200,"ruleId":fixture.rule,"trainType":0,"patType":0,"content":""})).unwrap();
    let mut creator =
        tokio::spawn(async move { add(State(state), Extension(owner), Json(body)).await });
    let reached = waiting(&fixture.pool, connection, "t_grading_rule", 1).await;
    let replacement =
        document(crate::cable::save(State(fixture.state.clone()), Json(cable("5678"))).await).await;
    let mut other = fixture.pool.begin().await.unwrap();
    sqlx::query("SET innodb_lock_wait_timeout=1")
        .execute(&mut *other)
        .await
        .unwrap();
    let writable = sqlx::query("UPDATE t_cable_floor SET mores_key='[\"B\"]' WHERE id=?")
        .bind(&unrelated_floor)
        .execute(&mut *other)
        .await
        .is_ok();
    gate.commit().await.unwrap();
    let completed = tokio::time::timeout(Duration::from_secs(5), &mut creator).await;
    other.rollback().await.unwrap();
    let created = match completed {
        Ok(Ok(response)) => response_document(response).await,
        Ok(Err(error)) => Err(format!("creator task failed: {error}")),
        Err(_) => {
            creator.abort();
            let _ = creator.await;
            Err("cable creation blocked on unrelated floor row".into())
        }
    };
    let pages = if let Some(id) = created
        .as_ref()
        .ok()
        .and_then(|response| response["data"]["id"].as_str())
    {
        sqlx::query("SELECT page_number,`key` FROM t_post_telex_pat_train_page WHERE train_id=? ORDER BY page_number,sort")
            .bind(id).fetch_all(&fixture.pool).await.unwrap()
    } else {
        Vec::new()
    };
    for id in [&cable_id, &unrelated_cable] {
        let floors: Vec<String> =
            sqlx::query_scalar("SELECT id FROM t_cable_floor WHERE cable_id=? ORDER BY id")
                .bind(id)
                .fetch_all(&fixture.pool)
                .await
                .unwrap();
        for floor in floors {
            sqlx::query("DELETE FROM t_cable_floor WHERE id=?")
                .bind(floor)
                .execute(&fixture.pool)
                .await
                .unwrap();
        }
        sqlx::query("DELETE FROM t_cable WHERE id=?")
            .bind(id)
            .execute(&fixture.pool)
            .await
            .unwrap();
    }
    sqlx::query("DELETE FROM t_cable_type WHERE id=?")
        .bind(&type_id)
        .execute(&fixture.pool)
        .await
        .unwrap();
    fixture.cleanup().await;
    assert_eq!(initial["code"], 200, "{initial}");
    let created = created.expect("cable creator must finish while unrelated floor stays locked");
    assert!(reached);
    assert!(
        writable,
        "queued creation must not lock unrelated cable floors"
    );
    assert_eq!(replacement["code"], 200, "{replacement}");
    assert_eq!(created["code"], 200, "{created}");
    assert_eq!(pages.len(), 2);
    for (index, page) in pages.iter().enumerate() {
        assert_eq!(
            page.get::<Option<i32>, _>("page_number"),
            Some(index as i32 + 1)
        );
        assert_eq!(
            page.get::<Option<String>, _>("key").as_deref(),
            Some("5678")
        );
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn queued_lazy_get_observes_reset_attempt_without_duplicate_generation() {
    let fixture = Fixture::new().await;
    sqlx::query("UPDATE t_post_telex_pat_train SET is_cable=0,group_number=201 WHERE id=?")
        .bind(&fixture.target)
        .execute(&fixture.pool)
        .await
        .unwrap();
    let (gate, connection) = gate(&fixture.pool, "t_post_telex_pat_train", &fixture.target).await;
    let (state, owner, id) = (
        fixture.state.clone(),
        fixture.owner.clone(),
        fixture.target.clone(),
    );
    let reset = tokio::spawn(async move { invoke(state, owner, id, 0, "reset").await });
    let mut reached = waiting(&fixture.pool, connection, "t_post_telex_pat_train", 1).await;
    let mut readers = Vec::new();
    for index in 0..2 {
        let (state, owner) = (fixture.state.clone(), fixture.owner.clone());
        let query =
            serde_json::from_value(json!({"trainId":fixture.target.to_uppercase(),"pageNumber":3}))
                .unwrap();
        readers.push(tokio::spawn(async move {
            crate::post_telex::get_page(State(state), Extension(owner), Query(query)).await
        }));
        reached &= waiting(
            &fixture.pool,
            connection,
            "t_post_telex_pat_train",
            index + 2,
        )
        .await;
    }
    gate.commit().await.unwrap();
    let reset = joined(reset).await;
    let mut replies = Vec::new();
    for reader in readers {
        replies.push(joined(reader).await);
    }
    let count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM t_post_telex_pat_train_page WHERE train_id=? AND page_number=3",
    )
    .bind(&fixture.target)
    .fetch_one(&fixture.pool)
    .await
    .unwrap();
    fixture.cleanup().await;
    let reset = reset.expect("queued reset must finish");
    let replies: Vec<Value> = replies
        .into_iter()
        .map(|reply| reply.expect("queued lazy get must finish"))
        .collect();
    assert!(reached);
    assert_eq!(reset["code"], 200, "{reset}");
    assert_eq!(
        count, 1,
        "only the final partial page may be generated once"
    );
    for reply in &replies {
        assert_eq!(reply["code"], 200, "{reply}");
        assert_eq!(reply["data"]["attempt"], 1);
        assert_eq!(reply["data"]["submitted"], false);
        assert_eq!(reply["data"]["pageVo"].as_array().unwrap().len(), 1);
    }
    assert_eq!(replies[0]["data"]["pageVo"], replies[1]["data"]["pageVo"]);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn settlement_replaces_telex_ids_once_but_preserves_type_four_ids() {
    for kind in [0, 4] {
        let fixture = Fixture::new().await;
        sqlx::query("UPDATE t_post_telex_pat_train SET train_type=? WHERE id=?")
            .bind(kind)
            .bind(&fixture.target)
            .execute(&fixture.pool)
            .await
            .unwrap();
        let before: Vec<String> = sqlx::query_scalar(
            "SELECT id FROM t_post_telex_pat_train_page WHERE train_id=? ORDER BY sort",
        )
        .bind(&fixture.target)
        .fetch_all(&fixture.pool)
        .await
        .unwrap();
        let finished = document(
            invoke(
                fixture.state.clone(),
                fixture.owner.clone(),
                fixture.target.clone(),
                0,
                "finish",
            )
            .await,
        )
        .await;
        let rows = sqlx::query("SELECT id,train_id,`key`,value,page_number,sort FROM t_post_telex_pat_train_page WHERE train_id=? ORDER BY sort")
            .bind(&fixture.target).fetch_all(&fixture.pool).await.unwrap();
        let after: Vec<String> = rows.iter().map(|row| row.get("id")).collect();
        let retry = document(
            invoke(
                fixture.state.clone(),
                fixture.owner.clone(),
                fixture.target.clone(),
                0,
                "finish",
            )
            .await,
        )
        .await;
        let retry_ids: Vec<String> = sqlx::query_scalar(
            "SELECT id FROM t_post_telex_pat_train_page WHERE train_id=? ORDER BY sort",
        )
        .bind(&fixture.target)
        .fetch_all(&fixture.pool)
        .await
        .unwrap();
        let query =
            serde_json::from_value(json!({"trainId":fixture.target,"pageNumber":1})).unwrap();
        let receipt = document(
            crate::post_telex::get_page(
                State(fixture.state.clone()),
                Extension(fixture.owner.clone()),
                Query(query),
            )
            .await,
        )
        .await;
        fixture.cleanup().await;
        assert_eq!(finished["code"], 200, "{finished}");
        assert_eq!(finished["data"]["score"], "100");
        assert_eq!(retry["code"], 200, "{retry}");
        assert_eq!(
            after, retry_ids,
            "terminal retry must not replace rows again"
        );
        if kind == 4 {
            assert_eq!(before, after);
        } else {
            assert!(
                after.iter().all(|id| !before.contains(id)),
                "old telex identities must disappear"
            );
        }
        assert_eq!(rows.len(), 2);
        for (index, row) in rows.iter().enumerate() {
            let expected = ["1234", "5678"][index];
            assert_eq!(
                row.get::<Option<String>, _>("key").as_deref(),
                Some(expected)
            );
            assert_eq!(
                row.get::<Option<String>, _>("value").as_deref(),
                Some(expected)
            );
            assert_eq!(row.get::<Option<i32>, _>("sort"), Some(index as i32));
            assert_eq!(row.get::<Option<i32>, _>("page_number"), Some(1));
            assert_eq!(receipt["data"]["pageVo"][index]["id"], after[index]);
        }
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and CREATE TRIGGER privilege"]
async fn settlement_insert_failure_restores_source_identities_capture_and_parent() {
    let fixture = Fixture::new().await;
    let trigger = format!("telex_settle_{}", uuid::Uuid::new_v4().simple());
    sqlx::raw_sql(&format!("CREATE TRIGGER {trigger} BEFORE INSERT ON t_post_telex_pat_train_page FOR EACH ROW BEGIN IF NEW.train_id='{}' AND NEW.sort=1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='telex settlement rollback regression'; END IF; END", fixture.target))
        .execute(&fixture.pool).await.unwrap();
    let (state, owner, id) = (
        fixture.state.clone(),
        fixture.owner.clone(),
        fixture.target.clone(),
    );
    let reply = joined(tokio::spawn(async move {
        invoke(state, owner, id, 0, "finish").await
    }))
    .await;
    sqlx::raw_sql(&format!("DROP TRIGGER {trigger}"))
        .execute(&fixture.pool)
        .await
        .unwrap();
    let mut tx = fixture.pool.begin().await.unwrap();
    let parent = sqlx::query("SELECT status,attempt,score,speed,valid_time,end_time FROM t_post_telex_pat_train WHERE id=? FOR UPDATE")
        .bind(&fixture.target).fetch_one(&mut *tx).await.unwrap();
    let rows = sqlx::query(
        "SELECT id,value FROM t_post_telex_pat_train_page WHERE train_id=? ORDER BY sort",
    )
    .bind(&fixture.target)
    .fetch_all(&mut *tx)
    .await
    .unwrap();
    let raw = sqlx::query("SELECT id,pat_value,capture_intervals FROM t_post_telex_pat_train_page_value WHERE train_id=?")
        .bind(&fixture.target).fetch_one(&mut *tx).await.unwrap();
    tx.commit().await.unwrap();
    let target = fixture.target.clone();
    fixture.cleanup().await;
    let reply = reply.expect("faulted settlement handler must finish");
    assert_eq!(reply["code"], 500, "{reply}");
    assert_eq!(parent.get::<Option<i32>, _>("status"), Some(1));
    assert_eq!(parent.get::<i32, _>("attempt"), 0);
    assert_eq!(
        parent.get::<Option<String>, _>("score").as_deref(),
        Some("100")
    );
    assert_eq!(
        parent.get::<Option<String>, _>("speed").as_deref(),
        Some("480")
    );
    assert_eq!(parent.get::<Option<i32>, _>("valid_time"), Some(1));
    assert!(parent
        .get::<Option<chrono::NaiveDateTime>, _>("end_time")
        .is_none());
    assert_eq!(rows.len(), 2);
    for (index, row) in rows.iter().enumerate() {
        assert_eq!(row.get::<String, _>("id"), format!("{target}-page-{index}"));
        assert_eq!(
            row.get::<Option<String>, _>("value").as_deref(),
            Some("confirmed")
        );
    }
    assert_eq!(raw.get::<String, _>("id"), format!("{target}-raw-0"));
    assert_eq!(
        raw.get::<Option<String>, _>("pat_value").as_deref(),
        Some("1234 5678")
    );
    assert_eq!(
        raw.get::<Option<String>, _>("capture_intervals").as_deref(),
        Some("[{\"startedMs\":0,\"endedMs\":1000}]")
    );
}

#[test]
fn multiline_counter_includes_nonempty_line_terminators_only_for_telex() {
    for (text, expected) in [
        ("1234 5678", 2),
        ("1234 5678\n", 3),
        ("1234\r\n5678\r\n", 4),
        ("\r\n1234\n\n5678", 3),
        (" \t\r\n", 0),
        ("", 0),
    ] {
        for kind in [0, 1] {
            let mut stats = ScoreStats::default();
            normalize_submission(text, kind, &mut stats);
            assert_eq!(stats.pat_groups, expected, "{text:?}, type {kind}");
        }
    }
    let mut stats = ScoreStats::default();
    normalize_submission("1234\r\n5678\r\n", 4, &mut stats);
    assert_eq!(stats.pat_groups, 2);
    assert_eq!(score_accuracy(21, 1, 0), Decimal::from(95));
    assert_eq!(score_accuracy(109, 1, 1), Decimal::from(99));
    assert_eq!(score_accuracy(218, 1, 0), Decimal::from(100));
    assert_eq!(score_accuracy(0, 0, 0), Decimal::ZERO);
    assert_eq!(score_accuracy(1, 2, 0), Decimal::ZERO);
}

#[test]
fn telex_page_marker_penalties_are_once_and_final_values_drop_markers() {
    let source = std::collections::BTreeMap::from([(
        1,
        (0..100)
            .map(|index| SourceGroup {
                id: index.to_string(),
                key: (1000 + index).to_string(),
                sort: index,
            })
            .collect::<Vec<_>>(),
    )]);
    let text = (0..10)
        .map(|line| {
            (0..10)
                .map(|column| (1000 + line * 10 + column).to_string())
                .collect::<Vec<_>>()
                .join(" ")
        })
        .collect::<Vec<_>>()
        .join("\n");
    for (suffix, missing, wrong) in [("-1", 0, 0), ("", 1, 0), ("-2", 0, 1)] {
        let submitted = std::collections::BTreeMap::from([(1, format!("{text}{suffix}"))]);
        let (stats, values) = score_pages(&source, &submitted, 0);
        assert_eq!(stats.pat_groups, 109);
        assert_eq!(stats.error_code, 0);
        assert_eq!(stats.less_page, missing);
        assert_eq!(stats.error_page, wrong);
        assert_eq!(values.last().unwrap().1, "1099");
    }
    let wrong = format!("ZZZZ{}-1", &text[4..]);
    let (stats, _) = score_pages(&source, &std::collections::BTreeMap::from([(1, wrong)]), 1);
    assert_eq!(stats.error_code, 1);
    assert_eq!(stats.pat_groups, 109);
    assert_eq!(
        score_accuracy(stats.pat_groups, stats.error_code, 1),
        Decimal::from(99)
    );
    for submitted in [
        std::collections::BTreeMap::new(),
        std::collections::BTreeMap::from([(1, String::new())]),
    ] {
        let (stats, values) = score_pages(&source, &submitted, 0);
        assert_eq!(stats.pat_groups, 0);
        assert_eq!(stats.less_page, 0);
        assert_eq!(stats.error_page, 0);
        assert_eq!(stats.less_line, 10);
        assert!(values.iter().all(|(_, value)| value.is_empty()));
    }
}
