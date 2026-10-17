use crate::domain::post_ticker::masthead;
use crate::domain::theory::theory;
use crate::domain::theory::theory_detail;
use crate::domain::theory::theory_question;
use crate::domain::theory::theory_test;
use crate::platform::auth::AuthUser;
use crate::platform::config::AppConfig;
use crate::platform::state::AppState;
use axum::{
    extract::{Extension, State},
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::{mysql::MySqlPoolOptions, MySql, MySqlPool, Transaction};
use std::{
    env,
    sync::{atomic::AtomicBool, Arc},
    time::Duration,
};
use tokio::time::timeout;
use uuid::Uuid;

struct Fixture {
    state: AppState,
    user: AuthUser,
    course: String,
    swfs: [String; 2],
    tests: [String; 3],
    contents: [String; 2],
    trains: [String; 2],
    heads: [String; 2],
}

impl Fixture {
    async fn new() -> Self {
        let url = env::var("RUST_PHASE2_DATABASE_URL").expect("isolated scratch database URL");
        assert!(url.ends_with("/rust_scratch_lockdemo"));
        let pool = MySqlPoolOptions::new()
            .max_connections(8)
            .connect(&url)
            .await
            .unwrap();
        let id = || Uuid::new_v4().to_string();
        let fixture = Self {
            state: AppState {
                config: Arc::new(AppConfig::from_env().unwrap()),
                pool: Some(pool),
                database_ready: Arc::new(AtomicBool::new(true)),
            },
            user: AuthUser {
                id: id(),
                is_admin: true,
            },
            course: id(),
            swfs: [id(), id()],
            tests: [id(), id(), id()],
            contents: [id(), id()],
            trains: [id(), id()],
            heads: [format!("z-{}", id()), format!("a-{}", id())],
        };
        sqlx::query(
            "INSERT INTO t_user (id,user_account,user_name,status) VALUES (?,?, 'B8 writer',0)",
        )
        .bind(&fixture.user.id)
        .bind(&fixture.user.id)
        .execute(fixture.pool())
        .await
        .unwrap();
        sqlx::query("INSERT INTO t_theory_knowledge (id,type,status,title,create_user_id,create_time) VALUES (?,0,1,'B8 writer',?,'1700000000000')")
            .bind(&fixture.course).bind(&fixture.user.id).execute(fixture.pool()).await.unwrap();
        for swf in &fixture.swfs {
            sqlx::query("INSERT INTO t_theory_knowledge_swf (id,knowledge_id,title,create_user_id,create_time) VALUES (?,?,'B8 chapter',?,'1700000000000')")
                .bind(swf).bind(&fixture.course).bind(&fixture.user.id).execute(fixture.pool()).await.unwrap();
        }
        for (index, test) in fixture.tests.iter().enumerate() {
            sqlx::query("INSERT INTO t_theory_knowledge_test (id,knowledge_id,knowledge_swf_id,title,versions,create_user_id,create_time) VALUES (?,?,?,'B8 test',?,?,'1700000000000')")
                .bind(test).bind(&fixture.course).bind(&fixture.swfs[usize::from(index == 2)]).bind(i32::from(index != 1)).bind(&fixture.user.id).execute(fixture.pool()).await.unwrap();
        }
        for (index, content) in fixture.contents.iter().enumerate() {
            sqlx::query("INSERT INTO t_theory_knowledge_test_content (id,knowledge_id,knowledge_swf_id,knowledge_test_id,type,topic,answer,sort,create_user_id,create_time) VALUES (?,?,?,?,1,'B8 question','A',0,?,'1700000000000')")
                .bind(content).bind(&fixture.course).bind(&fixture.swfs[index]).bind(&fixture.tests[if index == 0 { 1 } else { 2 }]).bind(&fixture.user.id).execute(fixture.pool()).await.unwrap();
        }
        for train in &fixture.trains {
            sqlx::query("INSERT INTO t_post_ticker_tape_train (id,user_id) VALUES (?,?)")
                .bind(train)
                .bind(&fixture.user.id)
                .execute(fixture.pool())
                .await
                .unwrap();
        }
        fixture
    }

    fn pool(&self) -> &MySqlPool {
        self.state.pool.as_ref().unwrap()
    }

    fn test_body(&self, id: Option<&str>) -> Value {
        json!({"knowledgeTest":{"id":id,"title":"activated","knowledgeId":self.course,"knowledgeSwfId":self.swfs[0],"versions":1},
            "knowledgeTestContents":[{"topic":"replacement","type":1,"answer":"B","sort":99}]})
    }

    async fn add_heads(&self) {
        for index in 0..2 {
            sqlx::query("INSERT INTO t_masthead (id,train_id,content) VALUES (?,?,'old')")
                .bind(&self.heads[index])
                .bind(&self.trains[index])
                .execute(self.pool())
                .await
                .unwrap();
        }
    }

    async fn cleanup(self) {
        for (table, column, ids) in [
            (
                "t_theory_knowledge_test_content",
                "knowledge_id",
                vec![&self.course],
            ),
            (
                "t_theory_knowledge_test_user",
                "knowledge_id",
                vec![&self.course],
            ),
            (
                "t_theory_knowledge_swf_record",
                "knowledge_id",
                vec![&self.course],
            ),
            (
                "t_theory_knowledge_test",
                "knowledge_id",
                vec![&self.course],
            ),
            ("t_theory_knowledge_swf", "knowledge_id", vec![&self.course]),
            ("t_masthead", "train_id", self.trains.iter().collect()),
            (
                "t_post_ticker_tape_train",
                "id",
                self.trains.iter().collect(),
            ),
            ("t_theory_knowledge", "id", vec![&self.course]),
            ("t_user", "id", vec![&self.user.id]),
        ] {
            for id in ids {
                sqlx::query(&format!("DELETE FROM {table} WHERE {column}=?"))
                    .bind(id)
                    .execute(self.pool())
                    .await
                    .unwrap();
            }
        }
        self.pool().close().await;
    }
}

async fn value(response: Response) -> Value {
    serde_json::from_slice(
        &axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap(),
    )
    .unwrap()
}

async fn lock(transaction: &mut Transaction<'_, MySql>, table: &str, id: &str) {
    sqlx::query(&format!("SELECT id FROM {table} WHERE id=? FOR UPDATE"))
        .bind(id)
        .fetch_one(&mut **transaction)
        .await
        .unwrap();
}

async fn connection(transaction: &mut Transaction<'_, MySql>) -> u64 {
    sqlx::query_scalar("SELECT CONNECTION_ID()")
        .fetch_one(&mut **transaction)
        .await
        .unwrap()
}

async fn wait_for_parent(pool: &MySqlPool, connection: u64, table: &str) -> bool {
    timeout(Duration::from_secs(5), async {
        loop {
            let waiting: i64 = sqlx::query_scalar("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME=?")
                .bind(connection).bind(table).fetch_one(pool).await.unwrap();
            if waiting == 2 { break; }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }).await.is_ok()
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn activation_does_not_lock_other_courseware_tests_or_contents() {
    let fixture = Fixture::new().await;
    let mut gate = fixture.pool().begin().await.unwrap();
    lock(&mut gate, "t_theory_knowledge_test", &fixture.tests[2]).await;
    lock(
        &mut gate,
        "t_theory_knowledge_test_content",
        &fixture.contents[1],
    )
    .await;
    let state = fixture.state.clone();
    let user = fixture.user.clone();
    let body = fixture.test_body(Some(&fixture.tests[1]));
    let mut writer =
        tokio::spawn(
            async move { theory_test::save(State(state), Extension(user), Json(body)).await },
        );
    let early = timeout(Duration::from_secs(2), &mut writer).await;
    gate.rollback().await.unwrap();
    let isolated = early.is_ok();
    let response = match early {
        Ok(result) => result.unwrap(),
        Err(_) => timeout(Duration::from_secs(5), writer)
            .await
            .unwrap()
            .unwrap(),
    };
    let response = value(response).await;
    let active: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM t_theory_knowledge_test WHERE knowledge_swf_id=? AND versions=1",
    )
    .bind(&fixture.swfs[0])
    .fetch_one(fixture.pool())
    .await
    .unwrap();
    fixture.cleanup().await;
    assert!(
        isolated,
        "foreign test/content locks must not block activation"
    );
    assert_eq!(response["code"], 200, "{response}");
    assert_eq!(active, 1);
    assert_eq!(response["data"]["knowledgeTestContents"][0]["sort"], 0);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn simultaneous_activations_wait_for_courseware_and_keep_one_enabled() {
    let fixture = Fixture::new().await;
    let mut gate = fixture.pool().begin().await.unwrap();
    lock(&mut gate, "t_theory_knowledge_swf", &fixture.swfs[0]).await;
    let connection = connection(&mut gate).await;
    let mut writers = Vec::new();
    for _ in 0..2 {
        let state = fixture.state.clone();
        let user = fixture.user.clone();
        let body = fixture.test_body(None);
        writers.push(tokio::spawn(async move {
            theory_test::save(State(state), Extension(user), Json(body)).await
        }));
    }
    let waited = wait_for_parent(fixture.pool(), connection, "t_theory_knowledge_swf").await;
    gate.rollback().await.unwrap();
    let mut results = Vec::new();
    for writer in writers {
        results.push(
            value(
                timeout(Duration::from_secs(5), writer)
                    .await
                    .unwrap()
                    .unwrap(),
            )
            .await,
        );
    }
    let active: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM t_theory_knowledge_test WHERE knowledge_swf_id=? AND versions=1",
    )
    .bind(&fixture.swfs[0])
    .fetch_one(fixture.pool())
    .await
    .unwrap();
    fixture.cleanup().await;
    assert!(
        waited,
        "both writers must wait on the existing courseware PK"
    );
    for result in results {
        assert_eq!(result["code"], 200, "{result}");
    }
    assert_eq!(
        active, 1,
        "the second writer must see the first writer's version change"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn missing_courseware_rejects_test_without_creating_orphans() {
    let fixture = Fixture::new().await;
    let mut body = fixture.test_body(None);
    body["knowledgeTest"]["knowledgeSwfId"] = json!(Uuid::new_v4().to_string());
    let result = value(
        theory_test::save(
            State(fixture.state.clone()),
            Extension(fixture.user.clone()),
            Json(body),
        )
        .await,
    )
    .await;
    let count: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM t_theory_knowledge_test WHERE knowledge_id=?")
            .bind(&fixture.course)
            .fetch_one(fixture.pool())
            .await
            .unwrap();
    fixture.cleanup().await;
    assert_eq!(result["code"], 202);
    assert_eq!(count, 3);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn masthead_save_does_not_lock_other_train_headers() {
    let fixture = Fixture::new().await;
    fixture.add_heads().await;
    let mut gate = fixture.pool().begin().await.unwrap();
    lock(&mut gate, "t_masthead", &fixture.heads[1]).await;
    let state = fixture.state.clone();
    let user = fixture.user.clone();
    let body = json!({"trainId":fixture.trains[0],"content":"updated"});
    let mut writer =
        tokio::spawn(
            async move { masthead::save(State(state), Extension(user), Json(body)).await },
        );
    let early = timeout(Duration::from_secs(2), &mut writer).await;
    gate.rollback().await.unwrap();
    let isolated = early.is_ok();
    let response = match early {
        Ok(result) => result.unwrap(),
        Err(_) => timeout(Duration::from_secs(5), writer)
            .await
            .unwrap()
            .unwrap(),
    };
    let response = value(response).await;
    fixture.cleanup().await;
    assert!(
        isolated,
        "foreign header lock must not block this train's update"
    );
    assert_eq!(response["code"], 200, "{response}");
    assert_eq!(response["data"]["content"], "updated");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn simultaneous_masthead_creates_wait_for_train_and_share_one_header() {
    let fixture = Fixture::new().await;
    let mut gate = fixture.pool().begin().await.unwrap();
    lock(&mut gate, "t_post_ticker_tape_train", &fixture.trains[0]).await;
    let connection = connection(&mut gate).await;
    let mut writers = Vec::new();
    for content in ["first", "second"] {
        let state = fixture.state.clone();
        let user = fixture.user.clone();
        let body = json!({"trainId":fixture.trains[0],"content":content});
        writers.push(tokio::spawn(async move {
            masthead::save(State(state), Extension(user), Json(body)).await
        }));
    }
    let waited = wait_for_parent(fixture.pool(), connection, "t_post_ticker_tape_train").await;
    gate.rollback().await.unwrap();
    let mut results = Vec::new();
    for writer in writers {
        results.push(
            value(
                timeout(Duration::from_secs(5), writer)
                    .await
                    .unwrap()
                    .unwrap(),
            )
            .await,
        );
    }
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_masthead WHERE train_id=?")
        .bind(&fixture.trains[0])
        .fetch_one(fixture.pool())
        .await
        .unwrap();
    fixture.cleanup().await;
    assert!(waited, "both writers must wait on the existing train PK");
    for result in &results {
        assert_eq!(result["code"], 200, "{result}");
    }
    assert_eq!(count, 1);
    assert_eq!(results[0]["data"]["id"], results[1]["data"]["id"]);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn replacing_courseware_and_deleting_course_preserves_learning_rows() {
    let fixture = Fixture::new().await;
    let answer = Uuid::new_v4().to_string();
    let record = Uuid::new_v4().to_string();
    sqlx::query("INSERT INTO t_theory_knowledge_test_user (id,knowledge_id,knowledge_swf_id,user_id,score) VALUES (?,?,?,?,100)")
        .bind(&answer).bind(&fixture.course).bind(&fixture.swfs[0]).bind(&fixture.user.id).execute(fixture.pool()).await.unwrap();
    sqlx::query("INSERT INTO t_theory_knowledge_swf_record (id,knowledge_id,knowledge_swf_id,user_id,type) VALUES (?,?,?,?,1)")
        .bind(&record).bind(&fixture.course).bind(&fixture.swfs[0]).bind(&fixture.user.id).execute(fixture.pool()).await.unwrap();
    let result = value(theory::save_knowledge(State(fixture.state.clone()), Extension(fixture.user.clone()), Json(json!({"knowledge":{"id":fixture.course,"title":"replaced","type":0,"status":1},"knowledgeSwfs":[]}))).await).await;
    let deletion = value(
        theory::delete_knowledge(
            State(fixture.state.clone()),
            Json(json!({"id":fixture.course})),
        )
        .await,
    )
    .await;
    let mut counts = Vec::new();
    for table in [
        "t_theory_knowledge_test_user",
        "t_theory_knowledge_swf_record",
        "t_theory_knowledge_test",
        "t_theory_knowledge_test_content",
    ] {
        counts.push(
            sqlx::query_scalar::<_, i64>(&format!(
                "SELECT COUNT(*) FROM {table} WHERE knowledge_id=?"
            ))
            .bind(&fixture.course)
            .fetch_one(fixture.pool())
            .await
            .unwrap(),
        );
    }
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(deletion["code"], 200, "{deletion}");
    assert_eq!(counts, [1, 1, 3, 2]);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn course_edits_only_invalidate_answers_when_enabled_questions_change() {
    let fixture = Fixture::new().await;
    for (id, version) in [(&fixture.tests[0], 0), (&fixture.tests[1], 1)] {
        sqlx::query("UPDATE t_theory_knowledge_test SET versions=? WHERE id=?")
            .bind(version)
            .bind(id)
            .execute(fixture.pool())
            .await
            .unwrap();
    }
    sqlx::query("INSERT INTO t_theory_knowledge_test_user (id,knowledge_id,knowledge_swf_id,user_id,score) VALUES (?,?,?,?,100)")
        .bind(Uuid::new_v4().to_string()).bind(&fixture.course).bind(&fixture.swfs[0]).bind(&fixture.user.id).execute(fixture.pool()).await.unwrap();
    sqlx::query("INSERT INTO t_theory_knowledge_swf_record (id,knowledge_id,knowledge_swf_id,user_id,type) VALUES (?,?,?,?,1)")
        .bind(Uuid::new_v4().to_string()).bind(&fixture.course).bind(&fixture.swfs[0]).bind(&fixture.user.id).execute(fixture.pool()).await.unwrap();
    let detail = value(
        theory_detail::by_id(
            State(fixture.state.clone()),
            Json(json!({"id":fixture.course})),
        )
        .await,
    )
    .await;
    let mut body = detail["data"].clone();
    let unchanged = value(
        theory::save_knowledge(
            State(fixture.state.clone()),
            Extension(fixture.user.clone()),
            Json(body.clone()),
        )
        .await,
    )
    .await;
    let before: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM t_theory_knowledge_test_user WHERE knowledge_id=?",
    )
    .bind(&fixture.course)
    .fetch_one(fixture.pool())
    .await
    .unwrap();
    let swf = body["knowledgeSwfs"]
        .as_array_mut()
        .unwrap()
        .iter_mut()
        .find(|swf| swf["id"] == fixture.swfs[0])
        .unwrap();
    let test = swf["test"]
        .as_array_mut()
        .unwrap()
        .iter_mut()
        .find(|test| test["id"] == fixture.tests[1])
        .unwrap();
    test["knowledgeTestContents"][0]["answer"] = json!("B");
    let changed = value(
        theory::save_knowledge(
            State(fixture.state.clone()),
            Extension(fixture.user.clone()),
            Json(body),
        )
        .await,
    )
    .await;
    let after: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM t_theory_knowledge_test_user WHERE knowledge_id=?",
    )
    .bind(&fixture.course)
    .fetch_one(fixture.pool())
    .await
    .unwrap();
    let records: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM t_theory_knowledge_swf_record WHERE knowledge_id=?",
    )
    .bind(&fixture.course)
    .fetch_one(fixture.pool())
    .await
    .unwrap();
    fixture.cleanup().await;
    assert_eq!(unchanged["code"], 200, "{unchanged}");
    assert_eq!(changed["code"], 200, "{changed}");
    assert_eq!((before, after, records), (1, 0, 1));
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn grade_count_filters_user_state_year_and_month_before_all_three_modes() {
    let fixture = Fixture::new().await;
    let exams = [Uuid::new_v4().to_string(), Uuid::new_v4().to_string()];
    let papers = [Uuid::new_v4().to_string(), Uuid::new_v4().to_string()];
    for (index, (total, pass)) in [(101, 70), (100, 90)].into_iter().enumerate() {
        sqlx::query("INSERT INTO t_theory_knowledge_exam_test_paper (id,exam_id,total,pass_mark) VALUES (?,?,?,?)")
            .bind(&papers[index]).bind(&exams[index]).bind(total).bind(pass).execute(fixture.pool()).await.unwrap();
    }
    let mut ids = Vec::new();
    for (date, score, paper, state, owner) in [
        ("2024-02-02 10:00:00", 69, 0, 4, fixture.user.id.as_str()),
        ("2024-02-29 10:00:00", 85, 0, 4, fixture.user.id.as_str()),
        ("2024-03-01 10:00:00", 80, 1, 4, fixture.user.id.as_str()),
        ("2023-02-02 10:00:00", 100, 0, 4, fixture.user.id.as_str()),
        ("2024-02-02 10:00:00", 100, 0, 3, fixture.user.id.as_str()),
        ("2024-02-02 10:00:00", 100, 0, 4, fixture.course.as_str()),
    ] {
        let id = Uuid::new_v4().to_string();
        sqlx::query("INSERT INTO t_theory_knowledge_exam_user (id,exam_id,user_id,state,score,end_time) VALUES (?,?,?,?,?,?)")
            .bind(&id).bind(&exams[paper]).bind(owner).bind(state).bind(score).bind(date).execute(fixture.pool()).await.unwrap();
        ids.push(id);
    }
    let mut results = Vec::new();
    for body in [
        json!({"year":"2024","type":0}),
        json!({"year":"2024","month":"02","type":0}),
        json!({"year":"2024","month":"","type":1}),
        json!({"year":"2024","month":2,"type":1}),
        json!({"year":"2024","type":2}),
        json!({"year":"2024","month":"2","type":99}),
    ] {
        results.push(
            value(
                theory::grade_count(
                    State(fixture.state.clone()),
                    Extension(fixture.user.clone()),
                    Json(body),
                )
                .await,
            )
            .await,
        );
    }
    for id in ids {
        sqlx::query("DELETE FROM t_theory_knowledge_exam_user WHERE id=?")
            .bind(id)
            .execute(fixture.pool())
            .await
            .unwrap();
    }
    for id in papers {
        sqlx::query("DELETE FROM t_theory_knowledge_exam_test_paper WHERE id=?")
            .bind(id)
            .execute(fixture.pool())
            .await
            .unwrap();
    }
    fixture.cleanup().await;
    for result in &results {
        assert_eq!(result["code"], 200, "{result}");
    }
    assert_eq!(
        results[0]["data"],
        json!({"up":{"all":3,"good":1},"down":{"59":2,"81":1}})
    );
    assert_eq!(
        results[1]["data"],
        json!({"up":{"all":2,"good":1},"down":{"59":1,"81":1}})
    );
    assert_eq!(results[2]["data"]["down"], json!({"2":2,"3":1}));
    assert_eq!(results[3]["data"]["down"], json!({"2":1,"29":1}));
    assert_eq!(
        results[4]["data"]["down"],
        json!({"2":{"high":"85","low":"69","avg":"77.00"},"3":{"high":"80","low":"80","avg":"80.00"}})
    );
    assert_eq!(
        results[5]["data"]["down"],
        json!({"2":{"high":"69","low":"69","avg":"69.00"},"29":{"high":"85","low":"85","avg":"85.00"}})
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn question_tree_search_uses_mysql_like_and_level_deletion_keeps_questions() {
    let fixture = Fixture::new().await;
    let root = Uuid::new_v4().to_string();
    let child = Uuid::new_v4().to_string();
    for (id, parent) in [(&root, ""), (&child, root.as_str())] {
        sqlx::query(
            "INSERT INTO t_theory_question_level (id,parent_id,name) VALUES (?,?,'B8 level')",
        )
        .bind(id)
        .bind(parent)
        .execute(fixture.pool())
        .await
        .unwrap();
    }
    let mut ids = Vec::new();
    for (topic, kind, level) in [
        (Some("Short-Wave"), 1, &root),
        (Some("SHORT_WAVE"), 2, &child),
        (None, 1, &child),
        (Some("Short-Wave"), 1, &fixture.course),
    ] {
        let id = Uuid::new_v4().to_string();
        sqlx::query("INSERT INTO t_theory_question (id,type,topic,level_id,create_user_id) VALUES (?,?,?,?,?)")
            .bind(&id).bind(kind).bind(topic).bind(level).bind(&fixture.user.id).execute(fixture.pool()).await.unwrap();
        ids.push(id);
    }
    let mut results = Vec::new();
    for body in [
        json!({"levelId":root,"name":"short"}),
        json!({"levelId":root,"name":"S%VE"}),
        json!({"levelId":root,"name":"Short_Wave"}),
        json!({"levelId":root,"name":"%","type":"1"}),
        json!({"levelId":root}),
    ] {
        results.push(
            value(theory_question::list_questions(State(fixture.state.clone()), Json(body)).await)
                .await,
        );
    }
    let deletion = value(
        theory_question::delete_level(State(fixture.state.clone()), Json(json!({"id":child})))
            .await,
    )
    .await;
    let remaining: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM t_theory_question WHERE level_id=?")
            .bind(&child)
            .fetch_one(fixture.pool())
            .await
            .unwrap();
    for id in ids {
        sqlx::query("DELETE FROM t_theory_question WHERE id=?")
            .bind(id)
            .execute(fixture.pool())
            .await
            .unwrap();
    }
    sqlx::query("DELETE FROM t_theory_question_level WHERE id=?")
        .bind(&root)
        .execute(fixture.pool())
        .await
        .unwrap();
    fixture.cleanup().await;
    for result in &results {
        assert_eq!(result["code"], 200, "{result}");
    }
    assert_eq!(
        results
            .iter()
            .map(|result| result["data"].as_array().unwrap().len())
            .collect::<Vec<_>>(),
        [2, 2, 2, 1, 3]
    );
    assert_eq!(results[0]["data"][0]["createUserName"], "B8 writer");
    assert_eq!(deletion["code"], 200);
    assert_eq!(remaining, 2);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn catalog_requires_default_categories_and_courseware_and_never_opens_drafts() {
    let fixture = Fixture::new().await;
    let difficulty = Uuid::new_v4().to_string();
    let specialty = Uuid::new_v4().to_string();
    for (id, kind) in [(&difficulty, 1), (&specialty, 0)] {
        sqlx::query(
            "INSERT INTO t_theory_knowledge_classify (id,type,name) VALUES (?,?,'B8 category')",
        )
        .bind(id)
        .bind(kind)
        .execute(fixture.pool())
        .await
        .unwrap();
    }
    sqlx::query("UPDATE t_theory_knowledge SET difficulty_id=?,specialty_id=? WHERE id=?")
        .bind(&difficulty)
        .bind(&specialty)
        .bind(&fixture.course)
        .execute(fixture.pool())
        .await
        .unwrap();
    let draft = Uuid::new_v4().to_string();
    let unclassified = Uuid::new_v4().to_string();
    let empty = Uuid::new_v4().to_string();
    let extra_swfs = [Uuid::new_v4().to_string(), Uuid::new_v4().to_string()];
    for (id, status, classified) in [
        (&draft, 0, true),
        (&unclassified, 1, false),
        (&empty, 1, true),
    ] {
        sqlx::query("INSERT INTO t_theory_knowledge (id,type,status,title,difficulty_id,specialty_id) VALUES (?,0,?,'B8 visibility',?,?)")
            .bind(id).bind(status).bind(classified.then_some(&difficulty)).bind(classified.then_some(&specialty)).execute(fixture.pool()).await.unwrap();
    }
    for (id, course) in [(&extra_swfs[0], &draft), (&extra_swfs[1], &unclassified)] {
        sqlx::query("INSERT INTO t_theory_knowledge_swf (id,knowledge_id) VALUES (?,?)")
            .bind(id)
            .bind(course)
            .execute(fixture.pool())
            .await
            .unwrap();
    }
    let basic =
        value(theory::list_basic(State(fixture.state.clone()), Json(json!({"type":0}))).await)
            .await;
    let open = value(
        theory::list_open(
            State(fixture.state.clone()),
            Extension(fixture.user.clone()),
            Json(json!({"type":0,"status":"[0]"})),
        )
        .await,
    )
    .await;
    let none = value(
        theory::list_basic(
            State(fixture.state.clone()),
            Json(json!({"type":0,"difficulty":"[]"})),
        )
        .await,
    )
    .await;
    for id in &extra_swfs {
        sqlx::query("DELETE FROM t_theory_knowledge_swf WHERE id=?")
            .bind(id)
            .execute(fixture.pool())
            .await
            .unwrap();
    }
    for id in [&draft, &unclassified, &empty] {
        sqlx::query("DELETE FROM t_theory_knowledge WHERE id=?")
            .bind(id)
            .execute(fixture.pool())
            .await
            .unwrap();
    }
    for id in [&difficulty, &specialty] {
        sqlx::query("DELETE FROM t_theory_knowledge_classify WHERE id=?")
            .bind(id)
            .execute(fixture.pool())
            .await
            .unwrap();
    }
    let course = fixture.course.clone();
    fixture.cleanup().await;
    for result in [&basic, &open, &none] {
        assert_eq!(result["code"], 200, "{result}");
    }
    let has = |result: &Value, id: &str| {
        result["data"]
            .as_array()
            .unwrap()
            .iter()
            .any(|item| item["id"] == id)
    };
    assert!(has(&basic, &course) && has(&basic, &draft));
    assert!(!has(&basic, &unclassified) && !has(&basic, &empty));
    assert!(has(&open, &course) && !has(&open, &draft));
    assert!(!has(&open, &unclassified) && !has(&open, &empty));
    assert_eq!(none["data"], json!([]));
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn course_edit_deletes_only_primary_keys_without_locking_other_courses() {
    let fixture = Fixture::new().await;
    let foreign = Fixture::new().await;
    let mut answers = Vec::new();
    for owner in [&fixture, &foreign] {
        for (id, version) in [(&owner.tests[0], 0), (&owner.tests[1], 1)] {
            sqlx::query("UPDATE t_theory_knowledge_test SET versions=? WHERE id=?")
                .bind(version)
                .bind(id)
                .execute(owner.pool())
                .await
                .unwrap();
        }
        let answer = Uuid::new_v4().to_string();
        sqlx::query("INSERT INTO t_theory_knowledge_test_user (id,knowledge_id,knowledge_swf_id,user_id,score) VALUES (?,?,?,?,100)")
            .bind(&answer).bind(&owner.course).bind(&owner.swfs[0]).bind(&owner.user.id).execute(owner.pool()).await.unwrap();
        answers.push(answer);
    }
    let detail = value(
        theory_detail::by_id(
            State(fixture.state.clone()),
            Json(json!({"id":fixture.course})),
        )
        .await,
    )
    .await;
    let mut body = detail["data"].clone();
    let swf = body["knowledgeSwfs"]
        .as_array_mut()
        .unwrap()
        .iter_mut()
        .find(|swf| swf["id"] == fixture.swfs[0])
        .unwrap();
    let test = swf["test"]
        .as_array_mut()
        .unwrap()
        .iter_mut()
        .find(|test| test["id"] == fixture.tests[1])
        .unwrap();
    test["knowledgeTestContents"][0]["answer"] = json!("B");
    let mut gate = foreign.pool().begin().await.unwrap();
    lock(&mut gate, "t_theory_knowledge_swf", &foreign.swfs[0]).await;
    lock(
        &mut gate,
        "t_theory_knowledge_test_content",
        &foreign.contents[0],
    )
    .await;
    lock(&mut gate, "t_theory_knowledge_test_user", &answers[1]).await;
    let state = fixture.state.clone();
    let user = fixture.user.clone();
    let mut writer = tokio::spawn(async move {
        theory::save_knowledge(State(state), Extension(user), Json(body)).await
    });
    let early = timeout(Duration::from_secs(2), &mut writer).await;
    gate.rollback().await.unwrap();
    let isolated = early.is_ok();
    let response = match early {
        Ok(result) => result.unwrap(),
        Err(_) => timeout(Duration::from_secs(5), writer)
            .await
            .unwrap()
            .unwrap(),
    };
    let response = value(response).await;
    fixture.cleanup().await;
    foreign.cleanup().await;
    assert!(
        isolated,
        "foreign courseware, content and answer locks must not block a course edit"
    );
    assert_eq!(response["code"], 200, "{response}");
}
