use super::common::*;
use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use axum::extract::Extension;
use axum::extract::Query;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::Response;
use axum::Json;
use serde_json::json;
use serde_json::Value;
use sqlx::MySqlConnection;
use sqlx::MySqlPool;
use sqlx::Row;
use std::collections::HashMap;

async fn export_train(
    connection: &mut MySqlConnection,
    actor: &AuthUser,
    id: &str,
) -> Outcome<Value> {
    // Read-only: plain reads inside the transaction's snapshot, no row lock.
    let train = load_train(connection, id, Domain::Key, false).await?;
    if !manages(connection, &train, actor, Domain::Key).await? {
        return Err(denied());
    }
    let train = rows(
        connection,
        table(Domain::Key),
        KEY_SYNC,
        "WHERE id=?",
        &[id],
    )
    .await?
    .remove(0);
    let pages = rows(
        connection,
        page_table(Domain::Key),
        KEY_PAGE,
        "WHERE train_id=? ORDER BY page_number,sort",
        &[id],
    )
    .await?;
    let members = rows(
        connection,
        user_table(Domain::Key),
        KEY_USER,
        "WHERE train_id=? ORDER BY id",
        &[id],
    )
    .await?;
    let values = rows(
        connection,
        value_table(Domain::Key),
        KEY_VALUE,
        "WHERE train_id=? ORDER BY page_number,sort,id",
        &[id],
    )
    .await?;
    let more = rows(
        connection,
        "general_key_pat_train_more",
        KEY_MORE,
        "WHERE train_id=? ORDER BY page_number,id",
        &[id],
    )
    .await?;
    let users = rows(connection, "t_user", USER_SYNC,
        "WHERE id IN (SELECT create_user FROM general_key_pat WHERE id=? UNION SELECT user_id FROM general_key_pat_user WHERE train_id=? UNION SELECT user_id FROM general_key_pat_user_value WHERE train_id=? UNION SELECT user_id FROM general_key_pat_train_more WHERE train_id=?) ORDER BY id",
        &[id, id, id, id]).await?;
    Ok(
        json!({"trainDto":train,"pageDto":pages,"userDto":members,"userValueDto":values,"moreDto":more,"users":users}),
    )
}
pub(super) async fn export(pool: &MySqlPool, actor: &AuthUser, id: Option<&str>) -> Outcome<Value> {
    let mut transaction = pool.begin().await?;
    let result = if let Some(id) = id {
        export_train(&mut transaction, actor, id).await
    } else {
        let ids = sqlx::query("SELECT DISTINCT t.id FROM general_key_pat t JOIN general_key_pat_user u ON u.train_id=t.id WHERE u.user_id=? AND (? OR t.create_user=? OR u.role=1) ORDER BY t.id")
            .bind(&actor.id).bind(actor.is_admin).bind(&actor.id).fetch_all(&mut *transaction).await?;
        let mut trains = Vec::with_capacity(ids.len());
        for row in ids {
            trains.push(
                export_train(
                    &mut transaction,
                    actor,
                    &row.get::<i32, _>("id").to_string(),
                )
                .await?,
            );
        }
        Ok(json!(trains))
    };
    match result {
        Ok(value) => {
            transaction.commit().await?;
            Ok(value)
        }
        Err(error) => {
            transaction.rollback().await?;
            Err(error)
        }
    }
}
fn array<'a>(body: &'a Value, field: &str) -> Outcome<&'a [Value]> {
    body[field]
        .as_array()
        .map(Vec::as_slice)
        .ok_or_else(|| invalid("导入数据缺少必需的列表"))
}
fn mapped_user(map: &HashMap<String, String>, source: &Value) -> Outcome<String> {
    source
        .as_str()
        .and_then(|id| map.get(id))
        .cloned()
        .ok_or_else(|| invalid("导入用户映射缺失"))
}
async fn import_train(connection: &mut MySqlConnection, body: &Value) -> Outcome<()> {
    let mut train = body["trainDto"]
        .as_object()
        .cloned()
        .map(Value::Object)
        .ok_or_else(|| invalid("训练信息不能为空"))?;
    let users = array(body, "users")?;
    let mut user_ids = HashMap::with_capacity(users.len());
    for source in users {
        let old_id = source["id"]
            .as_str()
            .filter(|id| !id.is_empty())
            .ok_or_else(|| invalid("导入用户ID不能为空"))?;
        let account = source["userAccount"]
            .as_str()
            .filter(|account| !account.trim().is_empty())
            .ok_or_else(|| invalid("导入账号不能为空"))?;
        if user_ids.contains_key(old_id) {
            return Err(invalid("导入用户ID重复"));
        }
        // t_user is indexed by primary key only: a locking read by account would lock the whole
        // table (and every login token write). Read the account plainly, then take a shared
        // lock on that one row by primary key so the user cannot be deleted under the import.
        let existing =
            sqlx::query("SELECT id FROM t_user WHERE user_account=? ORDER BY id LIMIT 1")
                .bind(account)
                .fetch_optional(&mut *connection)
                .await?;
        if let Some(row) = &existing {
            sqlx::query("SELECT id FROM t_user WHERE id=? FOR SHARE")
                .bind(row.try_get::<String, _>("id")?)
                .fetch_optional(&mut *connection)
                .await?;
        }
        let id =
            if let Some(row) = existing {
                row.try_get::<String, _>("id")?
            } else {
                let id = uuid::Uuid::new_v4().to_string();
                // Offline archives cannot install sessions, passwords, roles or account state.
                sqlx::query(
                "INSERT INTO t_user (id,user_account,user_name,user_img,status) VALUES (?,?,?,?,0)",
            )
            .bind(&id)
            .bind(account)
            .bind(source["userName"].as_str())
            .bind(source["userImg"].as_str().unwrap_or("/userImages/pag-avatar-man.png"))
            .execute(&mut *connection)
            .await?;
                id
            };
        user_ids.insert(old_id.to_owned(), id);
    }
    train["createUser"] = json!(mapped_user(&user_ids, &train["createUser"])?);
    if train["protocolVersion"].is_null() {
        train["protocolVersion"] = json!(0);
    }
    // The sync DTO carries neither isCable nor trainType: Java's entity default is isCable=0.
    train["isCable"] = json!(0);
    let fields = format!("{KEY_SYNC} isCable");
    let fields = fields
        .split_whitespace()
        .filter(|field| *field != "id")
        .collect::<Vec<_>>()
        .join(" ");
    let id = insert_fields(connection, "general_key_pat", &fields, &train).await?;
    for (property, table, fields) in [
        ("pageDto", "general_key_pat_page", KEY_PAGE),
        ("userDto", "general_key_pat_user", KEY_USER),
        ("userValueDto", "general_key_pat_user_value", KEY_VALUE),
        ("moreDto", "general_key_pat_train_more", KEY_MORE),
    ] {
        let fields = fields
            .split_whitespace()
            .filter(|field| *field != "id" || property == "pageDto")
            .collect::<Vec<_>>()
            .join(" ");
        for source in array(body, property)? {
            let mut row = source
                .as_object()
                .cloned()
                .map(Value::Object)
                .ok_or_else(|| invalid("导入行必须是对象"))?;
            row["trainId"] = json!(id);
            if property == "pageDto" {
                row["id"] = json!(uuid::Uuid::new_v4().to_string());
            } else {
                row["userId"] = json!(mapped_user(&user_ids, &row["userId"])?);
            }
            if property == "userDto" {
                if row["attempt"].is_null() {
                    row["attempt"] = json!(0);
                }
                if row["capturePages"].is_null() {
                    row["capturePages"] = json!("{}");
                }
            }
            insert_fields(connection, table, &fields, &row).await?;
        }
    }
    Ok(())
}
pub(super) async fn import(
    pool: &MySqlPool,
    actor: &AuthUser,
    body: Value,
    batch: bool,
) -> Outcome<Value> {
    if !actor.is_admin {
        return Err(denied());
    }
    let archives = if batch {
        body.as_array()
            .ok_or_else(|| invalid("导入数据必须是列表"))?
            .as_slice()
    } else {
        std::slice::from_ref(&body)
    };
    let mut transaction = pool.begin().await?;
    for archive in archives {
        if let Err(error) = import_train(&mut transaction, archive).await {
            transaction.rollback().await?;
            return Err(error);
        }
    }
    transaction.commit().await?;
    Ok(Value::Null)
}
pub(super) async fn key_export(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Query(query): Query<QueryId>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let Some(id) = query.train_id.filter(|id| !id.is_empty()) else {
        return response(Err(invalid("训练ID不能为空")));
    };
    response(export(pool, &actor, Some(&id)).await)
}
pub(super) async fn key_export_batch(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(export(pool, &actor, None).await)
}
pub(super) async fn key_import(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    if !actor.is_admin {
        return response(Err(denied()));
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(import(pool, &actor, body, false).await)
}
pub(super) async fn key_import_batch(
    State(state): State<AppState>,
    Extension(actor): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    if !actor.is_admin {
        return response(Err(denied()));
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    response(import(pool, &actor, body, true).await)
}
