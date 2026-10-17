//! Isolated-DB regressions for the simulation (仿真) REST endpoints.
//!
//! The simulation tables are indexed by primary key only, so a locking read, UPDATE or DELETE
//! filtered by `room_id`/`user_id` next-key locks every row it scans, other rooms' rows included.
//! Writers must lock the room row by primary key, read child ids plainly and write by primary
//! key; readers must not lock at all. Each writer test keeps an earlier, unrelated room's rows
//! X-locked (so any scan from the start of a clustered index collides with them, after a 2 s
//! lock wait timeout) while the endpoint works on the later room, using the same user ids in
//! both rooms.
use crate::domain::simulation::simulation::*;
use crate::platform::auth::AuthUser;
use crate::platform::config::AppConfig;
use crate::platform::state::AppState;
use axum::extract::Extension;
use axum::extract::Path;
use axum::extract::Query;
use axum::extract::State;
use axum::response::Response;
use axum::Json;
use serde_json::json;
use serde_json::Value;
use sqlx::MySql;
use sqlx::MySqlPool;
use sqlx::Transaction;
use std::sync::Arc;
use std::time::Duration;

const CHILD_TABLES: [&str; 4] = [
    "simulation_router_room_page_value",
    "simulation_router_room_page",
    "simulation_router_room_user",
    "simulation_router_room_content",
];

struct Fixture {
    state: AppState,
    pool: MySqlPool,
    tag: String,
    /// The earlier, unrelated room whose rows stay locked.
    unrelated: i32,
    /// The room the endpoint works on.
    target: i32,
}

impl Fixture {
    fn teacher(&self) -> String {
        format!("sim-teacher-{}", self.tag)
    }

    fn student(&self) -> String {
        format!("sim-student-{}", self.tag)
    }

    fn user(&self, id: String) -> AuthUser {
        AuthUser {
            id,
            is_admin: false,
        }
    }

    async fn new(stats: i32) -> Self {
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
        // Created first, so every row of the unrelated room sorts before the target's rows.
        fixture.unrelated = fixture.room(stats).await;
        fixture.target = fixture.room(stats).await;
        fixture
    }

    async fn room(&self, stats: i32) -> i32 {
        let room = sqlx::query("INSERT INTO simulation_router_room (name,is_cable,create_user_Id,stats,create_time,room_type,is_start_sign) VALUES (?,0,?,?,NOW(),1,1)")
            .bind(format!("simulation lock regression {}", self.tag))
            .bind(self.teacher())
            .bind(stats)
            .execute(&self.pool)
            .await
            .unwrap()
            .last_insert_id() as i32;
        sqlx::query("INSERT INTO simulation_router_room_content (room_id,content,main_signal,interference_signal,bd_type,bw_type,bw_count,is_random) VALUES (?,'',NULL,NULL,1,1,150,0)")
            .bind(room)
            .execute(&self.pool)
            .await
            .unwrap();
        for sort in 0..3 {
            sqlx::query("INSERT INTO simulation_router_room_page (id,room_id,page_number,sort,`key`) VALUES (?,?,1,?,'1234')")
                .bind(uuid::Uuid::new_v4().to_string())
                .bind(room)
                .bind(sort)
                .execute(&self.pool)
                .await
                .unwrap();
        }
        for (user, user_type) in [(self.teacher(), 0), (self.student(), 1)] {
            sqlx::query("INSERT INTO simulation_router_room_user (room_id,user_id,user_type,channel,user_status) VALUES (?,?,?,-1,0)")
                .bind(room)
                .bind(user)
                .bind(user_type)
                .execute(&self.pool)
                .await
                .unwrap();
        }
        sqlx::query("INSERT INTO simulation_router_room_page_value (page_number,room_id,value,user_id) VALUES (1,?,'[\"OLD\"]',?)")
            .bind(room)
            .bind(self.student())
            .execute(&self.pool)
            .await
            .unwrap();
        room
    }

    /// X-locks, by primary key, the room row and every child row of `room`.
    async fn hold(&self, room: i32) -> Transaction<'static, MySql> {
        let mut hold = self.pool.begin().await.unwrap();
        sqlx::query("SELECT id FROM simulation_router_room WHERE id=? FOR UPDATE")
            .bind(room)
            .fetch_one(&mut *hold)
            .await
            .unwrap();
        for table in CHILD_TABLES {
            let ids: Vec<String> = sqlx::query_scalar(&format!(
                "SELECT CAST(id AS CHAR) FROM {table} WHERE room_id=? ORDER BY id"
            ))
            .bind(room)
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

    async fn count(&self, table: &str, room: i32) -> i64 {
        sqlx::query_scalar(&format!("SELECT COUNT(*) FROM {table} WHERE room_id=?"))
            .bind(room)
            .fetch_one(&self.pool)
            .await
            .unwrap()
    }

    async fn member_status(&self, room: i32, user: &str) -> Option<i32> {
        sqlx::query_scalar(
            "SELECT user_status FROM simulation_router_room_user WHERE room_id=? AND user_id=?",
        )
        .bind(room)
        .bind(user)
        .fetch_one(&self.pool)
        .await
        .unwrap()
    }

    async fn cleanup(self) {
        for room in [self.unrelated, self.target] {
            let mut tx = self.pool.begin().await.unwrap();
            for table in CHILD_TABLES {
                delete_by_id(&mut tx, table, "room_id=?", &[], room)
                    .await
                    .unwrap();
            }
            sqlx::query("DELETE FROM simulation_router_room WHERE id=?")
                .bind(room)
                .execute(&mut *tx)
                .await
                .unwrap();
            tx.commit().await.unwrap();
        }
    }
}

async fn document(response: Response) -> Value {
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    serde_json::from_slice(&bytes).unwrap()
}

/// Runs `operation` while `held` stays locked and returns the response document.
async fn locked_beside<F, Fut>(fixture: &Fixture, held: i32, operation: F) -> Value
where
    F: FnOnce() -> Fut,
    Fut: std::future::Future<Output = Response>,
{
    let hold = fixture.hold(held).await;
    let result = tokio::time::timeout(Duration::from_secs(30), operation()).await;
    hold.rollback().await.unwrap();
    document(result.expect("endpoint did not finish")).await
}

fn room_query(room: i32) -> Query<RoomQuery> {
    Query(RoomQuery {
        room_id: Some(room),
    })
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn the_unrelated_room_lock_really_blocks_a_member_table_scan() {
    // Canary: a locking read filtered by user id scans the whole member table and so collides
    // with the held unrelated rows. Without this the other tests could pass vacuously.
    let fixture = Fixture::new(1).await;
    let hold = fixture.hold(fixture.unrelated).await;
    let scan = sqlx::query("SELECT id FROM simulation_router_room_user WHERE user_id=? FOR UPDATE")
        .bind(fixture.student())
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
async fn delete_removes_only_its_own_rows() {
    let fixture = Fixture::new(1).await;
    let (state, user, room) = (
        fixture.state.clone(),
        fixture.user(fixture.teacher()),
        fixture.target,
    );
    let result = locked_beside(&fixture, fixture.unrelated, || {
        delete(State(state), Extension(user), room_query(room))
    })
    .await;
    let mut left = 0;
    for table in CHILD_TABLES {
        left += fixture.count(table, fixture.target).await;
    }
    let room_rows: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM simulation_router_room WHERE id=?")
            .bind(fixture.target)
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
    let unrelated_users = fixture
        .count("simulation_router_room_user", fixture.unrelated)
        .await;
    let unrelated_values = fixture
        .count("simulation_router_room_page_value", fixture.unrelated)
        .await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(left, 0, "every child row of the deleted room is gone");
    assert_eq!(room_rows, 0);
    assert_eq!(unrelated_users, 2, "the unrelated room keeps its members");
    assert_eq!(unrelated_values, 1, "the unrelated room keeps its answers");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn upload_replaces_its_answers_without_scanning_other_rooms() {
    let fixture = Fixture::new(1).await;
    let (state, user, room) = (
        fixture.state.clone(),
        fixture.user(fixture.student()),
        fixture.target,
    );
    let body = json!({"roomId": room, "contentValue": ["[\"ABCD\"]", "[\"1234\"]"]});
    let result = locked_beside(&fixture, fixture.unrelated, || {
        upload_result(State(state), Extension(user), Json(body))
    })
    .await;
    let values: Vec<String> = sqlx::query_scalar(
        "SELECT value FROM simulation_router_room_page_value WHERE room_id=? ORDER BY page_number",
    )
    .bind(fixture.target)
    .fetch_all(&fixture.pool)
    .await
    .unwrap();
    let status = fixture
        .member_status(fixture.target, &fixture.student())
        .await;
    let unrelated_values = fixture
        .count("simulation_router_room_page_value", fixture.unrelated)
        .await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(
        values,
        vec!["[\"ABCD\"]".to_owned(), "[\"1234\"]".to_owned()],
        "the old answer is replaced, not kept"
    );
    assert_eq!(status, Some(1));
    assert_eq!(unrelated_values, 1, "the unrelated room keeps its answers");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn finish_marks_members_without_scanning_other_rooms() {
    let fixture = Fixture::new(1).await;
    let (state, user, room) = (
        fixture.state.clone(),
        fixture.user(fixture.teacher()),
        fixture.target,
    );
    let result = locked_beside(&fixture, fixture.unrelated, || {
        finish(State(state), Extension(user), room_query(room))
    })
    .await;
    let teacher = fixture
        .member_status(fixture.target, &fixture.teacher())
        .await;
    let student = fixture
        .member_status(fixture.target, &fixture.student())
        .await;
    let unrelated = fixture
        .member_status(fixture.unrelated, &fixture.student())
        .await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(teacher, Some(1));
    assert_eq!(
        student,
        Some(1),
        "receivers on the teacher's channel finish too"
    );
    assert_eq!(unrelated, Some(0), "the unrelated room is untouched");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn find_page_generates_a_missing_page_without_scanning_other_rooms() {
    let fixture = Fixture::new(1).await;
    let (state, user, room) = (
        fixture.state.clone(),
        fixture.user(fixture.student()),
        fixture.target,
    );
    let result = locked_beside(&fixture, fixture.unrelated, || {
        find_page(
            State(state),
            Extension(user),
            Query(PageQuery {
                room_id: Some(room),
                page_number: Some(2),
                user_id: None,
            }),
        )
    })
    .await;
    let created: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM simulation_router_room_page WHERE room_id=? AND page_number=2",
    )
    .bind(fixture.target)
    .fetch_one(&fixture.pool)
    .await
    .unwrap();
    let unrelated = fixture
        .count("simulation_router_room_page", fixture.unrelated)
        .await;
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
    assert_eq!(created, 50, "page 2 holds the remaining 50 groups");
    assert_eq!(unrelated, 3, "the unrelated room generated nothing");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn read_endpoints_do_not_wait_for_a_writer_of_the_room() {
    // A writer holds the target room (as a mutating endpoint would); readers must not queue.
    let fixture = Fixture::new(1).await;
    let hold = fixture.hold(fixture.target).await;
    let room = fixture.target;
    let teacher = fixture.user(fixture.teacher());
    let student = fixture.user(fixture.student());
    let mut codes = Vec::new();
    for response in [
        detail(
            State(fixture.state.clone()),
            Extension(student.clone()),
            room_query(room),
        )
        .await,
        detail_body(
            State(fixture.state.clone()),
            Extension(student.clone()),
            Json(json!({"roomId": room})),
        )
        .await,
        users(
            State(fixture.state.clone()),
            Extension(teacher.clone()),
            Path(room),
        )
        .await,
        users_body(
            State(fixture.state.clone()),
            Extension(teacher.clone()),
            Json(json!({"roomId": room})),
        )
        .await,
        channels(
            State(fixture.state.clone()),
            Extension(teacher),
            room_query(room),
        )
        .await,
    ] {
        codes.push(document(response).await["code"].clone());
    }
    hold.rollback().await.unwrap();
    fixture.cleanup().await;
    assert_eq!(
        codes,
        vec![json!(200); 5],
        "readers answered without waiting"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn add_room_reads_cable_floors_without_locking_them() {
    let fixture = Fixture::new(1).await;
    let cable = format!("cable-{}", fixture.tag);
    let floor = format!("floor-{}", fixture.tag);
    sqlx::query("INSERT INTO t_cable_floor (id,cable_id,floor_number,sort,mores_key) VALUES (?,?,1,0,'[\"A\",\"B\"]')")
        .bind(&floor)
        .bind(&cable)
        .execute(&fixture.pool)
        .await
        .unwrap();
    // Another session holds the shared cable floor (a cable editor, say).
    let mut hold = fixture.pool.begin().await.unwrap();
    sqlx::query("SELECT id FROM t_cable_floor WHERE id=? FOR UPDATE")
        .bind(&floor)
        .fetch_one(&mut *hold)
        .await
        .unwrap();
    let body = json!({"roomName":"cable lock regression","isCable":1,"cableId":cable,"bwCount":100,
        "startPage":1,"bdType":1,"bwType":1,"isRandom":0});
    let result = tokio::time::timeout(
        Duration::from_secs(30),
        add_disturb(
            State(fixture.state.clone()),
            Extension(fixture.user(fixture.teacher())),
            Json(body),
        ),
    )
    .await
    .expect("add_room did not finish");
    hold.rollback().await.unwrap();
    let result = document(result).await;
    if let Some(room) = result["data"].as_i64() {
        let mut tx = fixture.pool.begin().await.unwrap();
        for table in CHILD_TABLES {
            delete_by_id(&mut tx, table, "room_id=?", &[], room as i32)
                .await
                .unwrap();
        }
        sqlx::query("DELETE FROM simulation_router_room WHERE id=?")
            .bind(room)
            .execute(&mut *tx)
            .await
            .unwrap();
        tx.commit().await.unwrap();
    }
    sqlx::query("DELETE FROM t_cable_floor WHERE id=?")
        .bind(&floor)
        .execute(&fixture.pool)
        .await
        .unwrap();
    fixture.cleanup().await;
    assert_eq!(result["code"], 200, "{result}");
}
