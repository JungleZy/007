use super::dto::{content_item_json, err, invalid, AddContentRequest, ContentQuery};
use super::generated_groups::generated_groups;
use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use axum::extract::Extension;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::Response;
use axum::Json;
use serde_json::json;
use sqlx::Row;

pub(super) async fn print_bottom_report(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ContentQuery>,
) -> Response {
    let Some(train_id) = body.id.filter(|value| !value.is_empty()) else {
        return invalid("请求参数错误");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "start print report"),
    };
    let train = match sqlx::query("SELECT id,message_number,type,is_random,is_average,status,protocol_version,create_user FROM t_post_telegram_train WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(&train_id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row,
        Ok(None) => { let _ = tx.rollback().await; return envelope_response(StatusCode::OK, 202, "未查询到训练", "未查询到训练"); }
        Err(error) => { let _ = tx.rollback().await; return err(error, "lock print train"); }
    };
    let train_id = train.get::<String, _>("id");
    if train.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    if train.get::<Option<i32>, _>("status") != Some(2)
        && train.get::<Option<i32>, _>("protocol_version") != Some(1)
    {
        let _ = tx.rollback().await;
        return invalid("旧训练缺少原始采集协议，请终止旧训练并新建");
    }
    let Some(message_number) = train
        .get::<Option<i32>, _>("message_number")
        .filter(|value| *value > 0)
    else {
        let _ = tx.rollback().await;
        return invalid("训练报文数量缺失，无法打印报底");
    };
    let Some(train_type) = train.get::<Option<i32>, _>("type") else {
        let _ = tx.rollback().await;
        return invalid("报文类型缺失，无法打印报底");
    };
    let rows = match sqlx::query("SELECT id,floor_number,sort,mores_key FROM t_post_telegram_train_floor_content WHERE train_id=? ORDER BY floor_number,sort")
        .bind(&train_id).fetch_all(&mut *tx).await {
        Ok(rows) => rows,
        Err(error) => { let _ = tx.rollback().await; return err(error, "read print content"); }
    };
    let total_page = (message_number + 99) / 100;
    let latest_floor = rows
        .iter()
        .filter_map(|row| row.get::<Option<i32>, _>("floor_number"))
        .max()
        .unwrap_or(0);
    if train.get::<Option<i32>, _>("status") == Some(2) || latest_floor >= total_page {
        let result: Vec<String> = rows
            .iter()
            .filter_map(|row| row.get::<Option<String>, _>("mores_key"))
            .collect();
        if let Err(error) = tx.commit().await {
            return err(error, "commit print report");
        }
        return envelope_json(StatusCode::OK, 200, Some(json!(result)), "ok", "");
    }
    let existing_groups = rows.len() as i32;
    let remaining = (message_number - existing_groups).max(0) as usize;
    let groups = generated_groups(
        remaining,
        train_type,
        train.get::<Option<i32>, _>("is_random") == Some(1),
        train.get::<Option<i32>, _>("is_average") == Some(1),
    );
    for (offset, group) in groups.iter().enumerate() {
        let absolute = existing_groups as usize + offset;
        let floor_number = (absolute / 100) as i32 + 1;
        let sort = (absolute % 100) as i32;
        let key = serde_json::to_string(
            &group
                .chars()
                .map(|character| character.to_string())
                .collect::<Vec<_>>(),
        )
        .unwrap_or_else(|_| "[]".into());
        if let Err(error) = sqlx::query("INSERT INTO t_post_telegram_train_floor_content (id,train_id,floor_number,sort,mores_key,mores_value,mores_time,pat_keys) VALUES (?,?,?,? ,?,'[]','[]','[]')")
            .bind(uuid::Uuid::new_v4().to_string()).bind(&train_id).bind(floor_number).bind(sort).bind(key).execute(&mut *tx).await {
            let _ = tx.rollback().await;
            return err(error, "write print content");
        }
    }
    let result_rows = match sqlx::query("SELECT mores_key FROM t_post_telegram_train_floor_content WHERE train_id=? ORDER BY floor_number,sort")
        .bind(&train_id).fetch_all(&mut *tx).await {
        Ok(rows) => rows,
        Err(error) => { let _ = tx.rollback().await; return err(error, "read generated report"); }
    };
    let result: Vec<String> = result_rows
        .iter()
        .filter_map(|row| row.get::<Option<String>, _>("mores_key"))
        .collect();
    match tx.commit().await {
        Ok(()) => envelope_json(StatusCode::OK, 200, Some(json!(result)), "ok", ""),
        Err(error) => err(error, "commit print report"),
    }
}

pub(super) async fn add_content_value(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<AddContentRequest>,
) -> Response {
    let Some(train_id) = body.train_id.filter(|value| !value.is_empty()) else {
        return invalid("训练ID不能为空");
    };
    let Some(pages) = body.message_body else {
        return invalid("追加内容不能为空");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "start add content"),
    };
    let train = match sqlx::query("SELECT id,protocol_version,status,create_user FROM t_post_telegram_train WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(&train_id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row,
        Ok(None) => { let _ = tx.rollback().await; return envelope_response(StatusCode::OK, 202, "未查询到训练", "未查询到训练"); }
        Err(error) => { let _ = tx.rollback().await; return err(error, "lock add content train"); }
    };
    let train_id = train.get::<String, _>("id");
    if train.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        let _ = tx.rollback().await;
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    if train.get::<Option<i32>, _>("protocol_version") != Some(1) {
        let _ = tx.rollback().await;
        return invalid("旧训练缺少原始采集协议，请终止旧训练并新建");
    }
    if train.get::<Option<i32>, _>("status") != Some(0) {
        let _ = tx.rollback().await;
        return envelope_response(
            StatusCode::OK,
            208,
            "训练开始后不可追加报底",
            "训练开始后不可追加报底",
        );
    }
    let base_floor = match sqlx::query("SELECT floor_number FROM t_post_telegram_train_floor_content WHERE train_id=? ORDER BY floor_number DESC LIMIT 1")
        .bind(&train_id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row.get::<Option<i32>, _>("floor_number").unwrap_or(0),
        Ok(None) => 0,
        Err(error) => { let _ = tx.rollback().await; return err(error, "find add content floor"); }
    };
    for (page_offset, page) in pages.iter().enumerate() {
        if page.is_empty() {
            let _ = tx.rollback().await;
            return invalid("追加内容不能为空");
        }
        let floor_number = base_floor + page_offset as i32 + 1;
        for (sort, item) in page.iter().enumerate() {
            let Some(object) = item.as_object() else {
                let _ = tx.rollback().await;
                return invalid("追加内容无效");
            };
            let (mores_key, mores_value, _mores_time, pat_keys, _pat_logs) =
                match content_item_json(object) {
                    Ok(value) => value,
                    Err(message) => {
                        let _ = tx.rollback().await;
                        return invalid(message);
                    }
                };
            if let Err(error) = sqlx::query("INSERT INTO t_post_telegram_train_floor_content (id,train_id,floor_number,sort,mores_key,mores_value,mores_time,pat_keys) VALUES (?,?,?,?,?,?,?,?)")
                .bind(uuid::Uuid::new_v4().to_string()).bind(&train_id).bind(floor_number).bind(sort as i32).bind(mores_key).bind(mores_value).bind("[]").bind(pat_keys).execute(&mut *tx).await {
                let _ = tx.rollback().await;
                return err(error, "write add content");
            }
        }
    }
    let floors = match sqlx::query("SELECT floor_number FROM t_post_telegram_train_floor_content WHERE train_id=? GROUP BY floor_number ORDER BY floor_number")
        .bind(&train_id).fetch_all(&mut *tx).await {
        Ok(rows) => rows.iter().filter_map(|row| row.get::<Option<i32>, _>("floor_number")).collect::<Vec<_>>(),
        Err(error) => { let _ = tx.rollback().await; return err(error, "read add content result"); }
    };
    match tx.commit().await {
        Ok(()) => envelope_json(StatusCode::OK, 200, Some(json!(floors)), "ok", ""),
        Err(error) => err(error, "commit add content"),
    }
}
