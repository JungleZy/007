use std::collections::{HashMap, HashSet};

use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use serde_json::{json, Value};
use sqlx::{MySqlConnection, Row};
use tracing::warn;
use uuid::Uuid;

use crate::{
    admin, password,
    platform::{
        auth::{token_digest, AuthUser, LoginRequest, PasswordChangeRequest, RegistrationRequest},
        envelope::{envelope_json, envelope_response},
        state::AppState,
    },
};

#[derive(Clone)]
pub(crate) struct MenuRow {
    pub(crate) id: String,
    pub(crate) parent_id: String,
    pub(crate) key: Option<String>,
    pub(crate) path: Option<String>,
    pub(crate) name: Option<String>,
    pub(crate) icon: Option<String>,
    pub(crate) icon_f: Option<String>,
    pub(crate) height: Option<String>,
    pub(crate) title: Option<String>,
    pub(crate) is_menu: Option<i32>,
    pub(crate) is_bread: Option<i32>,
    pub(crate) component: Option<String>,
    pub(crate) sort: Option<i32>,
}

pub(crate) async fn user_logout(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("UPDATE t_user SET token = NULL, device_id = NULL WHERE id = ?")
        .bind(&user.id)
        .execute(pool)
        .await
    {
        Ok(result) if result.rows_affected() == 1 => {
            envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", "")
        }
        Ok(_) => envelope_json(StatusCode::OK, 200, Some(json!(false)), "ok", ""),
        Err(error) => {
            warn!(error = %error, user_id = %user.id, "logout update failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(crate) async fn change_password(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(request): Json<PasswordChangeRequest>,
) -> Response {
    let Some(old_password) = request.old_password.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(new_password) = request.new_password.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(new_password_verification) = request
        .new_password_verification
        .filter(|value| !value.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => {
            warn!(error = %error, "password change transaction could not start");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let row = match sqlx::query("SELECT password FROM t_user WHERE id = ? LIMIT 1 FOR UPDATE")
        .bind(&user.id)
        .fetch_optional(&mut *transaction)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => {
            let _ = transaction.rollback().await;
            return envelope_response(StatusCode::OK, 206, "账号登录凭证异常", "");
        }
        Err(error) => {
            let _ = transaction.rollback().await;
            warn!(error = %error, user_id = %user.id, "password query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    if !password::verify(
        &old_password,
        row.get::<Option<String>, _>("password").as_deref(),
    )
    .matches
    {
        let _ = transaction.rollback().await;
        return envelope_json(StatusCode::OK, 200, Some(json!(false)), "当前密码错误", "");
    }
    if new_password != new_password_verification {
        let _ = transaction.rollback().await;
        return envelope_json(StatusCode::OK, 200, Some(json!(false)), "密码不一致", "");
    }
    let new_hash = match password::hash(&new_password) {
        Ok(hash) => hash,
        Err(error) => {
            let _ = transaction.rollback().await;
            warn!(error, "password hash generation failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    if let Err(error) = sqlx::query("UPDATE t_user SET password = ? WHERE id = ?")
        .bind(new_hash)
        .bind(&user.id)
        .execute(&mut *transaction)
        .await
    {
        let _ = transaction.rollback().await;
        warn!(error = %error, user_id = %user.id, "password update failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if let Err(error) = transaction.commit().await {
        warn!(error = %error, user_id = %user.id, "password change commit failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(true)), "数据正常", "")
}

pub(crate) async fn user_signin(
    State(state): State<AppState>,
    Json(request): Json<RegistrationRequest>,
) -> Response {
    if request.id.as_deref().is_some_and(|id| !id.is_empty()) {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    }
    let Some(user_account) = request.user_account.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    if !valid_account(&user_account) {
        return envelope_response(
            StatusCode::OK,
            500,
            "账户格式不正确，最少4位最多32位，只能是数字或字母或汉字",
            "",
        );
    }
    let Some(id_card) = request.id_card.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 500, admin::ID_CARD_FORMAT_ERROR, "");
    };
    let Some(bday) = registration_birthday(&id_card) else {
        return envelope_response(StatusCode::OK, 500, admin::ID_CARD_FORMAT_ERROR, "");
    };
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    // Hash before taking the account lock; a missing password is reported only after the
    // duplicate check, as in UserService.handleNewUser.
    let password_hash = match request.password.filter(|value| !value.is_empty()) {
        Some(plaintext) => match admin::hash_blocking(plaintext).await {
            Ok(hash) => Some(hash),
            Err(error) => {
                warn!(error, "registration password hash failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        },
        None => None,
    };
    let mut connection = match admin::account_connection(pool).await {
        Ok(connection) => connection,
        Err(error) => {
            warn!(error = %error, "registration account lock failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let mut transaction = match sqlx::Connection::begin(&mut *connection).await {
        Ok(transaction) => transaction,
        Err(error) => {
            warn!(error = %error, "registration transaction could not start");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    match admin::account_exists(&mut transaction, &user_account).await {
        Ok(false) => {}
        Ok(true) => {
            return envelope_response(StatusCode::OK, 500, admin::USER_ACCOUNT_REPEAT, "");
        }
        Err(error) => {
            warn!(error = %error, "registration duplicate query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    }
    let Some(password_hash) = password_hash else {
        return admin::rejected(admin::PASSWORD_REQUIRED);
    };
    let default_role = match admin::default_role_id(&mut transaction).await {
        Ok(Some(role_id)) => role_id,
        Ok(None) => {
            warn!("registration cannot proceed without a default role");
            return admin::rejected(admin::NO_DEFAULT_ROLE);
        }
        Err(error) => {
            warn!(error = %error, "registration default role query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let user_id = Uuid::new_v4().to_string();
    let user_img = request
        .user_img
        .unwrap_or_else(|| "/userImages/pag-avatar-man.png".into());
    if let Err(error) = sqlx::query(
        "INSERT INTO t_user (id, user_account, user_img, user_name, id_card, user_sex, password, status, bday, eday) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
    )
    .bind(&user_id)
    .bind(&user_account)
    .bind(&user_img)
    .bind(&request.user_name)
    .bind(&id_card)
    .bind(request.user_sex)
    .bind(password_hash)
    .bind(&bday)
    .bind(&request.eday)
    .execute(&mut *transaction)
    .await
    {
        let _ = transaction.rollback().await;
        warn!(error = %error, "registration user insert failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if let Err(error) =
        sqlx::query("INSERT INTO t_user_role (id, user_id, role_id) VALUES (?, ?, ?)")
            .bind(Uuid::new_v4().to_string())
            .bind(&user_id)
            .bind(&default_role)
            .execute(&mut *transaction)
            .await
    {
        let _ = transaction.rollback().await;
        warn!(error = %error, "registration default role insert failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    if let Err(error) = transaction.commit().await {
        warn!(error = %error, "registration transaction commit failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(
        StatusCode::OK,
        200,
        Some(
            json!({"id": user_id, "userName": request.user_name, "userAccount": user_account, "userImg": user_img}),
        ),
        "ok",
        "",
    )
}

pub(crate) async fn verify_password(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    body: String,
) -> Response {
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    match sqlx::query("SELECT password FROM t_user WHERE id = ? LIMIT 1")
        .bind(&user.id)
        .fetch_optional(pool)
        .await
    {
        Ok(Some(row)) => envelope_json(
            StatusCode::OK,
            200,
            Some(json!(
                password::verify(&body, row.get::<Option<String>, _>("password").as_deref())
                    .matches
            )),
            "数据正常",
            "",
        ),
        Ok(None) => envelope_response(StatusCode::OK, 206, "账号登录凭证异常", ""),
        Err(error) => {
            warn!(error = %error, user_id = %user.id, "verify password query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

// Java's login rejections use ResponseResult.error(SYSTEM_ERROR, reason): the generic system
// message with the reason in the description (UserService.login).
fn login_rejected(reason: &str) -> Response {
    envelope_response(StatusCode::OK, 500, "服务器错误", reason)
}

/// PBKDF2 verification (and the rehash of an outdated hash) runs on the blocking pool so a login
/// burst does not stall the async workers.
async fn verify_login_password(
    plaintext: &str,
    stored: Option<String>,
) -> Result<(password::Verification, Option<String>), &'static str> {
    let plaintext = plaintext.to_owned();
    tokio::task::spawn_blocking(move || {
        let verification = password::verify(&plaintext, stored.as_deref());
        let upgraded = if verification.matches && verification.needs_upgrade {
            Some(password::hash(&plaintext)?)
        } else {
            None
        };
        Ok((verification, upgraded))
    })
    .await
    .map_err(|_| "password verification task failed")?
}

pub(crate) async fn user_login(
    State(state): State<AppState>,
    Json(request): Json<LoginRequest>,
) -> Response {
    let Some(user_account) = request.user_account.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(plaintext) = request.password.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(device_id) = request.device_id.filter(|value| !value.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "");
    };
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    // t_user is indexed by primary key only: a locking read filtered by user_account would lock
    // every row scanned (and the gap after it) for the whole PBKDF2 check, stalling every other
    // user's writes. Verify against a plain read off the transaction, then lock the matched row by
    // primary key and retry if its account or password hash changed in between.
    let mut attempts = 0;
    let (mut transaction, row, upgraded_password) = loop {
        attempts += 1;
        let candidate = match sqlx::query(
            "SELECT id, password FROM t_user WHERE user_account = ? ORDER BY id LIMIT 1",
        )
        .bind(&user_account)
        .fetch_optional(pool)
        .await
        {
            Ok(candidate) => candidate,
            Err(error) => {
                warn!(error = %error, "login user query failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
        let Some(candidate) = candidate else {
            return login_rejected("系统中不存在该账号");
        };
        let user_id: String = candidate.get("id");
        let stored: Option<String> = candidate.get("password");
        let (verification, upgraded_password) =
            match verify_login_password(&plaintext, stored.clone()).await {
                Ok(result) => result,
                Err(error) => {
                    warn!(error, "login password verification failed");
                    return envelope_response(
                        StatusCode::INTERNAL_SERVER_ERROR,
                        500,
                        "服务器错误",
                        "",
                    );
                }
            };
        if !verification.matches {
            return login_rejected("密码错误");
        }
        let mut transaction = match pool.begin().await {
            Ok(transaction) => transaction,
            Err(error) => {
                warn!(error = %error, "login transaction could not start");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
        let locked = match sqlx::query(
            "SELECT id, wkno, phone, email, user_account, user_img, user_name, id_card, user_sex, password, update_flag, status, bday, eday, dday FROM t_user WHERE id = ? FOR UPDATE",
        )
        .bind(&user_id)
        .fetch_optional(&mut *transaction)
        .await
        {
            Ok(locked) => locked,
            Err(error) => {
                let _ = transaction.rollback().await;
                warn!(error = %error, "login user lock failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
        match locked {
            Some(row)
                if row.get::<Option<String>, _>("user_account").as_deref()
                    == Some(user_account.as_str())
                    && row.get::<Option<String>, _>("password") == stored =>
            {
                break (transaction, row, upgraded_password);
            }
            _ => {
                let _ = transaction.rollback().await;
                if attempts >= 3 {
                    warn!(user_id = %user_id, "login credentials kept changing while being verified");
                    return envelope_response(
                        StatusCode::INTERNAL_SERVER_ERROR,
                        500,
                        "服务器错误",
                        "",
                    );
                }
            }
        }
    };
    match row.get::<Option<i32>, _>("status") {
        Some(1) => {
            let _ = transaction.rollback().await;
            return login_rejected("该账号正在审核中");
        }
        Some(-1) => {
            let _ = transaction.rollback().await;
            return login_rejected("该账号已停用");
        }
        Some(0) => {}
        _ => {
            let _ = transaction.rollback().await;
            return login_rejected("数据异常");
        }
    }
    let role = match sqlx::query(
        "SELECT r.id, r.title, r.remark, r.is_admin, r.is_default FROM t_role r JOIN t_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ? LIMIT 1",
    )
    .bind(row.get::<String, _>("id"))
    .fetch_optional(&mut *transaction)
    .await
    {
        Ok(Some(role)) => role,
        Ok(None) => {
            let _ = transaction.rollback().await;
            return login_rejected("数据异常");
        }
        Err(error) => {
            let _ = transaction.rollback().await;
            warn!(error = %error, "login role query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let role_id: String = role.get("id");
    let is_admin = role.get::<Option<i32>, _>("is_admin") == Some(0);
    let menus = match load_menu_tree(
        &mut *transaction,
        if is_admin { None } else { Some(&role_id) },
    )
    .await
    {
        Ok(menus) => menus,
        Err(error) => {
            let _ = transaction.rollback().await;
            warn!(error = %error, "login menu query failed");
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        }
    };
    let token = password::issue_token();
    let token_digest = token_digest(&token);
    let update = if let Some(upgraded_password) = upgraded_password {
        sqlx::query("UPDATE t_user SET password = ?, token = ?, device_id = ? WHERE id = ?")
            .bind(upgraded_password)
            .bind(&token_digest)
            .bind(&device_id)
            .bind(row.get::<String, _>("id"))
            .execute(&mut *transaction)
            .await
    } else {
        sqlx::query("UPDATE t_user SET token = ?, device_id = ? WHERE id = ?")
            .bind(&token_digest)
            .bind(&device_id)
            .bind(row.get::<String, _>("id"))
            .execute(&mut *transaction)
            .await
    };
    if let Err(error) = update {
        let _ = transaction.rollback().await;
        warn!(error = %error, "login session update failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    let data = json!({
        "user": {
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
        },
        "role": {
            "id": role_id,
            "title": role.get::<Option<String>, _>("title"),
            "remark": role.get::<Option<String>, _>("remark"),
            "isAdmin": role.get::<Option<i32>, _>("is_admin"),
            "isDefault": role.get::<Option<i32>, _>("is_default")
        },
        "menus": menus,
        "token": token,
        "deviceId": device_id
    });
    if let Err(error) = transaction.commit().await {
        warn!(error = %error, "login transaction commit failed");
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    }
    envelope_json(StatusCode::OK, 200, Some(data), "登陆成功", "")
}

async fn load_menu_tree(
    connection: &mut MySqlConnection,
    role_id: Option<&str>,
) -> Result<Vec<Value>, sqlx::Error> {
    let menu_rows = sqlx::query(
        "SELECT id, parent_id, `key`, path, name, icon, icon_f, height, title, is_menu, is_bread, component, sort FROM t_menus ORDER BY sort",
    )
    .fetch_all(&mut *connection)
    .await?;
    let button_rows = sqlx::query("SELECT id, menus_id, title, `key` FROM t_menus_button")
        .fetch_all(&mut *connection)
        .await?;
    let menus: Vec<MenuRow> = menu_rows
        .into_iter()
        .map(|row| MenuRow {
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
        })
        .collect();
    let mut selected = role_id.map(|_| HashSet::<String>::new());
    let mut role_permissions: HashMap<String, HashSet<String>> = HashMap::new();
    if let Some(role_id) = role_id {
        for row in sqlx::query("SELECT menu_id, per FROM t_role_menus WHERE role_id = ?")
            .bind(role_id)
            .fetch_all(&mut *connection)
            .await?
        {
            let menu_id: String = row.get("menu_id");
            selected
                .as_mut()
                .expect("role selection initialized")
                .insert(menu_id.clone());
            let permissions = row
                .get::<Option<String>, _>("per")
                .and_then(|value| serde_json::from_str::<Vec<String>>(&value).ok())
                .unwrap_or_default()
                .into_iter()
                .collect();
            role_permissions.insert(menu_id, permissions);
        }
        let selected = selected.as_mut().expect("role selection initialized");
        let mut changed = true;
        while changed {
            changed = false;
            for menu in &menus {
                if selected.contains(&menu.id)
                    && menu.parent_id != "-1"
                    && selected.insert(menu.parent_id.clone())
                {
                    changed = true;
                }
            }
        }
    }
    let selected_ids = selected.as_ref();
    let mut by_parent: HashMap<String, Vec<MenuRow>> = HashMap::new();
    for menu in menus {
        if selected_ids.is_some_and(|ids| !ids.contains(&menu.id)) {
            continue;
        }
        by_parent
            .entry(menu.parent_id.clone())
            .or_default()
            .push(menu);
    }
    let mut permissions: HashMap<String, Vec<Value>> = HashMap::new();
    for row in button_rows {
        let menu_id: String = row.get("menus_id");
        let button_key = row.get::<Option<String>, _>("key");
        let allowed = match role_id {
            None => true,
            Some(_) => button_key.as_ref().is_some_and(|key| {
                role_permissions
                    .get(&menu_id)
                    .is_some_and(|keys| keys.contains(key))
            }),
        };
        if allowed {
            permissions.entry(menu_id.clone()).or_default().push(json!({
                "id": row.get::<String, _>("id"),
                "menusId": menu_id,
                "title": row.get::<Option<String>, _>("title"),
                "key": button_key
            }));
        }
    }
    Ok(menu_tree("-1", &by_parent, &permissions))
}

pub(crate) async fn user_get_by_token(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let row = sqlx::query(
        "SELECT id, wkno, phone, email, user_account, user_img, user_name, id_card, user_sex, update_flag, status, bday, eday, dday FROM t_user WHERE id = ? LIMIT 1",
    )
    .bind(&user.id)
    .fetch_optional(pool)
    .await;
    match row {
        Ok(Some(row)) => {
            let data = json!({
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
            });
            envelope_json(StatusCode::OK, 200, Some(data), "ok", "")
        }
        Ok(None) => envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", ""),
        Err(error) => {
            warn!(error = %error, "user profile query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}
fn menu_tree(
    parent_id: &str,
    by_parent: &HashMap<String, Vec<MenuRow>>,
    permissions: &HashMap<String, Vec<Value>>,
) -> Vec<Value> {
    let mut rows = by_parent.get(parent_id).cloned().unwrap_or_default();
    rows.sort_by_key(|row| row.sort.unwrap_or(0));
    rows.into_iter()
        .map(|row| {
            let children = menu_tree(&row.id, by_parent, permissions);
            let row_permissions = permissions.get(&row.id).cloned().unwrap_or_default();
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
                "sort": row.sort.unwrap_or(0),
                "permissions": row_permissions,
                "children": children
            })
        })
        .collect()
}

pub(crate) async fn menu_get_all(
    State(state): State<AppState>,
    _user: Extension<AuthUser>,
) -> Response {
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let menu_rows = sqlx::query(
        "SELECT id, parent_id, `key`, path, name, icon, icon_f, height, title, is_menu, is_bread, component, sort FROM t_menus ORDER BY sort",
    )
    .fetch_all(pool)
    .await;
    let button_rows = sqlx::query("SELECT id, menus_id, title, `key` FROM t_menus_button")
        .fetch_all(pool)
        .await;
    match (menu_rows, button_rows) {
        (Ok(menu_rows), Ok(button_rows)) => {
            let mut by_parent: HashMap<String, Vec<MenuRow>> = HashMap::new();
            for row in menu_rows {
                let menu = MenuRow {
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
            let mut permissions: HashMap<String, Vec<Value>> = HashMap::new();
            for row in button_rows {
                let menus_id: String = row.get("menus_id");
                permissions
                    .entry(menus_id.clone())
                    .or_default()
                    .push(json!({
                        "id": row.get::<String, _>("id"),
                        "menusId": menus_id,
                        "title": row.get::<Option<String>, _>("title"),
                        "key": row.get::<Option<String>, _>("key")
                    }));
            }
            envelope_json(
                StatusCode::OK,
                200,
                Some(json!(menu_tree("-1", &by_parent, &permissions))),
                "ok",
                "",
            )
        }
        (Err(error), _) | (_, Err(error)) => {
            warn!(error = %error, "menu tree query failed");
            envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    }
}

pub(crate) async fn role_get_all(
    State(state): State<AppState>,
    _user: Extension<AuthUser>,
) -> Response {
    let Some(pool) = &state.pool else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let roles =
        match sqlx::query("SELECT id, title, remark, is_admin, is_default FROM t_role ORDER BY id")
            .fetch_all(pool)
            .await
        {
            Ok(rows) => rows,
            Err(error) => {
                warn!(error = %error, "role query failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
    let mut result = Vec::with_capacity(roles.len());
    for role in roles {
        let role_id: String = role.get("id");
        // RoleService.getRoleAll -> getMenusEntityByRoleId: the same projection, buttons included,
        // as getRoleById.
        let menus = match admin::role_menu_values(pool, &role_id).await {
            Ok(menus) => menus,
            Err(error) => {
                warn!(error = %error, role_id = %role_id, "role menu query failed");
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
        result.push(json!({
            "role": {
                "id": role_id,
                "title": role.get::<Option<String>, _>("title"),
                "remark": role.get::<Option<String>, _>("remark"),
                "isAdmin": role.get::<Option<i32>, _>("is_admin"),
                "isDefault": role.get::<Option<i32>, _>("is_default")
            },
            "menus": menus
        }));
    }
    envelope_json(StatusCode::OK, 200, Some(json!(result)), "ok", "")
}

pub(crate) fn valid_account(value: &str) -> bool {
    let length = value.chars().count();
    (4..=32).contains(&length)
        && value.chars().all(|character| {
            character.is_ascii_alphanumeric() || (('\u{4300}'..='\u{9fa5}').contains(&character))
        })
}

pub(crate) fn registration_birthday(id_card: &str) -> Option<String> {
    match id_card.chars().count() {
        18 => Some(format!(
            "{}-{}-{}",
            id_card.get(6..10)?,
            id_card.get(10..12)?,
            id_card.get(12..14)?
        )),
        15 => Some(format!(
            "19{}-{}-{}",
            id_card.get(6..8)?,
            id_card.get(8..10)?,
            id_card.get(10..12)?
        )),
        _ => None,
    }
}

pub(crate) fn routes(
    state: &crate::platform::state::AppState,
) -> axum::Router<crate::platform::state::AppState> {
    use crate::{
        admin,
        platform::auth::{authenticate, require_admin},
        user_training_stats,
    };
    use axum::{
        middleware,
        routing::{get, post},
        Router,
    };

    let mut router = Router::new();
    let user = Router::new()
        .route("/getUsersByToken", post(user_get_by_token))
        .route("/userOut", post(user_logout))
        .route("/changePassword", post(change_password))
        .route("/verifyPassword", post(verify_password))
        .route("/getUserDirectory", post(admin::get_user_directory))
        .route("/findAllTeacher", post(admin::find_all_teacher))
        .route("/findAllStu", post(admin::find_all_stu))
        .route(
            "/getUsersByUserNameStartingWith",
            post(admin::get_users_by_user_name_starting_with),
        )
        .route("/getUsersByIds", post(admin::get_users_by_ids))
        .route(
            "/getUserTrainDurationStat",
            post(user_training_stats::get_user_train_duration_stat),
        )
        .route(
            "/getRecentHandKeyTrains",
            post(user_training_stats::get_recent_hand_key_trains),
        )
        .route(
            "/getRecentElectronicKeyTrains",
            post(user_training_stats::get_recent_electronic_key_trains),
        )
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let menu = Router::new()
        .route("/getMenusAll", get(menu_get_all))
        .route("/getMenuById", get(admin::get_menu_by_id))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let role = Router::new()
        .route("/getRoleAll", post(role_get_all))
        .route("/getRoleById", get(admin::get_role_by_id))
        .route("/getRoleMenusInfo", get(admin::get_role_menus_info))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_user = Router::new()
        .route("/saveUser", post(admin::save_user))
        .route("/importUser", post(admin::import_user))
        .route("/addUserRole", post(admin::add_user_role))
        .route("/getAllUser", post(admin::get_all_user))
        .route("/getUserAndRoleById", post(admin::get_user_and_role_by_id))
        .route("/getUserById", post(admin::get_user_by_id))
        .route(
            "/getUserInfoAllByStatusDesc",
            post(admin::get_user_info_all_by_status_desc),
        )
        .route("/delete", get(admin::delete_user))
        .route("/resetPassword", get(admin::reset_password))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_role = Router::new()
        .route("/addRole", post(admin::add_role))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let admin_menu = Router::new()
        .route("/addMenu", post(admin::add_menu))
        .route_layer(middleware::from_fn(require_admin))
        .route_layer(middleware::from_fn_with_state(state.clone(), authenticate));
    let user_api = Router::new()
        .route("/login", post(user_login))
        .route("/signin", post(user_signin))
        .merge(user)
        .merge(admin_user);
    router = router
        .nest("/api/user", user_api)
        .nest("/api/menus", menu.merge(admin_menu))
        .nest("/api/role", role.merge(admin_role));
    router
}
