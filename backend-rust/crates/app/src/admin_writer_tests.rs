//! Isolated-DB regressions for the user/role/menu writers (no production database URLs).
use super::*;
use crate::platform::{
    auth::{LoginRequest, RegistrationRequest},
    config::AppConfig,
};
use sqlx::{MySql, MySqlPool, Transaction};
use std::sync::Arc;
use std::time::Duration;

/// Two parents per table: `aaa-*` sorts before and `zzz-*` after every other key, so a scan from
/// the start of the clustered index towards the target passes the unrelated row first.
struct Fixture {
    state: AppState,
    pool: MySqlPool,
    tag: String,
    account: String,
}

impl Fixture {
    async fn new(max_connections: u32) -> Self {
        let url = std::env::var("RUST_PHASE2_DATABASE_URL").expect("scratch database URL");
        assert!(url.ends_with("/rust_scratch_lockdemo"));
        let pool = sqlx::mysql::MySqlPoolOptions::new()
            .max_connections(max_connections)
            .connect(&url)
            .await
            .unwrap();
        let tag = uuid::Uuid::new_v4().simple().to_string()[..12].to_owned();
        let account = format!("b4acct{tag}");
        let hash = password::hash("Lock-pass-1").unwrap();
        for side in ["aaa", "zzz"] {
            let t = &tag;
            let user_account = if side == "zzz" {
                account.clone()
            } else {
                format!("b4other{t}")
            };
            sqlx::query("INSERT INTO t_user (id,user_account,user_name,password,status) VALUES (?,?,'b4 lock regression',?,0)")
                .bind(format!("{side}-u-{t}")).bind(user_account).bind(&hash).execute(&pool).await.unwrap();
            sqlx::query("INSERT INTO t_role (id,title,is_admin,is_default) VALUES (?,'b4 lock regression',1,1)")
                .bind(format!("{side}-role-{t}")).execute(&pool).await.unwrap();
            sqlx::query("INSERT INTO t_user_role (id,user_id,role_id) VALUES (?,?,?)")
                .bind(format!("{side}-ur-{t}"))
                .bind(format!("{side}-u-{t}"))
                .bind(format!("{side}-role-{t}"))
                .execute(&pool)
                .await
                .unwrap();
            sqlx::query("INSERT INTO t_menus (id,parent_id,`key`,title,sort,is_menu,is_bread,keep_alive) VALUES (?,'-1',?,'b4 lock regression',1,0,0,0)")
                .bind(format!("{side}-m-{t}")).bind(format!("{side}{t}")).execute(&pool).await.unwrap();
            sqlx::query(
                "INSERT INTO t_menus_button (id,menus_id,title,`key`) VALUES (?,?,'old','old')",
            )
            .bind(format!("{side}-b-{t}"))
            .bind(format!("{side}-m-{t}"))
            .execute(&pool)
            .await
            .unwrap();
            sqlx::query("INSERT INTO t_role_menus (id,role_id,menu_id,per) VALUES (?,?,?,'[]')")
                .bind(format!("{side}-rm-{t}"))
                .bind(format!("{side}-role-{t}"))
                .bind(format!("{side}-m-{t}"))
                .execute(&pool)
                .await
                .unwrap();
        }
        // Account creation needs an is_default=0 role; a zzz id keeps any existing default first.
        sqlx::query(
            "INSERT INTO t_role (id,title,is_admin,is_default) VALUES (?,'b4 lock default',1,0)",
        )
        .bind(format!("zzz-default-{tag}"))
        .execute(&pool)
        .await
        .unwrap();
        let state = AppState {
            config: Arc::new(AppConfig::from_env().unwrap()),
            pool: Some(pool.clone()),
            database_ready: Arc::new(std::sync::atomic::AtomicBool::new(true)),
        };
        Self {
            state,
            pool,
            tag,
            account,
        }
    }

    fn id(&self, kind: &str, side: &str) -> String {
        format!("{side}-{kind}-{}", self.tag)
    }

    async fn cleanup(self) {
        let tag = format!("%{}%", self.tag);
        let created: Vec<String> = sqlx::query_scalar(
            "SELECT id FROM t_user WHERE id LIKE ? OR user_account LIKE ? ORDER BY id",
        )
        .bind(&tag)
        .bind(&tag)
        .fetch_all(&self.pool)
        .await
        .unwrap();
        for user in &created {
            for id in sqlx::query_scalar::<_, String>(
                "SELECT id FROM t_user_role WHERE user_id=? ORDER BY id",
            )
            .bind(user)
            .fetch_all(&self.pool)
            .await
            .unwrap()
            {
                sqlx::query("DELETE FROM t_user_role WHERE id=?")
                    .bind(id)
                    .execute(&self.pool)
                    .await
                    .unwrap();
            }
            sqlx::query("DELETE FROM t_user WHERE id=?")
                .bind(user)
                .execute(&self.pool)
                .await
                .unwrap();
        }
        for (table, column) in [
            ("t_menus_button", "menus_id"),
            ("t_role_menus", "role_id"),
            ("t_user_role", "user_id"),
        ] {
            for id in sqlx::query_scalar::<_, String>(&format!(
                "SELECT id FROM {table} WHERE id LIKE ? OR {column} LIKE ? ORDER BY id"
            ))
            .bind(&tag)
            .bind(&tag)
            .fetch_all(&self.pool)
            .await
            .unwrap()
            {
                sqlx::query(&format!("DELETE FROM {table} WHERE id=?"))
                    .bind(id)
                    .execute(&self.pool)
                    .await
                    .unwrap();
            }
        }
        for table in ["t_menus", "t_role"] {
            for id in sqlx::query_scalar::<_, String>(&format!(
                "SELECT id FROM {table} WHERE id LIKE ? ORDER BY id"
            ))
            .bind(&tag)
            .fetch_all(&self.pool)
            .await
            .unwrap()
            {
                sqlx::query(&format!("DELETE FROM {table} WHERE id=?"))
                    .bind(id)
                    .execute(&self.pool)
                    .await
                    .unwrap();
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

fn json_body<T: serde::de::DeserializeOwned>(value: Value) -> Json<T> {
    Json(serde_json::from_value(value).unwrap())
}

async fn invoke(state: AppState, op: &str, fixture: (String, String)) -> Response {
    let (tag, account) = fixture;
    let id = |kind: &str| format!("zzz-{kind}-{tag}");
    match op {
        "user-role" => add_user_role(State(state), Json(json!({"userId": id("u"), "roleIds": [id("role")]}))).await,
        "user-delete" => {
            delete_user(State(state), Query(HashMap::from([("userId".to_owned(), id("u"))]))).await
        }
        "user-update" => save_user(State(state), Json(json!({"id": id("u"), "userAccount": account, "userName": "b4 renamed", "idCard": "110101199001011234", "userSex": 1, "eday": "2030-01-01"}))).await,
        "menu-edit" => add_menu(State(state), Json(json!({"menus": {"id": id("m"), "parentId": "-1", "key": "edited", "title": "edited", "sort": 2}, "permissions": [{"title": "new", "key": "new"}]}))).await,
        "role-edit" => add_role(State(state), Json(json!({"role": {"id": id("role"), "title": "edited", "remark": "", "isAdmin": 1, "isDefault": 1}, "menus": [format!("{{\"menusId\":\"{}\",\"per\":[]}}", id("m"))]}))).await,
        "login" => crate::system::user_login(State(state), json_body::<LoginRequest>(json!({"userAccount": account, "password": "Lock-pass-1", "deviceId": "b4-lock"}))).await,
        "signin" => crate::system::user_signin(State(state), json_body::<RegistrationRequest>(json!({"userAccount": format!("b4new{tag}"), "userName": "b4 signin", "idCard": "110101199001011234", "password": "Lock-pass-2", "userSex": 1}))).await,
        _ => panic!("unknown operation {op}"),
    }
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

async fn waiting(
    pool: &MySqlPool,
    connection: u64,
    table: &str,
    writers: i64,
    limit: Duration,
) -> bool {
    tokio::time::timeout(limit, async {
        loop {
            let count: i64 = sqlx::query_scalar("SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID JOIN performance_schema.threads t ON t.THREAD_ID=b.THREAD_ID WHERE t.PROCESSLIST_ID=? AND b.OBJECT_NAME=?")
                .bind(connection).bind(table).fetch_one(pool).await.unwrap();
            if count >= writers {
                break;
            }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    })
    .await
    .is_ok()
}

/// Every unrelated parent and child row stays writable, and a new child row can be inserted
/// between them, while the writer is parked on its own target row.
async fn unrelated_rows_writable(fixture: &Fixture) -> bool {
    let mut other = fixture.pool.begin().await.unwrap();
    sqlx::query("SET innodb_lock_wait_timeout=1")
        .execute(&mut *other)
        .await
        .unwrap();
    let mut writable = true;
    for (table, kind) in [
        ("t_user", "u"),
        ("t_user_role", "ur"),
        ("t_role", "role"),
        ("t_role_menus", "rm"),
        ("t_menus", "m"),
        ("t_menus_button", "b"),
    ] {
        writable &= sqlx::query(&format!("UPDATE {table} SET id=id WHERE id=?"))
            .bind(fixture.id(kind, "aaa"))
            .execute(&mut *other)
            .await
            .is_ok();
    }
    let middle = format!("mmm-{}", fixture.tag);
    for statement in [
        "INSERT INTO t_user_role (id,user_id,role_id) VALUES (?,'b4-gap','b4-gap')",
        "INSERT INTO t_menus_button (id,menus_id,title,`key`) VALUES (?,'b4-gap','gap','gap')",
        "INSERT INTO t_role_menus (id,role_id,menu_id,per) VALUES (?,'b4-gap','b4-gap','[]')",
    ] {
        writable &= sqlx::query(statement)
            .bind(&middle)
            .execute(&mut *other)
            .await
            .is_ok();
    }
    other.rollback().await.unwrap();
    writable
}

async fn isolation(op: &'static str, table: &str, kind: &str, reach: Duration) {
    let fixture = Fixture::new(8).await;
    let (gate, connection) = gate(&fixture.pool, table, &fixture.id(kind, "zzz")).await;
    let state = fixture.state.clone();
    let key = (fixture.tag.clone(), fixture.account.clone());
    let writer = tokio::spawn(async move { invoke(state, op, key).await });
    let reached = waiting(&fixture.pool, connection, table, 1, reach).await;
    let writable = unrelated_rows_writable(&fixture).await;
    gate.commit().await.unwrap();
    let result = document(
        tokio::time::timeout(Duration::from_secs(60), writer)
            .await
            .unwrap()
            .unwrap(),
    )
    .await;
    fixture.cleanup().await;
    assert!(reached, "{op} did not reach the {table} target gate");
    assert!(writable, "{op} locked rows of another parent");
    assert_eq!(result["code"], 200, "{op}: {result}");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn user_role_writers_lock_only_their_user() {
    // addUserRole and delete replace the role set by primary key after the user lock.
    for op in ["user-role", "user-delete"] {
        isolation(op, "t_user_role", "ur", Duration::from_secs(5)).await;
    }
    isolation("user-update", "t_user", "u", Duration::from_secs(5)).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn menu_and_role_writers_lock_only_their_parent() {
    isolation("menu-edit", "t_menus_button", "b", Duration::from_secs(5)).await;
    isolation("role-edit", "t_role_menus", "rm", Duration::from_secs(5)).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn login_locks_only_the_matched_user_after_verification() {
    // The plain read and PBKDF2 check run unlocked; only the matched PK is locked afterwards.
    isolation("login", "t_user", "u", Duration::from_secs(60)).await;
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn same_user_role_replacements_serialize_on_the_user() {
    let fixture = Fixture::new(8).await;
    let (gate, connection) = gate(&fixture.pool, "t_user", &fixture.id("u", "zzz")).await;
    let mut writers = Vec::new();
    for role in ["aaa", "zzz"] {
        let state = fixture.state.clone();
        let body = json!({"userId": fixture.id("u", "zzz"), "roleIds": [fixture.id("role", role), fixture.id("default", "zzz")]});
        writers.push(tokio::spawn(async move {
            add_user_role(State(state), Json(body)).await
        }));
    }
    let reached = waiting(
        &fixture.pool,
        connection,
        "t_user",
        2,
        Duration::from_secs(5),
    )
    .await;
    gate.commit().await.unwrap();
    let mut codes = Vec::new();
    for writer in writers {
        codes.push(
            document(
                tokio::time::timeout(Duration::from_secs(10), writer)
                    .await
                    .unwrap()
                    .unwrap(),
            )
            .await["code"]
                .clone(),
        );
    }
    let roles: Vec<String> =
        sqlx::query_scalar("SELECT role_id FROM t_user_role WHERE user_id=? ORDER BY role_id")
            .bind(fixture.id("u", "zzz"))
            .fetch_all(&fixture.pool)
            .await
            .unwrap();
    let expected_last = |role: &str| {
        let mut set = vec![fixture.id("role", role), fixture.id("default", "zzz")];
        set.sort();
        set
    };
    let complete_set = roles == expected_last("aaa") || roles == expected_last("zzz");
    fixture.cleanup().await;
    assert!(
        reached,
        "both replacements must wait on the user row before touching children"
    );
    assert_eq!(codes, vec![json!(200), json!(200)]);
    assert!(
        complete_set,
        "one complete replacement must win, got {roles:?}"
    );
}

async fn named_lock_waiter(
    fixture: &Fixture,
    prefix: &str,
    ops: &[&'static str],
) -> (bool, Vec<Value>) {
    let mut holder = fixture.pool.acquire().await.unwrap();
    let held: Option<i64> = sqlx::query_scalar(
        "SELECT GET_LOCK(CONCAT(?, LEFT(SHA2(DATABASE(), 256), 64 - CHAR_LENGTH(?))), 5)",
    )
    .bind(prefix)
    .bind(prefix)
    .fetch_one(&mut *holder)
    .await
    .unwrap();
    assert_eq!(held, Some(1));
    let mut writers = Vec::new();
    for &op in ops {
        let state = fixture.state.clone();
        let key = (fixture.tag.clone(), fixture.account.clone());
        writers.push(tokio::spawn(async move { invoke(state, op, key).await }));
    }
    let waiting = tokio::time::timeout(Duration::from_secs(60), async {
        loop {
            let count: i64 = sqlx::query_scalar(
                "SELECT COUNT(*) FROM performance_schema.metadata_locks WHERE OBJECT_TYPE='USER LEVEL LOCK' AND LOCK_STATUS='PENDING'",
            )
            .fetch_one(&fixture.pool)
            .await
            .unwrap();
            if count >= ops.len() as i64 {
                break;
            }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    })
    .await
    .is_ok();
    sqlx::query("SELECT RELEASE_ALL_LOCKS()")
        .execute(&mut *holder)
        .await
        .unwrap();
    let mut results = Vec::new();
    for writer in writers {
        results.push(
            document(
                tokio::time::timeout(Duration::from_secs(60), writer)
                    .await
                    .unwrap()
                    .unwrap(),
            )
            .await,
        );
    }
    (waiting, results)
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn account_creators_serialize_duplicate_check_and_insert() {
    let fixture = Fixture::new(8).await;
    let (waiting, results) =
        named_lock_waiter(&fixture, "user-account:", &["signin", "signin"]).await;
    let created: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_user WHERE user_account=?")
        .bind(format!("b4new{}", fixture.tag))
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
    fixture.cleanup().await;
    let mut codes: Vec<_> = results
        .iter()
        .map(|result| result["code"].clone())
        .collect();
    codes.sort_by_key(|code| code.as_i64());
    assert!(
        waiting,
        "both signins must wait on the account lock before the duplicate check"
    );
    assert_eq!(codes, vec![json!(200), json!(500)], "{results:?}");
    assert_eq!(created, 1);
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo and performance_schema"]
async fn role_writes_wait_on_the_role_catalogue_lock() {
    let fixture = Fixture::new(8).await;
    let (waiting, results) = named_lock_waiter(&fixture, "role-catalogue:", &["role-edit"]).await;
    fixture.cleanup().await;
    assert!(waiting, "addRole must serialize on the role catalogue lock");
    assert_eq!(results[0]["code"], 200, "{results:?}");
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn writers_never_reenter_a_single_connection_pool() {
    let fixture = Fixture::new(1).await;
    let key = (fixture.tag.clone(), fixture.account.clone());
    let mut codes = Vec::new();
    for op in [
        "user-update",
        "user-role",
        "menu-edit",
        "role-edit",
        "signin",
        "login",
        "user-delete",
    ] {
        let response = tokio::time::timeout(
            Duration::from_secs(60),
            invoke(fixture.state.clone(), op, key.clone()),
        )
        .await
        .unwrap_or_else(|_| panic!("{op} deadlocked a one-connection pool"));
        codes.push((op, document(response).await["code"].clone()));
    }
    let created = fixture.state.clone();
    let import = tokio::time::timeout(
        Duration::from_secs(60),
        import_user(State(created), Json(json!([{"userAccount": format!("b4imp{}", fixture.tag), "idCard": "110101199001019999", "password": "Import-1", "status": 0}]))),
    )
    .await
    .expect("importUser deadlocked a one-connection pool");
    codes.push(("import", document(import).await["code"].clone()));
    fixture.cleanup().await;
    assert!(
        codes.iter().all(|(_, code)| code == &json!(200)),
        "{codes:?}"
    );
}

#[tokio::test]
#[ignore = "requires isolated rust_scratch_lockdemo"]
async fn import_rejection_rolls_back_rows_already_inserted() {
    let fixture = Fixture::new(4).await;
    let first = format!("b4imp1{}", fixture.tag);
    let response = import_user(
        State(fixture.state.clone()),
        Json(json!([
            {"userAccount": first, "idCard": "110101199001018888", "password": "Import-1", "status": 0},
            {"userAccount": format!("b4imp2{}", fixture.tag), "idCard": "110101199001017777"}
        ])),
    )
    .await;
    let result = document(response).await;
    let created: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM t_user WHERE user_account=?")
        .bind(&first)
        .fetch_one(&fixture.pool)
        .await
        .unwrap();
    fixture.cleanup().await;
    assert_eq!(result["code"], 202, "{result}");
    assert_eq!(
        created, 0,
        "the first row must be rolled back with the rejected batch"
    );
}
