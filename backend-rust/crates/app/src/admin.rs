use std::collections::HashMap;

use axum::{
    extract::{Query, State},
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
        Ok(result) if result.rows_affected() == 1 => {
            envelope_json(StatusCode::OK, 200, Some(json!(temporary)), "ok", "")
        }
        Ok(_) => envelope_response(StatusCode::OK, 500, "数据异常", ""),
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
