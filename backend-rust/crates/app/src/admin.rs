use std::collections::HashMap;

use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::{MySqlPool, Row};
use uuid::Uuid;

use super::{
    envelope_json, envelope_response, password, registration_birthday, valid_account, AppState,
};

fn string_field(body: &Value, name: &str) -> Option<String> {
    body.get(name)
        .and_then(Value::as_str)
        .map(ToOwned::to_owned)
}

fn int_field(body: &Value, name: &str) -> Option<i32> {
    body.get(name)
        .and_then(Value::as_i64)
        .and_then(|value| value.try_into().ok())
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
    let Some(id_card) = string_field(&body, "idCard") else {
        return envelope_response(StatusCode::OK, 500, "身份证格式错误", "");
    };
    let Some(bday) = registration_birthday(&id_card) else {
        return envelope_response(StatusCode::OK, 500, "身份证格式错误", "");
    };
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => {
            tracing::warn!(error = %error, "admin save user transaction could not start");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let requested_id = string_field(&body, "id").filter(|value| !value.is_empty());
    let id = requested_id
        .clone()
        .unwrap_or_else(|| Uuid::new_v4().to_string());
    let user_name = string_field(&body, "userName");
    let user_sex = int_field(&body, "userSex");
    let eday = string_field(&body, "eday");
    if let Some(existing_id) = requested_id {
        let result = sqlx::query(
            "UPDATE t_user SET user_account = ?, user_name = ?, id_card = ?, user_sex = ?, bday = ?, eday = ? WHERE id = ?",
        )
        .bind(&account)
        .bind(&user_name)
        .bind(&id_card)
        .bind(user_sex)
        .bind(&bday)
        .bind(&eday)
        .bind(existing_id)
        .execute(&mut *tx)
        .await;
        if let Err(error) = result {
            let _ = tx.rollback().await;
            tracing::warn!(error = %error, "admin user update failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    } else {
        let Some(plaintext) = string_field(&body, "password").filter(|value| !value.is_empty())
        else {
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
        };
        let hash = match password::hash(&plaintext) {
            Ok(hash) => hash,
            Err(error) => {
                let _ = tx.rollback().await;
                tracing::warn!(error, "admin user password hash failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
        let image = string_field(&body, "userImg")
            .unwrap_or_else(|| "/userImages/pag-avatar-man.png".into());
        if let Err(error) = sqlx::query(
            "INSERT INTO t_user (id, user_account, user_img, user_name, id_card, user_sex, password, status, bday, eday) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
        )
        .bind(&id)
        .bind(&account)
        .bind(&image)
        .bind(&user_name)
        .bind(&id_card)
        .bind(user_sex)
        .bind(hash)
        .bind(&bday)
        .bind(&eday)
        .execute(&mut *tx)
        .await
        {
            let _ = tx.rollback().await;
            tracing::warn!(error = %error, "admin user insert failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
        let role = sqlx::query("SELECT id FROM t_role WHERE is_default = 0 ORDER BY id LIMIT 1")
            .fetch_optional(&mut *tx)
            .await;
        match role {
            Ok(Some(role)) => {
                if let Err(error) =
                    sqlx::query("INSERT INTO t_user_role (id, user_id, role_id) VALUES (?, ?, ?)")
                        .bind(Uuid::new_v4().to_string())
                        .bind(&id)
                        .bind(role.get::<String, _>("id"))
                        .execute(&mut *tx)
                        .await
                {
                    let _ = tx.rollback().await;
                    tracing::warn!(error = %error, "admin default role assignment failed");
                    return envelope_response(
                        StatusCode::INTERNAL_SERVER_ERROR,
                        500,
                        "服务器错误",
                        "",
                    );
                }
            }
            Ok(None) | Err(_) => {
                let _ = tx.rollback().await;
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        }
    }
    if let Err(error) = tx.commit().await {
        tracing::warn!(error = %error, "admin user transaction commit failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    match reload_profile(pool, &id).await {
        Ok(Some(row)) => envelope_json(StatusCode::OK, 200, Some(profile(&row)), "ok", ""),
        Ok(None) | Err(_) => {
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
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
    let rows = match sqlx::query(
        "SELECT id, wkno, phone, email, user_account, user_img, user_name, id_card, user_sex, update_flag, status, bday, eday, dday FROM t_user ORDER BY status DESC",
    )
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
    sqlx::query("SELECT id, wkno, phone, email, user_account, user_img, user_name, id_card, user_sex, update_flag, status, bday, eday, dday FROM t_user WHERE id = ?")
        .bind(id)
        .fetch_optional(pool)
        .await
}

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
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let default_role =
        match sqlx::query("SELECT id FROM t_role WHERE is_default = 0 ORDER BY id LIMIT 1")
            .fetch_optional(&mut *tx)
            .await
        {
            Ok(row) => row.map(|row| row.get::<String, _>("id")),
            Err(error) => {
                let _ = tx.rollback().await;
                tracing::warn!(error = %error, "admin import default role query failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
    let mut created = Vec::new();
    for item in items {
        let Some(account) = string_field(item, "userAccount").filter(|value| valid_account(value))
        else {
            continue;
        };
        let id_card = string_field(item, "idCard");
        let Some(bday) = id_card.as_deref().and_then(registration_birthday) else {
            continue;
        };
        let duplicate =
            match sqlx::query("SELECT 1 FROM t_user WHERE user_account = ? OR id_card = ? LIMIT 1")
                .bind(&account)
                .bind(&id_card)
                .fetch_optional(&mut *tx)
                .await
            {
                Ok(row) => row,
                Err(error) => {
                    let _ = tx.rollback().await;
                    tracing::warn!(error = %error, "admin import duplicate query failed");
                    return envelope_response(
                        StatusCode::INTERNAL_SERVER_ERROR,
                        500,
                        "服务器错误",
                        "",
                    );
                }
            };
        if duplicate.is_some() {
            continue;
        }
        let Some(plaintext) = string_field(item, "password").filter(|value| !value.is_empty())
        else {
            continue;
        };
        let Ok(hash) = password::hash(&plaintext) else {
            continue;
        };
        let id = Uuid::new_v4().to_string();
        let image = string_field(item, "userImg")
            .unwrap_or_else(|| "/userImages/pag-avatar-man.png".into());
        if let Err(error) = sqlx::query("INSERT INTO t_user (id, user_account, user_img, user_name, id_card, user_sex, password, status, bday, eday, token, device_id) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, NULL, NULL)")
            .bind(&id)
            .bind(&account)
            .bind(&image)
            .bind(string_field(item, "userName"))
            .bind(&id_card)
            .bind(int_field(item, "userSex"))
            .bind(hash)
            .bind(&bday)
            .bind(string_field(item, "eday"))
            .execute(&mut *tx)
            .await
        {
            let _ = tx.rollback().await;
            tracing::warn!(error = %error, "admin import user insert failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
        if let Some(role_id) = &default_role {
            if let Err(error) =
                sqlx::query("INSERT INTO t_user_role (id, user_id, role_id) VALUES (?, ?, ?)")
                    .bind(Uuid::new_v4().to_string())
                    .bind(&id)
                    .bind(role_id)
                    .execute(&mut *tx)
                    .await
            {
                let _ = tx.rollback().await;
                tracing::warn!(error = %error, "admin import role insert failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        }
        created.push(json!({"id": id, "userName": string_field(item, "userName"), "userAccount": account, "userImg": image}));
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(created)), "ok", "")
}

pub(crate) async fn add_user_role(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Response {
    let Some(user_id) = string_field(&body, "userId") else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let role_ids = body
        .get("roleIds")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default();
    if role_ids.is_empty() {
        return envelope_json(StatusCode::OK, 200, Some(json!("数据正常")), "ok", "");
    }
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if sqlx::query("DELETE FROM t_user_role WHERE user_id = ?")
        .bind(&user_id)
        .execute(&mut *tx)
        .await
        .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    for role_id in role_ids.iter().filter_map(Value::as_str) {
        if sqlx::query("INSERT INTO t_user_role (id, user_id, role_id) VALUES (?, ?, ?)")
            .bind(Uuid::new_v4().to_string())
            .bind(&user_id)
            .bind(role_id)
            .execute(&mut *tx)
            .await
            .is_err()
        {
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, Some(json!("数据正常")), "ok", "")
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
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => {
            tracing::warn!(error = %error, "delete user transaction could not start");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    if let Err(error) = sqlx::query("DELETE FROM t_user_role WHERE user_id = ?")
        .bind(user_id)
        .execute(&mut *tx)
        .await
    {
        let _ = tx.rollback().await;
        tracing::warn!(error = %error, "delete user role associations failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    let deleted = match sqlx::query("DELETE FROM t_user WHERE id = ?")
        .bind(user_id)
        .execute(&mut *tx)
        .await
    {
        Ok(result) => result.rows_affected() == 1,
        Err(error) => {
            let _ = tx.rollback().await;
            tracing::warn!(error = %error, "delete user row failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    if let Err(error) = tx.commit().await {
        tracing::warn!(error = %error, "delete user transaction commit failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(deleted)), "ok", "")
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

pub(crate) async fn add_role(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    let Some(role) = body.get("role").and_then(Value::as_object) else {
        return envelope_response(StatusCode::OK, 202, "角色信息不能为空", "");
    };
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let role_id = role
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| Uuid::new_v4().to_string());
    let title = role.get("title").and_then(Value::as_str);
    let remark = role.get("remark").and_then(Value::as_str);
    let is_admin = role
        .get("isAdmin")
        .and_then(Value::as_i64)
        .map(|value| value as i32);
    let is_default = role
        .get("isDefault")
        .and_then(Value::as_i64)
        .map(|value| value as i32);
    if is_admin == Some(1) && is_default == Some(0) {
        let _ = sqlx::query("UPDATE t_role SET is_default = 1 WHERE is_default = 0")
            .execute(&mut *tx)
            .await;
    }
    let result = sqlx::query("INSERT INTO t_role (id, title, remark, is_admin, is_default) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE title=VALUES(title), remark=VALUES(remark), is_admin=VALUES(is_admin), is_default=VALUES(is_default)")
        .bind(&role_id).bind(title).bind(remark).bind(is_admin).bind(is_default).execute(&mut *tx).await;
    if result.is_err() {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if sqlx::query("DELETE FROM t_role_menus WHERE role_id = ?")
        .bind(&role_id)
        .execute(&mut *tx)
        .await
        .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if let Some(menus) = body.get("menus").and_then(Value::as_array) {
        for menu in menus {
            let parsed = menu
                .as_str()
                .and_then(|value| serde_json::from_str::<Value>(value).ok())
                .unwrap_or_else(|| menu.clone());
            let Some(menu_id) = parsed.get("menusId").and_then(Value::as_str) else {
                continue;
            };
            let permissions = parsed
                .get("per")
                .cloned()
                .unwrap_or_else(|| json!([]))
                .to_string();
            if sqlx::query(
                "INSERT INTO t_role_menus (id, role_id, menu_id, per) VALUES (?, ?, ?, ?)",
            )
            .bind(Uuid::new_v4().to_string())
            .bind(&role_id)
            .bind(menu_id)
            .bind(permissions)
            .execute(&mut *tx)
            .await
            .is_err()
            {
                let _ = tx.rollback().await;
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        }
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
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

pub(crate) async fn add_menu(State(state): State<AppState>, Json(body): Json<Value>) -> Response {
    if body.get("permissions").is_none() {
        return envelope_response(
            StatusCode::OK,
            202,
            "permissions 缺失，拒绝编辑菜单权限",
            "",
        );
    }
    let Some(menu) = body.get("menus").and_then(Value::as_object) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let menu_id = menu
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
        .unwrap_or_else(|| Uuid::new_v4().to_string());
    let fields = (
        menu.get("parentId").and_then(Value::as_str),
        menu.get("key").and_then(Value::as_str),
        menu.get("path").and_then(Value::as_str),
        menu.get("name").and_then(Value::as_str),
        menu.get("icon").and_then(Value::as_str),
        menu.get("iconF").and_then(Value::as_str),
        menu.get("height").and_then(Value::as_str),
        menu.get("title").and_then(Value::as_str),
        menu.get("isMenu")
            .and_then(Value::as_i64)
            .map(|value| value as i32),
        menu.get("isBread")
            .and_then(Value::as_i64)
            .map(|value| value as i32),
        menu.get("component").and_then(Value::as_str),
        menu.get("sort")
            .and_then(Value::as_i64)
            .map(|value| value as i32),
    );
    let result = sqlx::query("INSERT INTO t_menus (id, parent_id, `key`, path, name, icon, icon_f, height, title, is_menu, is_bread, component, sort) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), `key`=VALUES(`key`), path=VALUES(path), name=VALUES(name), icon=VALUES(icon), icon_f=VALUES(icon_f), height=VALUES(height), title=VALUES(title), is_menu=VALUES(is_menu), is_bread=VALUES(is_bread), component=VALUES(component), sort=VALUES(sort)")
        .bind(&menu_id).bind(fields.0).bind(fields.1).bind(fields.2).bind(fields.3).bind(fields.4).bind(fields.5).bind(fields.6).bind(fields.7).bind(fields.8).bind(fields.9).bind(fields.10).bind(fields.11).execute(&mut *tx).await;
    if result.is_err() {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if sqlx::query("DELETE FROM t_menus_button WHERE menus_id = ?")
        .bind(&menu_id)
        .execute(&mut *tx)
        .await
        .is_err()
    {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if let Some(permissions) = body.get("permissions").and_then(Value::as_array) {
        for permission in permissions {
            if sqlx::query(
                "INSERT INTO t_menus_button (id, menus_id, title, `key`) VALUES (?, ?, ?, ?)",
            )
            .bind(Uuid::new_v4().to_string())
            .bind(&menu_id)
            .bind(permission.get("title").and_then(Value::as_str))
            .bind(permission.get("key").and_then(Value::as_str))
            .execute(&mut *tx)
            .await
            .is_err()
            {
                let _ = tx.rollback().await;
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        }
    }
    if tx.commit().await.is_err() {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({"menus": {"id": menu_id}, "permissions": body.get("permissions")})),
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

async fn role_menu_values(pool: &MySqlPool, role_id: &str) -> Result<Vec<Value>, sqlx::Error> {
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
