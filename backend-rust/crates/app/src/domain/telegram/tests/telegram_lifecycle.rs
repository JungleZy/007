use crate::domain::telegram::telegram_lifecycle::*;
use crate::platform::auth::AuthUser;
use crate::platform::state::AppState;
use axum::extract::Extension;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::Response;
use axum::Json;
use serde_json::json;
use serde_json::Value;
use sqlx::MySqlPool;
use sqlx::Row;
use std::{sync::Arc, time::Duration};

struct Fixture {
    state: AppState,
    user: AuthUser,
    train_id: String,
    floor_id: String,
    content_ids: Vec<String>,
}

async fn response_json(response: Response) -> Value {
    assert_eq!(response.status(), StatusCode::OK);
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    serde_json::from_slice(&bytes).unwrap()
}

impl Fixture {
    async fn new() -> Self {
        let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("isolated scratch database URL");
        assert!(url.ends_with("/rust_scratch_lockdemo"));
        let pool = sqlx::mysql::MySqlPoolOptions::new()
            .max_connections(5)
            .connect(&url)
            .await
            .unwrap();
        let user = AuthUser {
            id: uuid::Uuid::new_v4().to_string(),
            is_admin: false,
        };
        sqlx::query("INSERT INTO t_user (id,user_account,user_name,status) VALUES (?,?,?,0)")
            .bind(&user.id)
            .bind(&user.id)
            .bind("lifecycle regression")
            .execute(&pool)
            .await
            .unwrap();
        let state = AppState {
            config: Arc::new(crate::platform::config::AppConfig::from_env().unwrap()),
            pool: Some(pool.clone()),
            database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
        };
        let created = response_json(
            crate::domain::telegram::telegram_write::save(
                State(state.clone()),
                Extension(user.clone()),
                Json(json!({
                    "train":{"type":1,"title":"lifecycle regression","totalNumber":2},
                    "trainFloors":[{"floor":{"type":1,"numberType":0},
                        "floorContents":[{"moresKey":"E","moresValue":"[]"},
                            {"moresKey":"T","moresValue":"[]"}]}]
                })),
            )
            .await,
        )
        .await;
        assert_eq!(created["code"], 200, "{created}");
        let train_id = created["data"]["id"].as_str().unwrap().to_owned();
        let floor_id: String =
            sqlx::query_scalar("SELECT id FROM t_telegram_train_floor WHERE train_id=?")
                .bind(&train_id)
                .fetch_one(&pool)
                .await
                .unwrap();
        let content_ids = sqlx::query_scalar(
            "SELECT id FROM t_telegram_train_floor_content WHERE floor_id=? ORDER BY sort",
        )
        .bind(&floor_id)
        .fetch_all(&pool)
        .await
        .unwrap();
        Self {
            state,
            user,
            train_id,
            floor_id,
            content_ids,
        }
    }

    fn pool(&self) -> &MySqlPool {
        self.state.pool.as_ref().unwrap()
    }

    fn body(&self, kind: i32) -> Value {
        json!({"controlType":kind,"train":{"id":self.train_id,"nowFloorId":self.floor_id},
            "trainFloors":[{"floor":{"id":self.floor_id},"floorContents":[
                {"id":self.content_ids[0],"moresValue":"[0]","moresTime":"[40]"},
                {"id":self.content_ids[1],"moresValue":"[1]","moresTime":"[160]"}]}]})
    }

    async fn request(&self, body: Value) -> Value {
        response_json(
            control(
                State(self.state.clone()),
                Extension(self.user.clone()),
                Json(body),
            )
            .await,
        )
        .await
    }

    async fn success(&self, kind: i32) -> Value {
        let response = self.request(self.body(kind)).await;
        assert_eq!(response["code"], 200, "{response}");
        response["data"].clone()
    }

    async fn snapshot(&self) -> Value {
        let row = sqlx::query(&format!(
            "SELECT {} FROM t_telegram_train WHERE id=?",
            crate::domain::telegram::telegram::TRAIN_COLUMNS
        ))
        .bind(&self.train_id)
        .fetch_one(self.pool())
        .await
        .unwrap();
        let contents = sqlx::query("SELECT id,mores_key,mores_value,mores_time,sort FROM t_telegram_train_floor_content WHERE floor_id=? ORDER BY sort")
            .bind(&self.floor_id).fetch_all(self.pool()).await.unwrap();
        let logs = sqlx::query("SELECT id,type,value,creat_time FROM t_telegram_train_log WHERE telegram_train_id=? ORDER BY id")
            .bind(&self.train_id).fetch_all(self.pool()).await.unwrap();
        let stats = sqlx::query("SELECT id,type,total_count,total_time,CAST(avg_speed AS CHAR) AS avg_speed FROM t_telegram_train_statistical WHERE user_id=? ORDER BY id")
            .bind(&self.user.id).fetch_all(self.pool()).await.unwrap();
        json!({"train":crate::domain::telegram::telegram::train_json(&row),
            "content":contents.iter().map(|row| json!({
                "id":row.get::<String,_>("id"),"key":row.get::<Option<String>,_>("mores_key"),
                "raw":row.get::<Option<String>,_>("mores_value"),"time":row.get::<Option<String>,_>("mores_time"),
                "sort":row.get::<Option<i32>,_>("sort")
            })).collect::<Vec<_>>(),
            "logs":logs.iter().map(|row| json!({
                "id":row.get::<String,_>("id"),"type":row.get::<Option<i32>,_>("type"),
                "value":row.get::<Option<String>,_>("value"),"time":row.get::<Option<String>,_>("creat_time")
            })).collect::<Vec<_>>(),
            "statistics":stats.iter().map(|row| json!({
                "id":row.get::<String,_>("id"),"type":row.get::<Option<i32>,_>("type"),
                "count":row.get::<Option<i32>,_>("total_count"),"time":row.get::<Option<String>,_>("total_time"),
                "speed":row.get::<Option<String>,_>("avg_speed")
            })).collect::<Vec<_>>()})
    }

    async fn cleanup(&self) {
        for (table, column, owner) in [
            ("t_telegram_train_log", "telegram_train_id", &self.train_id),
            ("t_telegram_train_statistical", "user_id", &self.user.id),
        ] {
            let ids: Vec<String> =
                sqlx::query_scalar(&format!("SELECT id FROM {table} WHERE {column}=?"))
                    .bind(owner)
                    .fetch_all(self.pool())
                    .await
                    .unwrap();
            for id in ids {
                sqlx::query(&format!("DELETE FROM {table} WHERE id=?"))
                    .bind(id)
                    .execute(self.pool())
                    .await
                    .unwrap();
            }
        }
        for id in &self.content_ids {
            sqlx::query("DELETE FROM t_telegram_train_floor_content WHERE id=?")
                .bind(id)
                .execute(self.pool())
                .await
                .unwrap();
        }
        for (table, id) in [
            ("t_telegram_train_floor", &self.floor_id),
            ("t_telegram_train", &self.train_id),
            ("t_user", &self.user.id),
        ] {
            sqlx::query(&format!("DELETE FROM {table} WHERE id=?"))
                .bind(id)
                .execute(self.pool())
                .await
                .unwrap();
        }
    }
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo tables"]
async fn lifecycle_start_retry_preserves_initial_and_active_timestamps() {
    let fixture = Fixture::new().await;
    let first = fixture.success(0).await;
    tokio::time::sleep(Duration::from_millis(30)).await;
    let retried = fixture.success(0).await;
    fixture.success(1).await;
    tokio::time::sleep(Duration::from_millis(30)).await;
    let resumed = fixture.success(0).await;
    fixture.cleanup().await;
    assert_eq!(first["status"], 1);
    assert!(!first["startTime"].is_null());
    assert!(!first["activeSince"].is_null());
    assert_eq!(first["startTime"], retried["startTime"]);
    assert_eq!(first["activeSince"], retried["activeSince"]);
    assert_eq!(first["startTime"], resumed["startTime"]);
    assert!(resumed["activeSince"].as_i64().unwrap() > first["activeSince"].as_i64().unwrap());
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo tables"]
async fn lifecycle_pause_retry_does_not_double_charge_active_time() {
    let fixture = Fixture::new().await;
    fixture.success(0).await;
    tokio::time::sleep(Duration::from_millis(30)).await;
    let paused = fixture.success(1).await;
    let before = fixture.snapshot().await;
    tokio::time::sleep(Duration::from_millis(30)).await;
    let retry = fixture.success(1).await;
    let after = fixture.snapshot().await;
    fixture.cleanup().await;
    assert_eq!(paused["status"], 2);
    assert_eq!(paused["activeSince"], Value::Null);
    assert!(paused["accumulatedActiveMillis"].as_i64().unwrap() >= 30);
    assert_eq!(paused, retry);
    assert_eq!(before, after);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo tables"]
async fn lifecycle_paused_interval_is_excluded_from_active_time() {
    let fixture = Fixture::new().await;
    let started = fixture.success(0).await;
    tokio::time::sleep(Duration::from_millis(20)).await;
    let paused = fixture.success(1).await;
    tokio::time::sleep(Duration::from_millis(150)).await;
    let resumed = fixture.success(0).await;
    tokio::time::sleep(Duration::from_millis(20)).await;
    let paused_again = fixture.success(1).await;
    fixture.cleanup().await;
    let time = |value: &Value, name: &str| value[name].as_str().unwrap().parse::<i64>().unwrap();
    let first_segment = time(&paused, "pauseTime") - started["activeSince"].as_i64().unwrap();
    let second_segment =
        time(&paused_again, "pauseTime") - resumed["activeSince"].as_i64().unwrap();
    let expected = first_segment + second_segment;
    assert_eq!(paused["accumulatedActiveMillis"], first_segment);
    assert_eq!(resumed["accumulatedActiveMillis"], first_segment);
    assert_eq!(paused_again["accumulatedActiveMillis"], expected);
    assert_eq!(time(&paused_again, "sustainTime"), expected);
    let wall_time = time(&paused_again, "pauseTime") - time(&started, "startTime");
    assert!(
        wall_time - expected >= 150,
        "paused interval must not count as active time"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo tables"]
async fn lifecycle_end_from_paused_rejects_late_content_and_log_without_mutation() {
    let fixture = Fixture::new().await;
    fixture.success(0).await;
    let paused = fixture.success(1).await;
    tokio::time::sleep(Duration::from_millis(30)).await;
    let finished = fixture.success(2).await;
    let before = fixture.snapshot().await;
    let captured = save_captured_content(
        fixture.pool(),
        &fixture.user.id,
        &json!({"id":fixture.content_ids[0],"moresValue":"[1]","moresTime":"[999]"}),
    )
    .await;
    let logged = save_captured_log(
        fixture.pool(),
        &fixture.user.id,
        &json!({"telegramTrainId":fixture.train_id,"type":1,"value":999}),
    )
    .await;
    let after = fixture.snapshot().await;
    fixture.cleanup().await;
    assert_eq!(finished["status"], 3);
    assert!(!finished["endTime"].is_null());
    assert_eq!(finished["activeSince"], Value::Null);
    assert_eq!(
        finished["accumulatedActiveMillis"],
        paused["accumulatedActiveMillis"]
    );
    assert_eq!(finished["sustainTime"], paused["sustainTime"]);
    assert_eq!(finished["totalKnockNumber"], 2);
    assert_eq!(finished["accuracy"], "100.00");
    assert_eq!(before["statistics"][0]["count"], 1);
    assert_eq!(captured, Err((208, "训练已结束")));
    assert_eq!(logged, Err((208, "训练已结束")));
    assert_eq!(
        before, after,
        "late 3001/2001 must not alter any content, metrics, logs or statistics"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo tables"]
async fn lifecycle_invalid_raw_and_foreign_floor_roll_back_all_writes() {
    let fixture = Fixture::new().await;
    let foreign = Fixture::new().await;
    fixture.success(0).await;
    let before = fixture.snapshot().await;
    let foreign_before = foreign.snapshot().await;
    let mut invalid = fixture.body(2);
    invalid["train"]["rateDotMinMs"] = json!(2);
    invalid["trainFloors"][0]["floorContents"][1]["moresValue"] = json!("not json");
    let rejected_raw = fixture.request(invalid).await;
    let after_raw = fixture.snapshot().await;
    let mut invalid = fixture.body(2);
    invalid["train"]["rateDotMinMs"] = json!(3);
    invalid["train"]["nowFloorId"] = json!(foreign.floor_id);
    let rejected_floor = fixture.request(invalid).await;
    let after_floor = fixture.snapshot().await;
    let foreign_after = foreign.snapshot().await;
    fixture.cleanup().await;
    foreign.cleanup().await;
    assert_eq!(rejected_raw["code"], 202);
    assert_eq!(rejected_raw["message"], "报文原始内容无效");
    assert_eq!(rejected_floor["code"], 207);
    assert_eq!(
        before, after_raw,
        "invalid raw JSON must roll back rates/content/time/metrics"
    );
    assert_eq!(
        before, after_floor,
        "foreign current floor must roll back already-calculated metrics and raw writes"
    );
    assert_eq!(foreign_before, foreign_after);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo tables"]
async fn lifecycle_numeric_2001_duration_is_persisted_as_java_string() {
    let fixture = Fixture::new().await;
    fixture.success(0).await;
    save_captured_log(
        fixture.pool(),
        &fixture.user.id,
        &json!({"telegramTrainId":fixture.train_id,"type":1,"value":160}),
    )
    .await
    .unwrap();
    let stored = fixture.snapshot().await;
    fixture.cleanup().await;
    assert_eq!(stored["logs"].as_array().unwrap().len(), 1);
    assert_eq!(stored["logs"][0]["type"], 1);
    assert_eq!(stored["logs"][0]["value"], "160");
    assert!(!stored["logs"][0]["time"].is_null());
}

#[test]
fn persisted_morse_scoring_covers_groups_and_short_digits() {
    assert_eq!(raw_result("E", Some("[0]"), false, false), Ok((true, true)));
    assert_eq!(
        raw_result("T", Some("[]"), false, false),
        Ok((false, false))
    );
    assert_eq!(
        raw_result("8", Some("[1,0,0]"), false, true),
        Ok((true, true))
    );
    assert_eq!(
        raw_result("8", Some("[1,1,1,0,0]"), false, false),
        Ok((true, true))
    );
    assert_eq!(
        raw_result("[\"E\",\"T\"]", Some("[[0],[1]]"), true, false),
        Ok((true, true))
    );
    assert_eq!(
        raw_result("[\"E\",\"T\"]", Some("[[0],[]]"), true, false),
        Ok((false, true))
    );
    assert_eq!(
        raw_result("E", Some("[0.0]"), false, false),
        Ok((false, true))
    );
}

#[tokio::test]
#[ignore = "requires RUST_PHASE2_DATABASE_URL pointing to isolated scratch tables"]
async fn queued_capture_and_log_cannot_write_after_settlement() {
    use crate::domain::telegram::telegram_lifecycle::save_captured_content;
    use crate::domain::telegram::telegram_lifecycle::save_captured_log;
    use serde_json::json;
    use sqlx::{mysql::MySqlPoolOptions, Row};
    use std::time::Duration;

    let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("isolated scratch database URL");
    assert!(
        url.ends_with("/rust_scratch_lockdemo"),
        "only the isolated scratch database is allowed"
    );
    let pool = MySqlPoolOptions::new()
        .max_connections(5)
        .connect(&url)
        .await
        .unwrap();
    let actor = uuid::Uuid::new_v4().to_string();
    let train_id = uuid::Uuid::new_v4().to_string();
    let floor_id = uuid::Uuid::new_v4().to_string();
    let content_id = uuid::Uuid::new_v4().to_string();
    sqlx::query("INSERT INTO t_user (id,user_account,user_name,status) VALUES (?,?,?,0)")
        .bind(&actor)
        .bind(&actor)
        .bind("capture regression")
        .execute(&pool)
        .await
        .unwrap();
    sqlx::query("INSERT INTO t_telegram_train (id,type,status,protocol_version,create_user_id,accumulated_active_millis) VALUES (?,1,1,1,?,0)").bind(&train_id).bind(&actor).execute(&pool).await.unwrap();
    sqlx::query("INSERT INTO t_telegram_train_floor (id,train_id,sort,type,number_type,content_number) VALUES (?,?,0,1,0,1)").bind(&floor_id).bind(&train_id).execute(&pool).await.unwrap();
    sqlx::query("INSERT INTO t_telegram_train_floor_content (id,floor_id,sort,mores_key,mores_value,mores_time) VALUES (?,?,0,'E','[]','[]')").bind(&content_id).bind(&floor_id).execute(&pool).await.unwrap();

    let mut settlement = pool.begin().await.unwrap();
    assert!(
        crate::shared::statistical::lock_user(&mut settlement, &actor)
            .await
            .unwrap()
    );
    sqlx::query("SELECT id FROM t_telegram_train WHERE id=? FOR UPDATE")
        .bind(&train_id)
        .fetch_one(&mut *settlement)
        .await
        .unwrap();
    sqlx::query(
        "UPDATE t_telegram_train SET status=3,accuracy='100.00',total_knock_number=1 WHERE id=?",
    )
    .bind(&train_id)
    .execute(&mut *settlement)
    .await
    .unwrap();
    sqlx::query(
        "UPDATE t_telegram_train_floor_content SET mores_value='[0]',mores_time='[40]' WHERE id=?",
    )
    .bind(&content_id)
    .execute(&mut *settlement)
    .await
    .unwrap();

    let capture = json!({"id":content_id,"moresValue":"[1]","moresTime":"[160]"});
    let log = json!({"telegramTrainId":train_id,"type":1,"value":"160"});
    let (capture_pool, capture_actor, capture_data) =
        (pool.clone(), actor.clone(), capture.clone());
    let mut capture_job = tokio::spawn(async move {
        save_captured_content(&capture_pool, &capture_actor, &capture_data).await
    });
    let (log_pool, log_actor, log_data) = (pool.clone(), actor.clone(), log.clone());
    let mut log_job =
        tokio::spawn(async move { save_captured_log(&log_pool, &log_actor, &log_data).await });
    let capture_wait = tokio::time::timeout(Duration::from_millis(200), &mut capture_job).await;
    let log_wait = tokio::time::timeout(Duration::from_millis(200), &mut log_job).await;
    let capture_blocked = capture_wait.is_err();
    let log_blocked = log_wait.is_err();
    settlement.commit().await.unwrap();
    let captured = match capture_wait {
        Ok(result) => result.unwrap(),
        Err(_) => capture_job.await.unwrap(),
    };
    let logged = match log_wait {
        Ok(result) => result.unwrap(),
        Err(_) => log_job.await.unwrap(),
    };
    let late_capture = save_captured_content(&pool, &actor, &capture).await;
    let late_log = save_captured_log(&pool, &actor, &log).await;
    let stored =
        sqlx::query("SELECT mores_value,mores_time FROM t_telegram_train_floor_content WHERE id=?")
            .bind(&content_id)
            .fetch_one(&pool)
            .await
            .unwrap();
    let log_count = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM t_telegram_train_log WHERE telegram_train_id=?",
    )
    .bind(&train_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    sqlx::query("DELETE FROM t_telegram_train_log WHERE telegram_train_id=?")
        .bind(&train_id)
        .execute(&pool)
        .await
        .unwrap();
    for (table, id) in [
        ("t_telegram_train_floor_content", &content_id),
        ("t_telegram_train_floor", &floor_id),
        ("t_telegram_train", &train_id),
        ("t_user", &actor),
    ] {
        sqlx::query(&format!("DELETE FROM {table} WHERE id=?"))
            .bind(id)
            .execute(&pool)
            .await
            .unwrap();
    }
    assert!(
        capture_blocked && log_blocked,
        "queued capture/log must wait for the settlement user lock"
    );
    assert_eq!(captured, Err((208, "训练已结束")));
    assert_eq!(logged, Err((208, "训练已结束")));
    assert_eq!(late_capture, Err((208, "训练已结束")));
    assert_eq!(late_log, Err((208, "训练已结束")));
    assert_eq!(stored.get::<String, _>("mores_value"), "[0]");
    assert_eq!(stored.get::<String, _>("mores_time"), "[40]");
    assert_eq!(log_count, 0);
}
