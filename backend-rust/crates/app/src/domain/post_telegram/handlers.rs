use super::dto::{
    count_characters, err, invalid, ActionRequest, AddRequest, CaptureInterval, ContentQuery,
    ContentUpdate, DeleteQuery,
};
use super::rules::{capture_duration, require_no_overlap};
use super::settle::{
    clear_content, corrupt_saved, generate_source_page, settle_train, training_view, TRAIN_VIEW_SQL,
};
use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::envelope::envelope_response;
use crate::platform::state::AppState;
use crate::shared::generated_groups::generated_groups;
use crate::shared::handkey_rules::handkey_rule;
use crate::shared::handkey_rules::validate_standard;
use axum::extract::Extension;
use axum::extract::Query;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::Response;
use axum::Json;
use serde_json::json;
use serde_json::Value;
use sqlx::Row;

pub(super) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<AddRequest>,
) -> Response {
    let Some(is_cable) = body.is_cable else {
        return invalid("是否使用固定报底不能为空");
    };
    let Some(message_number) = body.message_number.filter(|value| *value >= 0) else {
        return invalid("报文数量不能为空");
    };
    if is_cable == 0 && body.train_type.is_none() {
        return invalid("报文类型不能为空");
    }
    let Some(rule_id) = body.rule_id.as_deref().filter(|id| !id.is_empty()) else {
        return invalid("评分规则不存在");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "add-begin"),
    };
    let grading = match sqlx::query(
        "SELECT id,content,score FROM t_grading_rule WHERE id=? LIMIT 1 FOR UPDATE",
    )
    .bind(rule_id)
    .fetch_optional(&mut *tx)
    .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return invalid("评分规则不存在"),
        Err(error) => return err(error, "add-rule"),
    };
    let rule_id = grading.get::<String, _>("id");
    let rule_content = grading.get::<Option<String>, _>("content");
    if let Err(message) = handkey_rule(rule_content.as_deref()) {
        return invalid(message);
    }
    let Some(full_score) = grading
        .get::<Option<i32>, _>("score")
        .filter(|score| *score >= 0)
    else {
        return invalid("评分规则满分必须为非负整数");
    };
    let mut pages: Vec<Vec<String>> = Vec::new();
    if is_cable == 0 {
        for (index, group) in generated_groups(
            message_number.min(200) as usize,
            body.train_type.unwrap_or(0),
            body.is_random == Some(true),
            body.is_average == Some(true),
        )
        .into_iter()
        .enumerate()
        {
            if index % 100 == 0 {
                pages.push(Vec::new());
            }
            let keys = group
                .chars()
                .map(|character| character.to_string())
                .collect::<Vec<_>>();
            let key = match serde_json::to_string(&keys) {
                Ok(key) => key,
                Err(error) => return corrupt_saved(&error.to_string(), "new-train"),
            };
            pages[index / 100].push(key);
        }
    } else {
        let total_pages = (message_number / 100) as usize;
        if total_pages == 0 {
            return invalid("报文组数不足一页，无法建立训练");
        }
        // A single MVCC read freezes cable floors without acquiring child scan
        // locks or inverting cable deletion's child-before-parent lock order.
        let rows = match sqlx::query("SELECT floor_number,mores_key FROM t_cable_floor WHERE cable_id=? ORDER BY floor_number,sort")
            .bind(body.cable_id.as_deref()).fetch_all(&mut *tx).await {
            Ok(rows) => rows,
            Err(error) => return err(error, "add-cable-pages"),
        };
        let mut previous = None;
        for row in rows {
            let floor = row.get::<Option<i32>, _>("floor_number");
            if floor.is_none() {
                return corrupt_saved("固定报底页码缺失", "new-train");
            }
            if floor != previous {
                pages.push(Vec::new());
                previous = floor;
            }
            let key = row.get::<Option<String>, _>("mores_key");
            let Some(key) = key else {
                continue;
            };
            match serde_json::from_str::<Option<Vec<String>>>(&key) {
                Ok(Some(_)) => pages.last_mut().expect("cable page was inserted").push(key),
                Ok(None) => {}
                Err(error) => return corrupt_saved(&error.to_string(), "new-train"),
            }
        }
        let start = body.start_page.unwrap_or(1).saturating_sub(1).max(0) as usize;
        if start > 0 && start < pages.len() {
            pages.drain(..start);
        }
        if pages.len() < total_pages {
            return invalid("所选电缆可用楼层不足");
        }
        pages.truncate(total_pages);
    }
    let id = uuid::Uuid::new_v4().to_string();
    if let Err(error) = sqlx::query("INSERT INTO t_post_telegram_train (id,name,is_cable,type,code_sort,is_random,is_average,message_number,status,create_user,create_time,error_number,accuracy,protocol_version,attempt,full_score,active_millis,valid_time,speed,rule_id,rule_content,score) VALUES (?,?,?,?,?,?,?,?,0,?,NOW(6),0,'0.00',1,0,?,0,0,'0',?,?,?)")
        .bind(&id).bind(body.name.as_deref()).bind(is_cable).bind(body.train_type)
        .bind(i32::from(body.code_sort == Some(true))).bind(i32::from(body.is_random == Some(true)))
        .bind(i32::from(body.is_average == Some(true))).bind(message_number).bind(&user.id)
        .bind(full_score).bind(rule_id).bind(rule_content).bind(full_score.to_string()).execute(&mut *tx).await {
        return err(error, "add-train");
    }
    for (floor, groups) in pages.into_iter().enumerate() {
        for (sort, key) in groups.into_iter().enumerate() {
            if let Err(error) = sqlx::query("INSERT INTO t_post_telegram_train_floor_content (id,train_id,floor_number,sort,mores_key,mores_value,mores_time,pat_keys) VALUES (?,?,?,?,?,'[]','[]','[]')")
                .bind(uuid::Uuid::new_v4().to_string()).bind(&id).bind((floor + 1) as i32).bind(sort as i32).bind(key).execute(&mut *tx).await {
                return err(error, "add-source-content");
            }
        }
    }
    let data = match sqlx::query(TRAIN_VIEW_SQL)
        .bind(&id)
        .fetch_one(&mut *tx)
        .await
    {
        Ok(row) => training_view(&row),
        Err(error) => return err(error, "add-result"),
    };
    match tx.commit().await {
        Ok(()) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(error) => err(error, "add-commit"),
    }
}

pub(super) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    action(state, user, body, 1).await
}

pub(super) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    action(state, user, body, 2).await
}

pub(super) async fn stop(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    let Some(id) = body.id else {
        return invalid("请求参数错误");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "stop-begin"),
    };
    let row = match sqlx::query("SELECT id,name,create_user,status,protocol_version,attempt FROM t_post_telegram_train WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(&id)
        .fetch_optional(&mut *tx)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return invalid("未查询到训练"),
        Err(error) => return err(error, "stop-lock"),
    };
    let id = row.get::<String, _>("id");
    if row.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    if row.get::<Option<i32>, _>("protocol_version") != Some(1) {
        return invalid("旧训练缺少原始采集协议，请终止旧训练并新建");
    }
    if body.attempt != row.get::<Option<i32>, _>("attempt") {
        return envelope_response(
            StatusCode::OK,
            208,
            "训练轮次已变化，请重新加载训练",
            "训练轮次已变化，请重新加载训练",
        );
    }
    if row.get::<Option<i32>, _>("status") != Some(1) {
        let name = row.get::<Option<String>, _>("name").unwrap_or_default();
        let message = format!("{name}训练的状态不是进行中");
        return envelope_response(StatusCode::OK, 202, &message, &message);
    }
    if let Err(error) = clear_content(&mut tx, &id, false).await {
        return err(error, "stop-clear-content");
    }
    if let Err(error) = sqlx::query("UPDATE t_post_telegram_train SET status=0,floor_now=1,speed_log=NULL,error_number=0,accuracy='0.00',attempt=attempt+1,start_time=NULL,end_time=NULL,valid_time=0,active_millis=0,speed='0.00',score=COALESCE(full_score,'0'),finish_info=NULL,statistic_info=NULL,deduct_info=NULL,lack=0 WHERE id=?")
        .bind(&id)
        .execute(&mut *tx)
        .await
    {
        return err(error, "stop-update");
    }
    if let Err(error) = tx.commit().await {
        return err(error, "stop-commit");
    }
    envelope_response(StatusCode::OK, 200, "ok", "")
}

async fn action(state: AppState, user: AuthUser, body: ActionRequest, status: i32) -> Response {
    let Some(id) = body.id.filter(|id| !id.is_empty()) else {
        return invalid("请求参数错误");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "action-begin"),
    };
    let train = match sqlx::query(TRAIN_VIEW_SQL)
        .bind(&id)
        .fetch_optional(&mut *tx)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return invalid("未查询到训练"),
        Err(error) => return err(error, "action-lock"),
    };
    let id = train.get::<String, _>("id");
    if train.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let protocol = train.get::<Option<i32>, _>("protocol_version");
    let terminal = train.get::<Option<i32>, _>("status") == Some(2);
    if (status == 1 || !terminal) && protocol != Some(1) {
        return invalid("旧训练缺少原始采集协议，请终止旧训练并新建");
    }
    if protocol == Some(1)
        && (body.attempt.is_none() || body.attempt != train.get::<Option<i32>, _>("attempt"))
    {
        return envelope_response(
            StatusCode::OK,
            208,
            "训练轮次已变化，请重新加载训练",
            "训练轮次已变化，请重新加载训练",
        );
    }
    if terminal {
        if status == 1 {
            return envelope_response(
                StatusCode::OK,
                208,
                "已完成训练不可重新开始",
                "已完成训练不可重新开始",
            );
        }
        let data = training_view(&train);
        if let Err(error) = tx.commit().await {
            return err(error, "finish-idempotent-commit");
        }
        return envelope_json(StatusCode::OK, 200, Some(data), "ok", "");
    }
    if status == 2
        && (train.get::<Option<i32>, _>("status") != Some(1)
            || train.get::<Option<String>, _>("start_text").is_none())
    {
        return invalid("训练不在进行中");
    }
    let rule = match handkey_rule(train.get::<Option<String>, _>("rule_content").as_deref()) {
        Ok(rule) => rule,
        Err(message) => return invalid(message),
    };
    let Some(full_score) = train
        .get::<Option<i32>, _>("full_score")
        .filter(|score| *score >= 0)
    else {
        return invalid("训练满分快照无效，请新建训练");
    };
    if status == 1 {
        if train.get::<Option<i32>, _>("status") == Some(0) {
            if let Err(error) = sqlx::query(
                "UPDATE t_post_telegram_train SET status=1,start_time=NOW(6) WHERE id=?",
            )
            .bind(&id)
            .execute(&mut *tx)
            .await
            {
                return err(error, "begin-update");
            }
        }
    } else if let Err(response) = settle_train(&mut tx, &train, &rule, full_score).await {
        return response;
    }
    let result = match sqlx::query(TRAIN_VIEW_SQL)
        .bind(&id)
        .fetch_one(&mut *tx)
        .await
    {
        Ok(row) => training_view(&row),
        Err(error) => return err(error, "action-result"),
    };
    match tx.commit().await {
        Ok(()) => envelope_json(StatusCode::OK, 200, Some(result), "ok", ""),
        Err(error) => err(error, "action-commit"),
    }
}

pub(super) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<DeleteQuery>,
) -> Response {
    let Some(id) = query.train_id.filter(|value| !value.is_empty()) else {
        return invalid("训练ID不能为空");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "delete-begin"),
    };
    let row = match sqlx::query(
        "SELECT id,create_user FROM t_post_telegram_train WHERE id=? LIMIT 1 FOR UPDATE",
    )
    .bind(&id)
    .fetch_optional(&mut *tx)
    .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return invalid("未查询到训练"),
        Err(error) => return err(error, "delete-lock"),
    };
    let id = row.get::<String, _>("id");
    if row.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    if let Err(error) = clear_content(&mut tx, &id, true).await {
        return err(error, "delete-content");
    }
    let deleted =
        match sqlx::query("DELETE FROM t_post_telegram_train WHERE id=? AND create_user=?")
            .bind(&id)
            .bind(&user.id)
            .execute(&mut *tx)
            .await
        {
            Ok(result) => result.rows_affected(),
            Err(error) => return err(error, "delete-train"),
        };
    if deleted != 1 {
        return err(sqlx::Error::RowNotFound, "delete-train-missing");
    }
    if let Err(error) = tx.commit().await {
        return err(error, "delete-commit");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", "")
}

pub(super) async fn content(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ContentQuery>,
) -> Response {
    let Some(train_id) = body.id.filter(|value| !value.is_empty()) else {
        return invalid("请求参数错误");
    };
    let Some(floor_number) = body.floor_number.filter(|value| *value >= 1) else {
        return invalid("页码不能为空");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "start content transaction"),
    };
    let train = match sqlx::query("SELECT id,protocol_version,attempt,status,message_number,type,is_random,is_average,create_user,CAST(TIMESTAMPDIFF(MICROSECOND,start_time,NOW(6)) DIV 1000 AS SIGNED) AS elapsed_ms FROM t_post_telegram_train WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(&train_id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到训练", "未查询到训练"),
        Err(error) => return err(error, "lock content train"),
    };
    let train_id = train.get::<String, _>("id");
    if train.get::<Option<String>, _>("create_user").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let message_number = train.get::<Option<i32>, _>("message_number").unwrap_or(0);
    if floor_number > (message_number + 99) / 100 {
        return invalid("页码超出训练范围");
    }
    let status = train.get::<Option<i32>, _>("status");
    if status != Some(2) && train.get::<Option<i32>, _>("protocol_version") != Some(1) {
        return invalid("旧训练缺少原始采集协议，请终止旧训练并新建");
    }
    let source_query = "SELECT id,mores_key,mores_value,mores_time,pat_keys FROM t_post_telegram_train_floor_content WHERE train_id=? AND floor_number=? ORDER BY sort";
    let mut rows = match sqlx::query(source_query)
        .bind(&train_id)
        .bind(floor_number)
        .fetch_all(&mut *tx)
        .await
    {
        Ok(rows) => rows,
        Err(error) => return err(error, "content rows"),
    };
    if rows.is_empty() {
        if status == Some(2) {
            return invalid("历史训练缺少该页报底，不再生成新内容");
        }
        if let Err(response) = generate_source_page(&mut tx, &train, &train_id, floor_number).await
        {
            return response;
        }
        rows = match sqlx::query(source_query)
            .bind(&train_id)
            .bind(floor_number)
            .fetch_all(&mut *tx)
            .await
        {
            Ok(rows) => rows,
            Err(error) => return err(error, "generated content rows"),
        };
    }
    let value = match sqlx::query("SELECT message_body,standard,finish_info,resolver,capture_intervals FROM t_post_telegram_train_floor_content_value WHERE train_id=? AND floor_number=? LIMIT 1")
        .bind(&train_id).bind(floor_number).fetch_optional(&mut *tx).await {
        Ok(value) => value,
        Err(error) => return err(error, "content value"),
    };
    if let Err(error) = tx.commit().await {
        return err(error, "commit content page");
    }
    let message_key: Vec<Value> = rows.iter().map(|row| json!({
        "id": row.get::<Option<String>, _>("id"),
        "moresKey": row.get::<Option<String>, _>("mores_key"),
        "moresValue": row.get::<Option<String>, _>("mores_value").unwrap_or_else(|| "[]".into()),
        "moresTime": row.get::<Option<String>, _>("mores_time").unwrap_or_else(|| "[]".into()),
        "patKeys": row.get::<Option<String>, _>("pat_keys").unwrap_or_else(|| "[]".into()),
        "patLogs": "[]"
    })).collect();
    let generated_body = serde_json::to_string(&message_key).unwrap_or_else(|_| "[]".into());
    let (message_body, standard, finish_info, resolver, capture_intervals, submitted) = match value
    {
        Some(row) => (
            row.get::<Option<String>, _>("message_body")
                .unwrap_or_else(|| "[]".into()),
            row.get::<Option<String>, _>("standard")
                .unwrap_or_else(|| "[]".into()),
            row.get::<Option<String>, _>("finish_info")
                .map_or(Value::Null, Value::String),
            row.get::<Option<String>, _>("resolver")
                .map_or(Value::Null, Value::String),
            row.get::<Option<String>, _>("capture_intervals")
                .unwrap_or_else(|| "[]".into()),
            true,
        ),
        None => (
            generated_body,
            "[]".into(),
            Value::String("[]".into()),
            Value::String(String::new()),
            "[]".into(),
            false,
        ),
    };
    let saved_capture_intervals = match serde_json::from_str::<Value>(&capture_intervals) {
        Ok(value) => value,
        Err(_) => return invalid("已保存采集时间轴无效"),
    };
    let server_elapsed_ms = train
        .get::<Option<i64>, _>("elapsed_ms")
        .unwrap_or(0)
        .max(0);
    envelope_json(
        StatusCode::OK,
        200,
        Some(json!({
            "protocolVersion": train.get::<Option<i32>, _>("protocol_version").unwrap_or(0),
            "attempt": train.get::<Option<i32>, _>("attempt").unwrap_or(0),
            "serverElapsedMs": server_elapsed_ms,
            "submitted": submitted,
            "savedCaptureIntervals": saved_capture_intervals,
            "messageBody": message_body,
            "messageKey": message_key,
            "finishInfo": finish_info,
            "standard": standard,
            "resolver": resolver
        })),
        "ok",
        "",
    )
}

pub(super) async fn save_content(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ContentUpdate>,
) -> Response {
    let Some(train_id) = body.train_id.filter(|value| !value.is_empty()) else {
        return invalid("训练ID不能为空");
    };
    let Some(floor_number) = body.floor_number.filter(|value| *value >= 1) else {
        return invalid("页码不能为空");
    };
    let Some(attempt) = body.attempt else {
        return invalid("训练轮次不能为空");
    };
    let Some(message_body) = body.message_body else {
        return invalid("拍发原始记录不能为空");
    };
    let Some(standard) = body.standard else {
        return invalid("点划基准记录不能为空");
    };
    let Some(capture_intervals) = body.capture_intervals else {
        return invalid("采集时间轴缺失");
    };
    let body_json = match serde_json::to_string(&message_body) {
        Ok(value) => value,
        Err(_) => return invalid("拍发原始记录无效"),
    };
    let standard_json = match serde_json::to_string(&standard) {
        Ok(value) => value,
        Err(_) => return invalid("点划基准记录无效"),
    };
    let capture_json = match serde_json::to_string(&capture_intervals) {
        Ok(value) => value,
        Err(_) => return invalid("采集时间轴无效"),
    };
    let characters = match count_characters(&message_body) {
        Ok(value) => value,
        Err(message) => return invalid(message),
    };
    if let Err(message) = validate_standard(&standard, characters > 0) {
        return invalid(message);
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "start save content"),
    };
    let train = match sqlx::query("SELECT id,protocol_version,attempt,status,message_number,type,is_random,is_average,start_time,CAST(TIMESTAMPDIFF(MICROSECOND,start_time,NOW(6)) DIV 1000 AS SIGNED) AS elapsed_ms,create_user FROM t_post_telegram_train WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(&train_id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row,
        Ok(None) => { let _ = tx.rollback().await; return envelope_response(StatusCode::OK, 202, "未查询到训练", "未查询到训练"); }
        Err(error) => { let _ = tx.rollback().await; return err(error, "lock save train"); }
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
    if train
        .get::<Option<i32>, _>("message_number")
        .is_none_or(|number| i64::from(floor_number) > (i64::from(number) + 99) / 100)
    {
        let _ = tx.rollback().await;
        return invalid("页码超出训练范围");
    }
    if train.get::<Option<i32>, _>("attempt") != Some(attempt) {
        let _ = tx.rollback().await;
        return envelope_response(
            StatusCode::OK,
            208,
            "训练轮次已变化，请重新加载训练",
            "训练轮次已变化，请重新加载训练",
        );
    }
    let elapsed_ms = train
        .get::<Option<i64>, _>("elapsed_ms")
        .unwrap_or(0)
        .max(0);
    let duration = match capture_duration(&capture_intervals, elapsed_ms) {
        Ok(value) => value,
        Err(message) => {
            let _ = tx.rollback().await;
            return invalid(message);
        }
    };
    if characters > 0 && duration == 0 {
        let _ = tx.rollback().await;
        return invalid("拍发正文缺少有效采集区间");
    }
    let existing = match sqlx::query("SELECT id,message_body,standard,capture_intervals,finish_info FROM t_post_telegram_train_floor_content_value WHERE train_id=? AND floor_number=? LIMIT 1")
        .bind(&train_id).bind(floor_number).fetch_optional(&mut *tx).await {
        Ok(value) => value,
        Err(error) => { let _ = tx.rollback().await; return err(error, "read save content value"); }
    };
    let finish_info = body.finish_info;
    if let Some(row) = &existing {
        let same = row.get::<Option<String>, _>("message_body").as_deref()
            == Some(body_json.as_str())
            && row.get::<Option<String>, _>("standard").as_deref() == Some(standard_json.as_str())
            && row.get::<Option<String>, _>("capture_intervals").as_deref()
                == Some(capture_json.as_str())
            && row.get::<Option<String>, _>("finish_info").as_deref() == finish_info.as_deref();
        if same {
            if let Err(error) = tx.commit().await {
                return err(error, "commit idempotent content");
            }
            return envelope_response(StatusCode::OK, 200, "ok", "");
        }
    }
    if train.get::<Option<i32>, _>("status") != Some(1)
        || train.get::<Option<i64>, _>("elapsed_ms").is_none()
    {
        let _ = tx.rollback().await;
        return invalid("训练不在进行中");
    }
    if let Some(row) = &existing {
        let previous: Vec<CaptureInterval> = match row.get::<Option<String>, _>("capture_intervals")
        {
            Some(value) => match serde_json::from_str(&value) {
                Ok(previous) => previous,
                Err(error) => {
                    tracing::warn!(%error, train_id = %train_id, floor_number, "stored capture intervals are invalid");
                    let _ = tx.rollback().await;
                    return envelope_response(
                        StatusCode::INTERNAL_SERVER_ERROR,
                        500,
                        "服务器错误",
                        "",
                    );
                }
            },
            None => {
                tracing::warn!(train_id = %train_id, floor_number, "stored capture intervals are missing");
                let _ = tx.rollback().await;
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
        if capture_intervals.len() <= previous.len() {
            let _ = tx.rollback().await;
            return invalid("页内容已变化，必须读取已确认记录后继续采集");
        }
        if previous
            .iter()
            .zip(&capture_intervals)
            .any(|(left, right)| left != right)
        {
            let _ = tx.rollback().await;
            return invalid("不能覆盖已确认的原始采集区间");
        }
    }
    let other_values = match sqlx::query("SELECT capture_intervals FROM t_post_telegram_train_floor_content_value WHERE train_id=? AND floor_number<>?")
        .bind(&train_id).bind(floor_number).fetch_all(&mut *tx).await {
        Ok(rows) => rows,
        Err(error) => { let _ = tx.rollback().await; return err(error, "read other capture values"); }
    };
    let mut pages = vec![capture_intervals.clone()];
    for row in other_values {
        let Some(value) = row.get::<Option<String>, _>("capture_intervals") else {
            tracing::warn!(train_id = %train_id, "stored page capture intervals are missing");
            let _ = tx.rollback().await;
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
        };
        let page = match serde_json::from_str::<Vec<CaptureInterval>>(&value) {
            Ok(page) => page,
            Err(error) => {
                tracing::warn!(%error, train_id = %train_id, "stored page capture intervals are invalid");
                let _ = tx.rollback().await;
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
            }
        };
        pages.push(page);
    }
    if let Err(message) = require_no_overlap(&pages) {
        let _ = tx.rollback().await;
        return invalid(message);
    }
    let source = match sqlx::query("SELECT id FROM t_post_telegram_train_floor_content WHERE train_id=? AND floor_number=? LIMIT 1")
        .bind(&train_id).bind(floor_number).fetch_optional(&mut *tx).await {
        Ok(source) => source,
        Err(error) => { let _ = tx.rollback().await; return err(error, "find source content"); }
    };
    if source.is_none() {
        if let Err(response) = generate_source_page(&mut tx, &train, &train_id, floor_number).await
        {
            let _ = tx.rollback().await;
            return response;
        }
    }
    let write_result = if let Some(row) = existing {
        sqlx::query("UPDATE t_post_telegram_train_floor_content_value SET attempt=?,message_body=?,standard=?,finish_info=?,capture_intervals=?,received_at=NOW(6),resolver=NULL WHERE id=?")
            .bind(attempt).bind(&body_json).bind(&standard_json).bind(&finish_info).bind(&capture_json).bind(row.get::<String, _>("id")).execute(&mut *tx).await
    } else {
        sqlx::query("INSERT INTO t_post_telegram_train_floor_content_value (id,train_id,floor_number,attempt,message_body,standard,finish_info,capture_intervals,received_at,resolver) VALUES (?,?,?,?,?,?,?, ?,NOW(6),NULL)")
            .bind(uuid::Uuid::new_v4().to_string()).bind(&train_id).bind(floor_number).bind(attempt).bind(&body_json).bind(&standard_json).bind(&finish_info).bind(&capture_json).execute(&mut *tx).await
    };
    if let Err(error) = write_result {
        let _ = tx.rollback().await;
        return err(error, "write content value");
    }
    if let Err(error) = sqlx::query("UPDATE t_post_telegram_train SET floor_now=GREATEST(COALESCE(floor_now,1),LEAST(?,CEIL(message_number/100))) WHERE id=? AND create_user=?")
        .bind(floor_number + 1).bind(&train_id).bind(&user.id).execute(&mut *tx).await {
        let _ = tx.rollback().await;
        return err(error, "update content floor");
    }
    match tx.commit().await {
        Ok(()) => envelope_response(StatusCode::OK, 200, "ok", ""),
        Err(error) => err(error, "commit save content"),
    }
}
