use std::collections::HashMap;

use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::{pool::PoolConnection, Connection, MySql, MySqlPool, Row, Transaction};
use std::fmt::Display;
use uuid::Uuid;

use super::{
    envelope_json, envelope_response, password, registration_birthday, valid_account, AppState,
};
use crate::statistical::lock_user;

fn string_field(body: &Value, name: &str) -> Option<String> {
    body.get(name)
        .and_then(Value::as_str)
        .map(ToOwned::to_owned)
}

fn int_field(body: &Value, name: &str) -> Option<i32> {
    body.get(name).and_then(java_int)
}

/// Jackson's Integer coercion: JSON integers and integral numeric strings.
fn java_int(value: &Value) -> Option<i32> {
    match value {
        Value::Number(number) => number.as_i64().and_then(|value| value.try_into().ok()),
        Value::String(text) => text.trim().parse().ok(),
        _ => None,
    }
}

/// `String.valueOf` of a Jackson-bound Object: strings as-is, scalars by their JSON text.
pub(crate) fn java_string_value(value: &Value) -> String {
    match value {
        Value::String(text) => text.clone(),
        other => other.to_string(),
    }
}

const DEFAULT_AVATAR: &str = "/userImages/pag-avatar-man.png";
pub(crate) const ID_CARD_FORMAT_ERROR: &str = "身份证号码格式错误";
pub(crate) const USER_ACCOUNT_REPEAT: &str = "用户名已被注册";
pub(crate) const PASSWORD_REQUIRED: &str = "Password must not be null or empty";
pub(crate) const NO_DEFAULT_ROLE: &str = "系统未配置默认角色，无法创建用户";
const DATA_SUCCESS: &str = "数据正常";
const PROFILE_COLUMNS: &str = "id, wkno, phone, email, user_account, user_img, user_name, id_card, user_sex, update_flag, status, bday, eday, dday";

fn server_error(error: impl Display, context: &'static str) -> Response {
    tracing::warn!(error = %error, "{context}");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

/// Java's IllegalArgumentException/IllegalStateException mappers: 202 with the message repeated
/// in the description.
pub(crate) fn rejected(message: &str) -> Response {
    envelope_response(StatusCode::OK, 202, message, message)
}

/// A pooled session holding a database-scoped GET_LOCK. Closed on every exit (including
/// cancellation while waiting) so the lock cannot leak into another request; never reused.
async fn named_lock_connection(
    pool: &MySqlPool,
    prefix: &'static str,
) -> Result<PoolConnection<MySql>, sqlx::Error> {
    let mut connection = pool.acquire().await?;
    connection.close_on_drop();
    let acquired: Option<i64> = sqlx::query_scalar(
        "SELECT GET_LOCK(CONCAT(?, LEFT(SHA2(DATABASE(), 256), 64 - CHAR_LENGTH(?))), 10)",
    )
    .bind(prefix)
    .bind(prefix)
    .fetch_one(&mut *connection)
    .await?;
    if acquired != Some(1) {
        return Err(sqlx::Error::Protocol(format!("{prefix} lock unavailable")));
    }
    Ok(connection)
}

/// t_user.user_account has no unique index, so every path that creates accounts (signin,
/// saveUser, importUser) runs its duplicate check and insert under this one named lock.
pub(crate) async fn account_connection(
    pool: &MySqlPool,
) -> Result<PoolConnection<MySql>, sqlx::Error> {
    named_lock_connection(pool, "user-account:").await
}

pub(crate) async fn account_exists(
    tx: &mut Transaction<'_, MySql>,
    account: &str,
) -> Result<bool, sqlx::Error> {
    sqlx::query("SELECT 1 FROM t_user WHERE user_account = ? LIMIT 1")
        .bind(account)
        .fetch_optional(&mut **tx)
        .await
        .map(|row| row.is_some())
}

/// The signin default role is the `is_default = 0` row (inverted flag, like is_admin); Java's
/// unordered firstResult reads the clustered index, i.e. the lowest id.
pub(crate) async fn default_role_id(
    tx: &mut Transaction<'_, MySql>,
) -> Result<Option<String>, sqlx::Error> {
    sqlx::query_scalar("SELECT id FROM t_role WHERE is_default = 0 ORDER BY id LIMIT 1")
        .fetch_optional(&mut **tx)
        .await
}

pub(crate) async fn insert_user_role(
    tx: &mut Transaction<'_, MySql>,
    user_id: &str,
    role_id: &str,
) -> Result<(), sqlx::Error> {
    sqlx::query("INSERT INTO t_user_role (id, user_id, role_id) VALUES (?, ?, ?)")
        .bind(Uuid::new_v4().to_string())
        .bind(user_id)
        .bind(role_id)
        .execute(&mut **tx)
        .await
        .map(|_| ())
}

/// Replaces nothing by itself: deletes the child rows of one parent by primary key after a plain
/// discovery read. These tables are indexed by primary key only, so a DELETE filtered on the
/// parent column would lock every scanned row (and gap) of every other parent. Callers must hold
/// the lock that serializes writers of this set before the discovery read.
async fn delete_children(
    tx: &mut Transaction<'_, MySql>,
    table: &'static str,
    column: &'static str,
    parent: &str,
) -> Result<(), sqlx::Error> {
    let ids: Vec<String> = sqlx::query_scalar(&format!(
        "SELECT id FROM {table} WHERE {column} = ? ORDER BY id"
    ))
    .bind(parent)
    .fetch_all(&mut **tx)
    .await?;
    for id in ids {
        sqlx::query(&format!("DELETE FROM {table} WHERE id = ?"))
            .bind(id)
            .execute(&mut **tx)
            .await?;
    }
    Ok(())
}

/// PBKDF2 on the blocking pool so password-heavy requests do not stall the async workers.
pub(crate) async fn hash_blocking(plaintext: String) -> Result<String, &'static str> {
    tokio::task::spawn_blocking(move || password::hash(&plaintext))
        .await
        .map_err(|_| "password hash task failed")?
}

/// `UserProfile.from(entity)` of a posted (unsaved) import row.
fn posted_profile(item: &Value) -> Value {
    json!({
        "id": string_field(item, "id"),
        "wkno": string_field(item, "wkno"),
        "phone": string_field(item, "phone"),
        "email": string_field(item, "email"),
        "userAccount": string_field(item, "userAccount"),
        "userImg": string_field(item, "userImg"),
        "userName": string_field(item, "userName"),
        "idCard": string_field(item, "idCard"),
        "userSex": int_field(item, "userSex"),
        "updateFlag": string_field(item, "updateFlag"),
        "status": int_field(item, "status"),
        "bday": string_field(item, "bday"),
        "eday": string_field(item, "eday"),
        "dday": string_field(item, "dday")
    })
}

fn profile(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "wkno": row.get::<Option<String>, _>("wkno"),
        "phone": row.get::<Option<String>, _>("phone"),
        "email": row.get::<Option<String>, _>("email"),
        "userAccount": row.get::<Option<String>, _>("user_account"),
        "userImg": row.get::<Option<String>, _>("user_img"),
        "userName": row.get::<Option<String>, _>("user_name"),
        "idCard": row.get::<Option<String>, _>("id_card"),
        "userSex": row.get::<Option<i32>, _>("user_sex"),
        "updateFlag": row.get::<Option<String>, _>("update_flag"),
        "status": row.get::<Option<i32>, _>("status"),
        "bday": row.get::<Option<String>, _>("bday"),
        "eday": row.get::<Option<String>, _>("eday"),
        "dday": row.get::<Option<String>, _>("dday")
    })
}

fn bad_account() -> Response {
    envelope_response(
        StatusCode::OK,
        500,
        "账户格式不正确，最少4位最多32位，只能是数字或字母或汉字",
        "",
    )
}

pub(crate) async fn save_user(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(account) = string_field(&body, "userAccount").filter(|value| valid_account(value))
    else {
        return bad_account();
    };
    let id_card = string_field(&body, "idCard");
    let Some(bday) = id_card.as_deref().and_then(registration_birthday) else {
        return envelope_response(StatusCode::OK, 500, ID_CARD_FORMAT_ERROR, "");
    };
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match string_field(&body, "id").filter(|value| !value.is_empty()) {
        Some(user_id) => update_user(pool, &user_id, &body, &account, id_card, &bday).await,
        None => create_user(pool, &body, &account, id_card, &bday).await,
    }
}

// UserService.handleExistingUser: only the profile fields below change; the password, status,
// contact fields and session columns are left alone, and the account is not re-checked for
// duplicates (Java does not either).
async fn update_user(
    pool: &MySqlPool,
    user_id: &str,
    body: &Value,
    account: &str,
    id_card: Option<String>,
    bday: &str,
) -> Response {
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return server_error(error, "admin user update transaction could not start"),
    };
    match lock_user(&mut tx, user_id).await {
        Ok(true) => {}
        Ok(false) => return rejected("未查询到该用户"),
        Err(error) => return server_error(error, "admin user update lock failed"),
    }
    if let Err(error) = sqlx::query(
        "UPDATE t_user SET user_account = ?, user_name = ?, id_card = ?, user_sex = ?, bday = ?, eday = ?, user_img = COALESCE(user_img, ?) WHERE id = ?",
    )
    .bind(account)
    .bind(string_field(body, "userName"))
    .bind(id_card)
    .bind(int_field(body, "userSex"))
    .bind(bday)
    .bind(string_field(body, "eday"))
    .bind(DEFAULT_AVATAR)
    .bind(user_id)
    .execute(&mut *tx)
    .await
    {
        return server_error(error, "admin user update failed");
    }
    commit_with_profile(tx, user_id).await
}

// UserService.handleNewUser: the account check, hash, insert and default-role assignment form one
// transaction under the account lock. Hashing happens before the lock, but a missing password is
// only reported after the duplicate check, matching Java's error precedence.
async fn create_user(
    pool: &MySqlPool,
    body: &Value,
    account: &str,
    id_card: Option<String>,
    bday: &str,
) -> Response {
    let hash = match string_field(body, "password").filter(|value| !value.is_empty()) {
        Some(plaintext) => match hash_blocking(plaintext).await {
            Ok(hash) => Some(hash),
            Err(error) => return server_error(error, "admin user password hash failed"),
        },
        None => None,
    };
    let mut connection = match account_connection(pool).await {
        Ok(connection) => connection,
        Err(error) => return server_error(error, "admin user account lock failed"),
    };
    let mut tx = match connection.begin().await {
        Ok(tx) => tx,
        Err(error) => return server_error(error, "admin user create transaction could not start"),
    };
    match account_exists(&mut tx, account).await {
        Ok(false) => {}
        Ok(true) => return envelope_response(StatusCode::OK, 500, USER_ACCOUNT_REPEAT, ""),
        Err(error) => return server_error(error, "admin user duplicate query failed"),
    }
    let Some(hash) = hash else {
        return rejected(PASSWORD_REQUIRED);
    };
    let user_id = Uuid::new_v4().to_string();
    // Java persists the posted entity, so contact fields survive; status, birthday and password are
    // server-decided and the session columns are never taken from the request.
    if let Err(error) = sqlx::query(
        "INSERT INTO t_user (id, wkno, phone, email, user_account, user_img, user_name, id_card, user_sex, password, update_flag, status, bday, eday, dday) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)",
    )
    .bind(&user_id)
    .bind(string_field(body, "wkno"))
    .bind(string_field(body, "phone"))
    .bind(string_field(body, "email"))
    .bind(account)
    .bind(string_field(body, "userImg").unwrap_or_else(|| DEFAULT_AVATAR.into()))
    .bind(string_field(body, "userName"))
    .bind(id_card)
    .bind(int_field(body, "userSex"))
    .bind(hash)
    .bind(string_field(body, "updateFlag"))
    .bind(bday)
    .bind(string_field(body, "eday"))
    .bind(string_field(body, "dday"))
    .execute(&mut *tx)
    .await
    {
        return server_error(error, "admin user insert failed");
    }
    match default_role_id(&mut tx).await {
        Ok(Some(role_id)) => {
            if let Err(error) = insert_user_role(&mut tx, &user_id, &role_id).await {
                return server_error(error, "admin default role assignment failed");
            }
        }
        Ok(None) => return rejected(NO_DEFAULT_ROLE),
        Err(error) => return server_error(error, "admin default role query failed"),
    }
    commit_with_profile(tx, &user_id).await
}

async fn commit_with_profile(mut tx: Transaction<'_, MySql>, user_id: &str) -> Response {
    let row = match sqlx::query(&format!(
        "SELECT {PROFILE_COLUMNS} FROM t_user WHERE id = ?"
    ))
    .bind(user_id)
    .fetch_one(&mut *tx)
    .await
    {
        Ok(row) => row,
        Err(error) => return server_error(error, "admin user reload failed"),
    };
    if let Err(error) = tx.commit().await {
        return server_error(error, "admin user transaction commit failed");
    }
    envelope_json(StatusCode::OK, 200, Some(profile(&row)), "ok", "")
}

pub(crate) async fn get_all_user(
    State(state): State<AppState>,
    _body: Option<Json<Value>>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    // Keep this projection identical to Java's UserProfile DTO.  In particular,
    // do not select session credentials or the password, even transiently.
    let rows = match sqlx::query(&format!(
        "SELECT {PROFILE_COLUMNS} FROM t_user ORDER BY status DESC"
    ))
    .fetch_all(pool)
    .await
    {
        Ok(rows) => rows,
        Err(error) => {
            tracing::warn!(error = %error, "admin user list query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(Value::Array(rows.iter().map(profile).collect())),
        "ok",
        "",
    )
}

async fn reload_profile(
    pool: &MySqlPool,
    id: &str,
) -> Result<Option<sqlx::mysql::MySqlRow>, sqlx::Error> {
    sqlx::query(&format!(
        "SELECT {PROFILE_COLUMNS} FROM t_user WHERE id = ?"
    ))
    .bind(id)
    .fetch_optional(pool)
    .await
}

// UserService.importUser: rows whose account or id card already exists are skipped and returned
// as posted; every other row is stored as posted except that the password is hashed and the
// session columns stay NULL. Any row without a password rejects the whole batch.
pub(crate) async fn import_user(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(items) = body.as_array() else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut connection = match account_connection(pool).await {
        Ok(connection) => connection,
        Err(error) => return server_error(error, "admin import account lock failed"),
    };
    let mut tx = match connection.begin().await {
        Ok(tx) => tx,
        Err(error) => return server_error(error, "admin import transaction could not start"),
    };
    let mut skipped = Vec::new();
    for item in items {
        let account = string_field(item, "userAccount");
        let id_card = string_field(item, "idCard");
        let duplicate =
            match sqlx::query("SELECT 1 FROM t_user WHERE id_card = ? OR user_account = ? LIMIT 1")
                .bind(&id_card)
                .bind(&account)
                .fetch_optional(&mut *tx)
                .await
            {
                Ok(row) => row.is_some(),
                Err(error) => return server_error(error, "admin import duplicate query failed"),
            };
        if duplicate {
            skipped.push(posted_profile(item));
            continue;
        }
        let Some(plaintext) = string_field(item, "password").filter(|value| !value.is_empty())
        else {
            return rejected(PASSWORD_REQUIRED);
        };
        let hash = match hash_blocking(plaintext).await {
            Ok(hash) => hash,
            Err(error) => return server_error(error, "admin import password hash failed"),
        };
        let user_id = Uuid::new_v4().to_string();
        if let Err(error) = sqlx::query(
            "INSERT INTO t_user (id, wkno, phone, email, user_account, user_img, user_name, id_card, user_sex, password, update_flag, status, bday, eday, dday, token, device_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)",
        )
        .bind(&user_id)
        .bind(string_field(item, "wkno"))
        .bind(string_field(item, "phone"))
        .bind(string_field(item, "email"))
        .bind(&account)
        .bind(string_field(item, "userImg"))
        .bind(string_field(item, "userName"))
        .bind(&id_card)
        .bind(int_field(item, "userSex"))
        .bind(hash)
        .bind(string_field(item, "updateFlag"))
        .bind(int_field(item, "status"))
        .bind(string_field(item, "bday"))
        .bind(string_field(item, "eday"))
        .bind(string_field(item, "dday"))
        .execute(&mut *tx)
        .await
        {
            return server_error(error, "admin import user insert failed");
        }
        match default_role_id(&mut tx).await {
            Ok(Some(role_id)) => {
                if let Err(error) = insert_user_role(&mut tx, &user_id, &role_id).await {
                    return server_error(error, "admin import role insert failed");
                }
            }
            Ok(None) => {}
            Err(error) => return server_error(error, "admin import default role query failed"),
        }
    }
    if let Err(error) = tx.commit().await {
        return server_error(error, "admin import commit failed");
    }
    envelope_json(StatusCode::OK, 200, Some(Value::Array(skipped)), "ok", "")
}

// UserController.addUserRole: Java stringifies every posted role id and treats an empty user id
// or an empty/non-list roleIds as a successful no-op (the structure page posts userId:'' right
// after creating a user).
pub(crate) async fn add_user_role(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(user_id) = body
        .get("userId")
        .filter(|value| !value.is_null())
        .map(java_string_value)
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    // ToolUtil.objToList returns null for anything but a list; an empty user id short-circuits
    // before that null is dereferenced, otherwise the caught NPE becomes 500 "数据异常".
    let role_ids: Option<Vec<String>> = body
        .get("roleIds")
        .and_then(Value::as_array)
        .map(|ids| ids.iter().map(java_string_value).collect());
    if user_id.is_empty() {
        return envelope_json(StatusCode::OK, 200, Some(json!(DATA_SUCCESS)), "ok", "");
    }
    let Some(role_ids) = role_ids else {
        return envelope_response(StatusCode::OK, 500, "数据异常", "");
    };
    if role_ids.is_empty() {
        return envelope_json(StatusCode::OK, 200, Some(json!(DATA_SUCCESS)), "ok", "");
    }
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<(), sqlx::Error> = async {
        let mut tx = pool.begin().await?;
        // Java does not require the user to exist; the lock still serializes this user's role set
        // against delete and other role replacements before the plain discovery read.
        lock_user(&mut tx, &user_id).await?;
        delete_children(&mut tx, "t_user_role", "user_id", &user_id).await?;
        for role_id in &role_ids {
            insert_user_role(&mut tx, &user_id, role_id).await?;
        }
        tx.commit().await
    }
    .await;
    match result {
        Ok(()) => envelope_json(StatusCode::OK, 200, Some(json!(DATA_SUCCESS)), "ok", ""),
        Err(error) => {
            tracing::warn!(error = %error, "admin user role replacement failed");
            envelope_response(StatusCode::OK, 500, "数据异常", "")
        }
    }
}

pub(crate) async fn delete_user(
    State(state): State<AppState>,
    Query(query): Query<HashMap<String, String>>,
) -> Response {
    let Some(user_id) = query.get("userId").filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<bool, sqlx::Error> = async {
        let mut tx = pool.begin().await?;
        lock_user(&mut tx, user_id).await?;
        delete_children(&mut tx, "t_user_role", "user_id", user_id).await?;
        let deleted = sqlx::query("DELETE FROM t_user WHERE id = ?")
            .bind(user_id)
            .execute(&mut *tx)
            .await?
            .rows_affected()
            == 1;
        tx.commit().await?;
        Ok(deleted)
    }
    .await;
    match result {
        Ok(deleted) => envelope_json(StatusCode::OK, 200, Some(json!(deleted)), "ok", ""),
        Err(error) => server_error(error, "delete user failed"),
    }
}

pub(crate) async fn reset_password(
    State(state): State<AppState>,
    Query(query): Query<HashMap<String, String>>,
) -> Response {
    let Some(user_id) = query.get("userId").filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let temporary = "123456";
    let Ok(hash) = password::hash(temporary) else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE t_user SET password = ? WHERE id = ?")
        .bind(hash)
        .bind(user_id)
        .execute(pool)
        .await
    {
        // sqlx reports matched rows (FOUND_ROWS), so zero means no such user; Java throws
        // IllegalArgumentException("未查询到该用户"), which the mapper turns into 202.
        Ok(result) if result.rows_affected() >= 1 => {
            envelope_json(StatusCode::OK, 200, Some(json!(temporary)), "ok", "")
        }
        Ok(_) => envelope_response(StatusCode::OK, 202, "未查询到该用户", "未查询到该用户"),
        Err(error) => {
            tracing::warn!(error = %error, "reset password failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

// RoleService.addRole. All role writes hold one database-scoped named lock: the "single default
// role" invariant spans unindexed rows, and the role-menu set is replaced by discovery plus
// primary-key deletes inside the same serialized section.
pub(crate) async fn add_role(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(role) = body.get("role").and_then(Value::as_object) else {
        return rejected("角色信息不能为空");
    };
    // Java parses every entry before it can be stored (Gson map, then menusId/per toString);
    // anything it would fail on with an NPE is rejected here before any write.
    let Some(menus) = body.get("menus").and_then(Value::as_array) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let mut role_menus = Vec::with_capacity(menus.len());
    for menu in menus {
        let parsed = match menu {
            Value::String(text) => serde_json::from_str::<Value>(text).ok(),
            other => Some(other.clone()),
        };
        let entry = parsed.as_ref().and_then(|parsed| {
            let menu_id = parsed.get("menusId").filter(|value| !value.is_null())?;
            let per = parsed.get("per").filter(|value| !value.is_null())?;
            Some((java_string_value(menu_id), stored_per(per)))
        });
        let Some(entry) = entry else {
            return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
        };
        role_menus.push(entry);
    }
    let role_id = role
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| Uuid::new_v4().to_string());
    let title = role.get("title").and_then(Value::as_str);
    let remark = role.get("remark").and_then(Value::as_str);
    let is_admin = role.get("isAdmin").and_then(java_int);
    let is_default = role.get("isDefault").and_then(java_int);
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result: Result<(), sqlx::Error> = async {
        let mut connection = named_lock_connection(pool, "role-catalogue:").await?;
        let mut tx = connection.begin().await?;
        // An ordinary (isAdmin=1) role becoming the signin default demotes the previous defaults.
        if is_admin == Some(1) && is_default == Some(0) {
            let defaults: Vec<String> =
                sqlx::query_scalar("SELECT id FROM t_role WHERE is_default = 0 ORDER BY id")
                    .fetch_all(&mut *tx)
                    .await?;
            for id in defaults {
                sqlx::query("UPDATE t_role SET is_default = 1 WHERE id = ?")
                    .bind(id)
                    .execute(&mut *tx)
                    .await?;
            }
        }
        sqlx::query("INSERT INTO t_role (id, title, remark, is_admin, is_default) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE title=VALUES(title), remark=VALUES(remark), is_admin=VALUES(is_admin), is_default=VALUES(is_default)")
            .bind(&role_id)
            .bind(title)
            .bind(remark)
            .bind(is_admin)
            .bind(is_default)
            .execute(&mut *tx)
            .await?;
        delete_children(&mut tx, "t_role_menus", "role_id", &role_id).await?;
        for (menu_id, per) in &role_menus {
            sqlx::query("INSERT INTO t_role_menus (id, role_id, menu_id, per) VALUES (?, ?, ?, ?)")
                .bind(Uuid::new_v4().to_string())
                .bind(&role_id)
                .bind(menu_id)
                .bind(per)
                .execute(&mut *tx)
                .await?;
        }
        tx.commit().await
    }
    .await;
    if let Err(error) = result {
        return server_error(error, "admin role save failed");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"id": role_id, "title": title, "remark": remark, "isAdmin": is_admin, "isDefault": is_default}),
        ),
        "ok",
        "",
    )
}

/// Java stores `map.get("per").toString()`. A string is stored as-is and `[]` as `[]`, like Java;
/// other values are stored as JSON because Java's `[add, edit]` cannot be read back by the role
/// form's `JSON.parse(m.per)` (registered as a deliberate difference, not replicated).
fn stored_per(per: &Value) -> String {
    match per {
        Value::String(text) => text.clone(),
        other => other.to_string(),
    }
}

// MenusService.addMenus: a new menu is persisted with the entity defaults (isMenu/isBread/keepAlive
// 0 when absent); an edit locks the existing row and changes only the fields Java copies. The
// button set is replaced after the menu row is locked.
pub(crate) async fn add_menu(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(permissions) = body.get("permissions").and_then(Value::as_array) else {
        return rejected("permissions 缺失，拒绝编辑菜单权限");
    };
    let Some(menu) = body.get("menus").and_then(Value::as_object) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let text = |name: &str| {
        menu.get(name)
            .and_then(Value::as_str)
            .map(ToOwned::to_owned)
    };
    let number = |name: &str| menu.get(name).and_then(java_int);
    // Entity defaults apply only to absent fields; an explicit null is stored as NULL.
    let defaulted = |name: &str| match menu.get(name) {
        None => Some(0),
        Some(value) => java_int(value),
    };
    let requested_id = text("id").filter(|value| !value.is_empty());
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return server_error(error, "admin menu transaction could not start"),
    };
    let menu_id = match requested_id {
        Some(menu_id) => {
            match sqlx::query("SELECT id FROM t_menus WHERE id = ? FOR UPDATE")
                .bind(&menu_id)
                .fetch_optional(&mut *tx)
                .await
            {
                Ok(Some(_)) => {}
                Ok(None) => return rejected(&format!("菜单不存在: {menu_id}")),
                Err(error) => return server_error(error, "admin menu lock failed"),
            }
            if let Err(error) = sqlx::query("UPDATE t_menus SET parent_id = ?, component = ?, `key` = ?, path = ?, name = ?, icon = ?, title = ?, sort = ? WHERE id = ?")
                .bind(text("parentId"))
                .bind(text("component"))
                .bind(text("key"))
                .bind(text("path"))
                .bind(text("name"))
                .bind(text("icon"))
                .bind(text("title"))
                .bind(number("sort"))
                .bind(&menu_id)
                .execute(&mut *tx)
                .await
            {
                return server_error(error, "admin menu update failed");
            }
            menu_id
        }
        None => {
            let menu_id = Uuid::new_v4().to_string();
            if let Err(error) = sqlx::query("INSERT INTO t_menus (id, parent_id, `key`, path, name, icon, icon_f, height, title, is_menu, is_bread, component, sort, keep_alive) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .bind(&menu_id)
                .bind(text("parentId"))
                .bind(text("key"))
                .bind(text("path"))
                .bind(text("name"))
                .bind(text("icon"))
                .bind(text("iconF"))
                .bind(text("height"))
                .bind(text("title"))
                .bind(defaulted("isMenu"))
                .bind(defaulted("isBread"))
                .bind(text("component"))
                .bind(number("sort"))
                .bind(defaulted("keepAlive"))
                .execute(&mut *tx)
                .await
            {
                return server_error(error, "admin menu insert failed");
            }
            menu_id
        }
    };
    let result: Result<(Value, Vec<Value>), sqlx::Error> = async {
        delete_children(&mut tx, "t_menus_button", "menus_id", &menu_id).await?;
        let mut saved = Vec::with_capacity(permissions.len());
        for permission in permissions {
            let id = Uuid::new_v4().to_string();
            let title = permission.get("title").and_then(Value::as_str);
            let key = permission.get("key").and_then(Value::as_str);
            sqlx::query("INSERT INTO t_menus_button (id, menus_id, title, `key`) VALUES (?, ?, ?, ?)")
                .bind(&id)
                .bind(&menu_id)
                .bind(title)
                .bind(key)
                .execute(&mut *tx)
                .await?;
            saved.push(json!({"id": id, "menusId": menu_id, "title": title, "key": key}));
        }
        let row = sqlx::query("SELECT id, parent_id, `key`, path, name, icon, icon_f, height, title, is_menu, is_bread, component, sort, keep_alive FROM t_menus WHERE id = ?")
            .bind(&menu_id)
            .fetch_one(&mut *tx)
            .await?;
        Ok((menu_entity_value(&row), saved))
    }
    .await;
    let (menus, saved) = match result {
        Ok(saved) => saved,
        Err(error) => return server_error(error, "admin menu buttons replacement failed"),
    };
    if let Err(error) = tx.commit().await {
        return server_error(error, "admin menu commit failed");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"menus": menus, "permissions": saved})),
        "ok",
        "",
    )
}

fn user_summary(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "userName": row.get::<Option<String>, _>("user_name"),
        "userAccount": row.get::<Option<String>, _>("user_account"),
        "userImg": row.get::<Option<String>, _>("user_img")
    })
}

fn role_value(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "title": row.get::<Option<String>, _>("title"),
        "remark": row.get::<Option<String>, _>("remark"),
        "isAdmin": row.get::<Option<i32>, _>("is_admin"),
        "isDefault": row.get::<Option<i32>, _>("is_default")
    })
}

fn button_value(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "menusId": row.get::<Option<String>, _>("menus_id"),
        "title": row.get::<Option<String>, _>("title"),
        "key": row.get::<Option<String>, _>("key")
    })
}

fn menu_entity_value(row: &sqlx::mysql::MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "parentId": row.get::<Option<String>, _>("parent_id"),
        "key": row.get::<Option<String>, _>("key"),
        "path": row.get::<Option<String>, _>("path"),
        "name": row.get::<Option<String>, _>("name"),
        "icon": row.get::<Option<String>, _>("icon"),
        "iconF": row.get::<Option<String>, _>("icon_f"),
        "height": row.get::<Option<String>, _>("height"),
        "title": row.get::<Option<String>, _>("title"),
        "isMenu": row.get::<Option<i32>, _>("is_menu"),
        "isBread": row.get::<Option<i32>, _>("is_bread"),
        "component": row.get::<Option<String>, _>("component"),
        "sort": row.get::<Option<i32>, _>("sort"),
        "keepAlive": row.get::<Option<i32>, _>("keep_alive")
    })
}

fn role_menu_value(row: &sqlx::mysql::MySqlRow, permissions: &[Value]) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "parentId": row.get::<Option<String>, _>("parent_id"),
        "key": row.get::<Option<String>, _>("key"),
        "path": row.get::<Option<String>, _>("path"),
        "name": row.get::<Option<String>, _>("name"),
        "icon": row.get::<Option<String>, _>("icon"),
        "iconF": row.get::<Option<String>, _>("icon_f"),
        "height": row.get::<Option<String>, _>("height"),
        "title": row.get::<Option<String>, _>("title"),
        "isMenu": row.get::<Option<i32>, _>("is_menu"),
        "isBread": row.get::<Option<i32>, _>("is_bread"),
        "component": row.get::<Option<String>, _>("component"),
        "sort": row.get::<Option<i32>, _>("sort"),
        "per": row.get::<Option<String>, _>("per"),
        "permissions": permissions
    })
}

fn group_buttons(rows: Vec<sqlx::mysql::MySqlRow>) -> HashMap<String, Vec<Value>> {
    let mut grouped = HashMap::new();
    for row in rows {
        let menu_id = row.get::<String, _>("menus_id");
        grouped
            .entry(menu_id)
            .or_insert_with(Vec::new)
            .push(button_value(&row));
    }
    grouped
}

pub(crate) async fn role_menu_values(
    pool: &MySqlPool,
    role_id: &str,
) -> Result<Vec<Value>, sqlx::Error> {
    let menu_rows = sqlx::query(
        "SELECT m.id, m.parent_id, m.`key`, m.path, m.name, m.icon, m.icon_f, m.height, m.title, m.is_menu, m.is_bread, m.component, m.sort, rm.per FROM t_menus m JOIN t_role_menus rm ON rm.menu_id = m.id WHERE rm.role_id = ? ORDER BY m.sort",
    )
    .bind(role_id)
    .fetch_all(pool)
    .await?;
    let buttons = sqlx::query("SELECT id, menus_id, title, `key` FROM t_menus_button WHERE menus_id IN (SELECT menu_id FROM t_role_menus WHERE role_id = ?) ORDER BY menus_id, id")
        .bind(role_id)
        .fetch_all(pool)
        .await
        .map(group_buttons)?;
    Ok(menu_rows
        .iter()
        .map(|row| {
            let id = row.get::<String, _>("id");
            role_menu_value(row, buttons.get(&id).map(Vec::as_slice).unwrap_or(&[]))
        })
        .collect())
}

fn all_menu_tree_nodes(
    parent_id: &str,
    by_parent: &HashMap<String, Vec<super::MenuRow>>,
    permissions: &HashMap<String, Vec<Value>>,
) -> Vec<Value> {
    by_parent
        .get(parent_id)
        .map(|rows| {
            rows.iter()
                .map(|row| {
                    let children = all_menu_tree_nodes(&row.id, by_parent, permissions);
                    json!({
                        "id": row.id,
                        "parentId": Value::Null,
                        "key": row.key,
                        "path": row.path,
                        "name": row.name,
                        "meta": {
                            "icon": row.icon,
                            "iconF": row.icon_f,
                            "height": row.height,
                            "title": row.title,
                            "isMenu": row.is_menu == Some(0),
                            "isBread": row.is_bread == Some(0)
                        },
                        "component": row.component,
                        "sort": row.sort,
                        "permissions": permissions.get(&row.id).map(Vec::as_slice).unwrap_or(&[]),
                        "children": children
                    })
                })
                .collect()
        })
        .unwrap_or_default()
}

async fn all_menu_tree(pool: &MySqlPool) -> Result<Value, sqlx::Error> {
    let menu_rows = sqlx::query(
        "SELECT id, parent_id, `key`, path, name, icon, icon_f, height, title, is_menu, is_bread, component, sort FROM t_menus ORDER BY sort",
    )
    .fetch_all(pool)
    .await?;
    let button_rows =
        sqlx::query("SELECT id, menus_id, title, `key` FROM t_menus_button ORDER BY menus_id, id")
            .fetch_all(pool)
            .await?;
    let mut by_parent: HashMap<String, Vec<super::MenuRow>> = HashMap::new();
    for row in menu_rows {
        let menu = super::MenuRow {
            id: row.get("id"),
            parent_id: row
                .get::<Option<String>, _>("parent_id")
                .unwrap_or_else(|| "-1".into()),
            key: row.get("key"),
            path: row.get("path"),
            name: row.get("name"),
            icon: row.get("icon"),
            icon_f: row.get("icon_f"),
            height: row.get("height"),
            title: row.get("title"),
            is_menu: row.get("is_menu"),
            is_bread: row.get("is_bread"),
            component: row.get("component"),
            sort: row.get("sort"),
        };
        by_parent
            .entry(menu.parent_id.clone())
            .or_default()
            .push(menu);
    }
    let permissions = group_buttons(button_rows);
    Ok(json!(all_menu_tree_nodes("-1", &by_parent, &permissions)))
}

pub(crate) async fn get_user_directory(
    State(state): State<AppState>,
    _user: Extension<super::AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query(
        "SELECT id, user_name, user_account, user_img FROM t_user ORDER BY status DESC",
    )
    .fetch_all(pool)
    .await
    {
        Ok(rows) => envelope_json(
            StatusCode::OK,
            200,
            Some(Value::Array(rows.iter().map(user_summary).collect())),
            "ok",
            "",
        ),
        Err(error) => {
            tracing::warn!(error = %error, "user directory query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

fn checked_user_projection(row: &sqlx::mysql::MySqlRow, full: bool) -> Result<Value, sqlx::Error> {
    row.try_get::<&str, _>("id")?;
    for column in ["user_name", "user_account", "user_img"] {
        row.try_get::<Option<&str>, _>(column)?;
    }
    if full {
        for column in [
            "wkno",
            "phone",
            "email",
            "id_card",
            "update_flag",
            "bday",
            "eday",
            "dday",
        ] {
            row.try_get::<Option<&str>, _>(column)?;
        }
        row.try_get::<Option<i32>, _>("user_sex")?;
        row.try_get::<Option<i32>, _>("status")?;
        Ok(profile(row))
    } else {
        Ok(user_summary(row))
    }
}

fn user_query_response(result: Result<Value, sqlx::Error>) -> Response {
    match result {
        Ok(value) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Err(error) => {
            tracing::warn!(error = %error, "user query or projection failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(crate) async fn get_users_by_user_name_starting_with(
    State(state): State<AppState>,
    Json(body): Json<HashMap<String, Option<String>>>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result = async {
        // Java binds the supplied LIKE pattern verbatim; it does not append '%'.
        let rows = sqlx::query(
            "SELECT id, user_name, user_account, user_img FROM t_user WHERE user_name LIKE ?",
        )
        .bind(body.get("userName").and_then(|value| value.as_deref()))
        .fetch_all(pool)
        .await?;
        rows.iter()
            .map(|row| checked_user_projection(row, false))
            .collect::<Result<Vec<_>, sqlx::Error>>()
            .map(Value::Array)
    }
    .await;
    user_query_response(result)
}

pub(crate) async fn get_users_by_ids(
    State(state): State<AppState>,
    Json(ids): Json<Option<Vec<Option<String>>>>,
) -> Response {
    let Some(ids) = ids.filter(|ids| !ids.is_empty()) else {
        return user_query_response(Ok(json!([])));
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result = async {
        let mut query = sqlx::QueryBuilder::<sqlx::MySql>::new(
            "SELECT id, user_name, user_account, user_img FROM t_user WHERE id IN (",
        );
        let mut values = query.separated(", ");
        for id in &ids {
            values.push_bind(id.as_deref());
        }
        values.push_unseparated(")");
        let rows = query.build().fetch_all(pool).await?;
        rows.iter()
            .map(|row| checked_user_projection(row, false))
            .collect::<Result<Vec<_>, sqlx::Error>>()
            .map(Value::Array)
    }
    .await;
    user_query_response(result)
}

pub(crate) async fn get_user_by_id(
    State(state): State<AppState>,
    user: Extension<super::AuthUser>,
    Json(body): Json<HashMap<String, Option<String>>>,
) -> Response {
    if !user.0.is_admin {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(id) = body.get("userId").and_then(|value| value.as_deref()) else {
        return envelope_response(
            StatusCode::OK,
            202,
            "id to load is required for loading",
            "id to load is required for loading",
        );
    };
    match reload_profile(pool, id).await {
        Ok(Some(row)) => user_query_response(checked_user_projection(&row, true)),
        Ok(None) => envelope_response(StatusCode::OK, 202, "未查询到该用户", "未查询到该用户"),
        Err(error) => user_query_response(Err(error)),
    }
}

pub(crate) async fn get_user_info_all_by_status_desc(
    State(state): State<AppState>,
    user: Extension<super::AuthUser>,
) -> Response {
    if !user.0.is_admin {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let result = async {
        let rows = sqlx::query("SELECT u.id, u.id_card, u.phone, u.status, u.user_account, u.user_img, u.user_name, u.user_sex, r.title FROM t_user u LEFT JOIN t_user_role ur ON ur.user_id = u.id LEFT JOIN t_role r ON r.id = ur.role_id ORDER BY u.status DESC")
            .fetch_all(pool).await?;
        rows.iter().map(|row| -> Result<Value, sqlx::Error> {
            Ok(json!({
                "id": row.try_get::<String, _>("id")?,
                "idCard": row.try_get::<Option<String>, _>("id_card")?,
                "phone": row.try_get::<Option<String>, _>("phone")?,
                "status": row.try_get::<Option<i32>, _>("status")?,
                "userAccount": row.try_get::<Option<String>, _>("user_account")?,
                "userImg": row.try_get::<Option<String>, _>("user_img")?,
                "userName": row.try_get::<Option<String>, _>("user_name")?,
                "userSex": row.try_get::<Option<i32>, _>("user_sex")?,
                "title": row.try_get::<Option<String>, _>("title")?
            }))
        }).collect::<Result<Vec<_>, _>>().map(Value::Array)
    }.await;
    user_query_response(result)
}

pub(crate) async fn get_user_and_role_by_id(
    State(state): State<AppState>,
    user: Extension<super::AuthUser>,
    body: Option<Json<Value>>,
) -> Response {
    if !user.0.is_admin {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let Some(Json(body)) = body else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(user_id) = body
        .get("userId")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let user_row = match sqlx::query(
        "SELECT id, wkno, phone, email, user_account, user_img, user_name, id_card, user_sex, update_flag, status, bday, eday, dday FROM t_user WHERE id = ? LIMIT 1",
    )
    .bind(user_id)
    .fetch_optional(pool)
    .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该用户", "未查询到该用户"),
        Err(error) => {
            tracing::warn!(error = %error, user_id, "user and role profile query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let role_row = match sqlx::query(
        "SELECT r.id, r.title, r.remark, r.is_admin, r.is_default FROM t_role r JOIN t_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ? LIMIT 1",
    )
    .bind(user_id)
    .fetch_optional(pool)
    .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 500, "数据异常", ""),
        Err(error) => {
            tracing::warn!(error = %error, user_id, "user role query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({
            "user": profile(&user_row),
            "role": role_value(&role_row),
            "menus": Value::Null
        })),
        "ok",
        "",
    )
}

async fn find_users_by_role(pool: &MySqlPool, role_id: &str) -> Result<Vec<Value>, sqlx::Error> {
    let rows = sqlx::query(
        "SELECT u.id, u.user_name, u.user_account FROM t_user u JOIN t_user_role ur ON ur.user_id = u.id WHERE ur.role_id = ?",
    )
    .bind(role_id)
    .fetch_all(pool)
    .await?;
    Ok(rows
        .iter()
        .map(|row| {
            json!({
                "id": row.get::<String, _>("id"),
                "userName": row.get::<Option<String>, _>("user_name"),
                "userAccount": row.get::<Option<String>, _>("user_account")
            })
        })
        .collect())
}

pub(crate) async fn find_all_teacher(
    State(state): State<AppState>,
    _user: Extension<super::AuthUser>,
) -> Response {
    find_all_by_role(State(state), "1").await
}

pub(crate) async fn find_all_stu(
    State(state): State<AppState>,
    _user: Extension<super::AuthUser>,
) -> Response {
    find_all_by_role(State(state), "2").await
}

async fn find_all_by_role(State(state): State<AppState>, role_id: &str) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match find_users_by_role(pool, role_id).await {
        Ok(users) => envelope_json(StatusCode::OK, 200, Some(json!(users)), "ok", ""),
        Err(error) => {
            tracing::warn!(error = %error, role_id, "role user list query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(crate) async fn get_menu_by_id(
    State(state): State<AppState>,
    _user: Extension<super::AuthUser>,
    Query(query): Query<HashMap<String, String>>,
) -> Response {
    let Some(id) = query.get("id").filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let menu_row = match sqlx::query(
        "SELECT id, parent_id, `key`, path, name, icon, icon_f, height, title, is_menu, is_bread, component, sort, keep_alive FROM t_menus WHERE id = ? LIMIT 1",
    )
    .bind(id)
    .fetch_optional(pool)
    .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该菜单", "未查询到该菜单"),
        Err(error) => {
            tracing::warn!(error = %error, menu_id = id, "menu query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let permissions = match sqlx::query(
        "SELECT id, menus_id, title, `key` FROM t_menus_button WHERE menus_id = ? ORDER BY id",
    )
    .bind(id)
    .fetch_all(pool)
    .await
    {
        Ok(rows) => rows.iter().map(button_value).collect::<Vec<_>>(),
        Err(error) => {
            tracing::warn!(error = %error, menu_id = id, "menu permissions query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"menus": menu_entity_value(&menu_row), "permissions": permissions})),
        "ok",
        "",
    )
}

pub(crate) async fn get_role_by_id(
    State(state): State<AppState>,
    _user: Extension<super::AuthUser>,
    Query(query): Query<HashMap<String, String>>,
) -> Response {
    get_role_view(state, query, false).await
}

pub(crate) async fn get_role_menus_info(
    State(state): State<AppState>,
    _user: Extension<super::AuthUser>,
    Query(query): Query<HashMap<String, String>>,
) -> Response {
    get_role_view(state, query, true).await
}

async fn get_role_view(
    state: AppState,
    query: HashMap<String, String>,
    with_tree: bool,
) -> Response {
    let Some(role_id) = query.get("id").filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let role_row = match sqlx::query(
        "SELECT id, title, remark, is_admin, is_default FROM t_role WHERE id = ? LIMIT 1",
    )
    .bind(role_id)
    .fetch_optional(pool)
    .await
    {
        Ok(Some(row)) => row,
        Ok(None) => {
            return envelope_response(StatusCode::OK, 202, "未查询到该角色", "未查询到该角色")
        }
        Err(error) => {
            tracing::warn!(error = %error, role_id, "role query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let menus_checked = match role_menu_values(pool, role_id).await {
        Ok(menus) => menus,
        Err(error) => {
            tracing::warn!(error = %error, role_id, "role menu query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let data = if with_tree {
        let menus_all = match all_menu_tree(pool).await {
            Ok(menus) => menus,
            Err(error) => {
                tracing::warn!(error = %error, role_id, "all menu tree query failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
        // RoleMenusDto.menusChecked is List<MenusTwoDto>: the converted rows gain its keepAlive
        // property, which the role-menu query never populates.
        let menus_checked: Vec<Value> = menus_checked
            .into_iter()
            .map(|mut menu| {
                if let Value::Object(fields) = &mut menu {
                    fields.insert("keepAlive".into(), Value::Null);
                }
                menu
            })
            .collect();
        json!({
            "role": role_value(&role_row),
            "menusAll": menus_all,
            "menusChecked": menus_checked
        })
    } else {
        json!({"role": role_value(&role_row), "menus": menus_checked})
    };
    envelope_json(StatusCode::OK, 200, Some(data), "ok", "")
}

#[cfg(test)]
#[path = "admin_writer_tests.rs"]
mod writer_tests;
