use super::dto::{
    business, err, generated_groups, ActionRequest, AddRequest, CaptureInterval, FinishPageRequest,
};
use super::parse::{
    canonical_messages, check_no_overlap, electronic_rule, parse_array, same_raw_messages,
    string_array, valid_intervals,
};
use super::settle::{action, delete_child_rows};
use crate::domain::post_telegraph::post_telegraph;
use crate::platform::auth::AuthUser;
use crate::platform::envelope::envelope_json;
use crate::platform::state::AppState;
use axum::extract::Extension;
use axum::extract::Query;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::Response;
use axum::Json;
use rust_decimal::Decimal;
use serde_json::Value;
use sqlx::Row;
use std::collections::BTreeMap;

pub(super) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<AddRequest>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return business(500, "服务器错误");
    };
    let Some(is_cable) = body.is_cable else {
        return business(202, "是否使用固定报底不能为空");
    };
    if !(0..=1).contains(&is_cable) {
        return business(202, "是否使用固定报底参数无效");
    }
    let Some(total_number) = body.total_number.filter(|value| *value >= 0) else {
        return business(202, "训练总组数不能为空");
    };
    if body
        .message_type
        .is_some_and(|message_type| !(0..=2).contains(&message_type))
    {
        return business(202, "报文类型无效");
    }
    let message_type = if is_cable == 0 {
        let Some(message_type) = body.message_type else {
            return business(202, "报文类型不能为空");
        };
        message_type
    } else {
        body.message_type.unwrap_or(0)
    };
    let Some(rule_id) = body
        .rule_id
        .as_deref()
        .filter(|value| !value.trim().is_empty())
        .map(str::to_owned)
    else {
        return business(202, "评分规则ID不能为空");
    };
    let cable_id = if is_cable == 1 {
        let Some(cable_id) = body
            .cable_id
            .as_deref()
            .filter(|value| !value.trim().is_empty())
            .map(str::to_owned)
        else {
            return business(202, "固定报编号不能为空");
        };
        if total_number / 100 == 0 {
            return business(202, "报文组数不足一页，无法建立训练");
        }
        Some(cable_id)
    } else {
        None
    };

    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "add-begin"),
    };
    let rule =
        match sqlx::query("SELECT content,score FROM t_grading_rule WHERE id=? LIMIT 1 FOR UPDATE")
            .bind(&rule_id)
            .fetch_optional(&mut *tx)
            .await
        {
            Ok(Some(row)) => row,
            Ok(None) => return business(202, "未查询到评分规则"),
            Err(error) => return err(error, "add-rule"),
        };
    let Some(rule_content) = rule.get::<Option<String>, _>("content") else {
        return business(202, "评分规则缺失");
    };
    if let Err(message) = electronic_rule(&rule_content) {
        return business(202, message);
    }
    let Some(score) = rule
        .get::<Option<i32>, _>("score")
        .filter(|score| *score >= 0)
    else {
        return business(202, "评分规则满分必须为非负整数");
    };
    let full_score = Decimal::from(score);
    let id = uuid::Uuid::new_v4().to_string();
    if let Err(error) = sqlx::query("INSERT INTO t_post_telegraph_key_pat_train (id,title,is_cable,status,duration,total_number,error_number,accuracy,speed,create_user_id,attempt,protocol_version,content,message_type,rule_id,rule_content,full_score,score,create_time) VALUES (?,?,?,0,'0',?,0,0,NULL,?,0,1,NULL,?,?,?,?,?,NOW())")
        .bind(&id)
        .bind(body.title.as_deref())
        .bind(is_cable)
        .bind(total_number)
        .bind(&user.id)
        .bind(message_type)
        .bind(&rule_id)
        .bind(&rule_content)
        .bind(full_score)
        .bind(full_score)
        .execute(&mut *tx)
        .await
    {
        return err(error, "add-train");
    }

    if is_cable == 0 {
        let generated_total = total_number.min(200) as usize;
        for offset in (0..generated_total).step_by(100) {
            let count = (generated_total - offset).min(100);
            for (sort, group) in generated_groups(message_type, count)
                .into_iter()
                .enumerate()
            {
                let key = serde_json::to_string(
                    &group
                        .chars()
                        .map(|character| character.to_string())
                        .collect::<Vec<_>>(),
                )
                .expect("generated report contains only JSON-safe characters");
                if let Err(error) = sqlx::query("INSERT INTO t_post_telegraph_key_pat_train_page (id,train_id,page_number,sort,`key`,value,time) VALUES (?,?,?,?,?,?,?)")
                    .bind(uuid::Uuid::new_v4().to_string())
                    .bind(&id)
                    .bind((offset / 100 + 1) as i32)
                    .bind(sort as i32)
                    .bind(key)
                    .bind("[]")
                    .bind("[]")
                    .execute(&mut *tx)
                    .await
                {
                    return err(error, "add-page");
                }
            }
        }
    } else {
        // This is the transaction's first consistent read. Only floor content is copied,
        // so one committed MVCC snapshot is sufficient even for child-first cable deletes.
        let floors = match sqlx::query("SELECT floor_number,sort,mores_key FROM t_cable_floor WHERE cable_id=? ORDER BY floor_number,sort")
            .bind(cable_id.as_deref())
            .fetch_all(&mut *tx)
            .await
        {
            Ok(rows) => rows,
            Err(error) => return err(error, "add-cable-pages"),
        };
        let mut grouped = BTreeMap::<i32, Vec<String>>::new();
        for floor in floors {
            let Some(floor_number) = floor.get::<Option<i32>, _>("floor_number") else {
                return business(202, "固定报页码缺失");
            };
            let Some(raw_key) = floor.get::<Option<String>, _>("mores_key") else {
                return business(202, "固定报内容缺失");
            };
            let key = match string_array(&raw_key) {
                Ok(values) => {
                    serde_json::to_string(&values).expect("fixed report characters are JSON-safe")
                }
                Err(_) => return business(202, "固定报内容不合法"),
            };
            grouped.entry(floor_number).or_default().push(key);
        }
        let grouped: Vec<Vec<String>> = grouped.into_iter().map(|(_, groups)| groups).collect();
        let requested_start = body.start_page.unwrap_or(1);
        let requested_start = if requested_start > 1 {
            let index = (requested_start - 1) as usize;
            if index < grouped.len() {
                index
            } else {
                0
            }
        } else {
            0
        };
        let page_count = (total_number / 100) as usize;
        if page_count > grouped.len().saturating_sub(requested_start) {
            return business(202, "所选电缆可用楼层不足");
        }
        for (page, groups) in grouped
            .into_iter()
            .skip(requested_start)
            .take(page_count)
            .enumerate()
        {
            for (sort, key) in groups.into_iter().enumerate() {
                if let Err(error) = sqlx::query("INSERT INTO t_post_telegraph_key_pat_train_page (id,train_id,page_number,sort,`key`,value,time) VALUES (?,?,?,?,?,?,?)")
                    .bind(uuid::Uuid::new_v4().to_string())
                    .bind(&id)
                    .bind(page as i32 + 1)
                    .bind(sort as i32)
                    .bind(key)
                    .bind("[]")
                    .bind("[]")
                    .execute(&mut *tx)
                    .await
                {
                    return err(error, "add-cable-page");
                }
            }
        }
    }
    if let Err(error) = tx.commit().await {
        return err(error, "add-commit");
    }
    match post_telegraph::owned_train_json(pool, &id, &user.id).await {
        Ok(Ok(value)) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Ok(Err(code)) => business(
            code,
            if code == 207 {
                "无权限"
            } else {
                "未查询到训练"
            },
        ),
        Err(error) => err(error, "add-detail"),
    }
}

pub(super) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    action(state, user, body, false).await
}
pub(super) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    action(state, user, body, true).await
}

pub(super) async fn reset(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<ActionRequest>,
) -> Response {
    let Some(id) = body.id.filter(|value| !value.trim().is_empty()) else {
        return business(202, "请求参数错误");
    };
    let Some(pool) = state.pool.as_ref() else {
        return business(500, "服务器错误");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return err(e, "reset-begin"),
    };
    let row = match sqlx::query("SELECT id,create_user_id,protocol_version,attempt FROM t_post_telegraph_key_pat_train WHERE id=? LIMIT 1 FOR UPDATE")
        .bind(&id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row,
        Ok(None) => return business(202, "未查询到训练"),
        Err(e) => return err(e, "reset-select"),
    };
    let id = row.get::<String, _>("id");
    if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return business(207, "无权限");
    }
    if row.get::<Option<i32>, _>("protocol_version").unwrap_or(0) != 1 {
        return business(202, "旧训练缺少采集时间轴，请终止旧训练并新建训练");
    }
    if row.get::<Option<i32>, _>("protocol_version").unwrap_or(0)
        != body.protocol_version.unwrap_or(-1)
        || row.get::<Option<i32>, _>("attempt").unwrap_or(-1) != body.attempt.unwrap_or(-1)
    {
        return business(208, "训练协议或轮次已失效，请重新读取训练");
    }
    for table in [
        "t_post_telegraph_key_pat_train_raw_page",
        "t_post_telegraph_key_pat_train_page_value",
        "t_post_telegraph_key_pat_train_more",
    ] {
        if let Err(e) = delete_child_rows(&mut tx, table, &id).await {
            return err(e, "reset-delete");
        }
    }
    let result = sqlx::query("UPDATE t_post_telegraph_key_pat_train SET status=0,attempt=attempt+1,begin_time=NULL,end_time=NULL,duration='0',speed='0',accuracy=0,error_number=0,score=full_score,deduct_info=NULL,content=NULL WHERE id=? AND create_user_id=?")
        .bind(&id).bind(&user.id).execute(&mut *tx).await;
    match result {
        Ok(result) if result.rows_affected() == 1 => {}
        Ok(_) => return business(208, "训练状态不可执行"),
        Err(e) => return err(e, "reset-update"),
    }
    if let Err(e) = tx.commit().await {
        return err(e, "reset-commit");
    }
    match post_telegraph::owned_train_json(pool, &id, &user.id).await {
        Ok(Ok(value)) => envelope_json(StatusCode::OK, 200, Some(value), "ok", ""),
        Ok(Err(code)) => business(code, "未查询到训练"),
        Err(e) => err(e, "reset-detail"),
    }
}

pub(super) async fn finish_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(dto): Json<FinishPageRequest>,
) -> Response {
    let Some(id) = dto.id.clone().filter(|value| !value.trim().is_empty()) else {
        return business(202, "请求参数错误");
    };
    let Some(page_number) = dto.page_number else {
        return business(202, "页码不能为空");
    };
    let Some(intervals) = dto.capture_intervals.clone() else {
        return business(202, "采集时间轴不能为空");
    };
    let Some(pool) = state.pool.as_ref() else {
        return business(500, "服务器错误");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(e) => return err(e, "finish-page-begin"),
    };
    let row = match sqlx::query("SELECT id,create_user_id,status,protocol_version,attempt,total_number,is_cable,message_type,CAST(begin_time IS NOT NULL AS SIGNED) AS begun,CAST(COALESCE(GREATEST(0,TIMESTAMPDIFF(MICROSECOND,begin_time,NOW(6)) DIV 1000),0) AS SIGNED) AS elapsed_ms FROM t_post_telegraph_key_pat_train WHERE id=? LIMIT 1 FOR UPDATE").bind(&id).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => row,
        Ok(None) => return business(202, "未查询到训练"),
        Err(e) => return err(e, "finish-page-select"),
    };
    let id = row.get::<String, _>("id");
    if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return business(207, "无权限");
    }
    if row.get::<Option<i32>, _>("protocol_version").unwrap_or(0) != 1 {
        return business(202, "旧训练缺少采集时间轴，请终止旧训练并新建训练");
    }
    if dto.protocol_version != Some(1) || dto.attempt != row.get::<Option<i32>, _>("attempt") {
        return business(208, "训练协议或轮次已失效，请重新读取训练");
    }
    let groups = if row.get::<Option<i32>, _>("is_cable").unwrap_or(0) == 1 {
        match sqlx::query(
            "SELECT COUNT(*) AS total FROM t_post_telegraph_key_pat_train_page WHERE train_id=?",
        )
        .bind(&id)
        .fetch_one(&mut *tx)
        .await
        {
            Ok(value) => value.get::<i64, _>("total") as i32,
            Err(e) => return err(e, "finish-page-source-count"),
        }
    } else {
        row.get::<Option<i32>, _>("total_number").unwrap_or(0)
    };
    let maximum_page = groups / 100 + i32::from(groups % 100 > 0);
    if page_number < 1 || maximum_page < 1 || page_number > maximum_page {
        return business(202, "页码不正确");
    }
    let values = match canonical_messages(&dto, &id, page_number) {
        Ok(values) => values,
        Err(message) => return business(202, message),
    };
    let raw_value = match serde_json::to_string(&values) {
        Ok(value) => value,
        Err(_) => return business(500, "服务器错误"),
    };
    let interval_json = match serde_json::to_string(&intervals) {
        Ok(value) => value,
        Err(_) => return business(202, "采集时间轴无效"),
    };
    let existing = match sqlx::query("SELECT id,attempt,value,capture_intervals FROM t_post_telegraph_key_pat_train_raw_page WHERE train_id=? AND page_number=? LIMIT 1").bind(&id).bind(page_number).fetch_optional(&mut *tx).await { Ok(row) => row, Err(e) => return err(e, "finish-page-existing") };
    if let Some(existing) = existing.as_ref() {
        if existing.get::<i32, _>("attempt") != dto.attempt.unwrap_or(-1) {
            return business(208, "已保存页轮次不匹配，请重新读取训练");
        }
        if existing.get::<String, _>("capture_intervals") == interval_json {
            let saved_value = existing.get::<String, _>("value");
            let same_content = if saved_value == raw_value {
                true
            } else {
                match same_raw_messages(&saved_value, &values, &id) {
                    Ok(same) => same,
                    Err(_) => return business(500, "已保存原始页内容无效"),
                }
            };
            if same_content {
                if let Err(e) = tx.rollback().await {
                    return err(e, "finish-page-rollback");
                }
                return post_telegraph::get_page(
                    State(state),
                    Extension(user),
                    Query(post_telegraph::PageQuery {
                        train_id: Some(id),
                        page_number: Some(page_number),
                    }),
                )
                .await;
            }
            return business(202, "同一采集时间轴内容冲突，请重新读取已保存页");
        }
        let old: Vec<CaptureInterval> =
            match serde_json::from_str(existing.get::<String, _>("capture_intervals").as_str()) {
                Ok(value) => value,
                Err(_) => return business(500, "已保存采集时间轴无效"),
            };
        if intervals.len() <= old.len()
            || intervals
                .iter()
                .zip(&old)
                .any(|(left, right)| left != right)
        {
            return business(208, "不能覆盖已确认的原始采集区间");
        }
    }
    if row.get::<Option<i32>, _>("status").unwrap_or(0) != 1
        || row.get::<Option<i64>, _>("begun").unwrap_or(0) == 0
    {
        return business(208, "训练尚未开始或已经结束");
    }
    let duration = match valid_intervals(
        &intervals,
        row.get::<Option<i64>, _>("elapsed_ms").unwrap_or(0),
    ) {
        Ok(duration) => duration,
        Err(message) => return business(202, message),
    };
    if duration == 0
        && values.iter().any(|value| {
            value
                .get("value")
                .and_then(Value::as_str)
                .is_some_and(|text| parse_array(text).is_ok_and(|array| !array.is_empty()))
        })
    {
        return business(202, "非空原始事件必须提供有效采集时长");
    }
    let all_rows = match sqlx::query("SELECT capture_intervals FROM t_post_telegraph_key_pat_train_raw_page WHERE train_id=? AND page_number<>?").bind(&id).bind(page_number).fetch_all(&mut *tx).await { Ok(rows) => rows, Err(e) => return err(e, "finish-page-overlap") };
    let mut timelines = Vec::with_capacity(all_rows.len() + 1);
    for raw in all_rows {
        match serde_json::from_str(raw.get::<String, _>("capture_intervals").as_str()) {
            Ok(value) => timelines.push(value),
            Err(_) => return business(500, "已保存采集时间轴无效"),
        }
    }
    timelines.push(intervals);
    if let Err(message) = check_no_overlap(timelines) {
        return business(202, message);
    }
    if row.get::<Option<i32>, _>("is_cable") == Some(0) {
        let generated = match sqlx::query_scalar::<_, i64>("SELECT EXISTS(SELECT 1 FROM t_post_telegraph_key_pat_train_page WHERE train_id=? AND page_number=?)")
            .bind(&id).bind(page_number).fetch_one(&mut *tx).await {
            Ok(generated) => generated != 0,
            Err(e) => return err(e, "finish-page-source-exists"),
        };
        if !generated {
            if let Err(e) =
                post_telegraph::generate_source_page(&mut tx, &row, &id, page_number).await
            {
                return err(e, "finish-page-source");
            }
        }
    }
    let result = if let Some(existing) = existing {
        sqlx::query("UPDATE t_post_telegraph_key_pat_train_raw_page SET value=?,capture_intervals=?,received_at=NOW(6) WHERE id=?").bind(raw_value).bind(interval_json).bind(existing.get::<String, _>("id")).execute(&mut *tx).await
    } else {
        sqlx::query("INSERT INTO t_post_telegraph_key_pat_train_raw_page (id,train_id,page_number,attempt,value,capture_intervals,received_at) VALUES (?,?,?,?,?,?,NOW(6))").bind(uuid::Uuid::new_v4().to_string()).bind(&id).bind(page_number).bind(dto.attempt.unwrap_or(0)).bind(raw_value).bind(interval_json).execute(&mut *tx).await
    };
    match result {
        Ok(_) => {}
        Err(e) => return err(e, "finish-page-save"),
    }
    if let Err(e) = tx.commit().await {
        return err(e, "finish-page-commit");
    }
    post_telegraph::get_page(
        State(state),
        Extension(user),
        Query(post_telegraph::PageQuery {
            train_id: Some(id),
            page_number: Some(page_number),
        }),
    )
    .await
}
