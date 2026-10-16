use super::common::*;
use super::generate::participant_action;
use super::lifecycle::{add, delete, detail, finish_participant, list, set_status, statistic};
use crate::platform::auth::AuthUser;
use crate::platform::state::AppState;
use axum::extract::{Extension, Query, State};
use axum::response::Response;
use axum::Json;
use serde_json::{json, Value};
use sqlx::MySqlConnection;
use std::collections::HashMap;

use super::capture::stored_json;
use super::generate::frozen_rule;
pub(super) async fn key_add(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    add(s, u, b, Domain::Key).await
}
pub(super) async fn key_list(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    list(s, u, b, Domain::Key).await
}
pub(super) async fn key_detail(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    detail(s, u, b, Domain::Key).await
}
pub(super) async fn key_update(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    let id = b
        .get("trainId")
        .map(Value::to_string)
        .unwrap_or_default()
        .trim_matches('"')
        .to_owned();
    let status = b
        .get("status")
        .and_then(Value::as_i64)
        .and_then(|status| i32::try_from(status).ok())
        .unwrap_or(-1);
    set_status(s, u, id, status, Domain::Key).await
}
pub(super) async fn key_finish(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    finish_participant(s, u, b, Domain::Key).await
}
pub(super) async fn key_reset(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    participant_action(s, u, b, Domain::Key, true).await
}
pub(super) async fn key_start(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    participant_action(
        s,
        u,
        json!({"trainId":q.train_id,"attempt":q.attempt}),
        Domain::Key,
        false,
    )
    .await
}
pub(super) async fn key_delete(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    delete(s, u, q.train_id.unwrap_or_default(), Domain::Key).await
}
pub(super) async fn key_statistics(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    statistic(s, u, b, Domain::Key).await
}
pub(super) async fn settle_key(
    connection: &mut MySqlConnection,
    train: &Value,
    member: &Value,
    user_id: &str,
) -> Outcome<()> {
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let rule = frozen_rule(train, Domain::Key)?;
    let values = rows(
        connection,
        value_table(Domain::Key),
        value_fields(Domain::Key),
        "WHERE train_id=? AND user_id=? ORDER BY page_number,sort,id",
        &[&id, user_id],
    )
    .await?;
    let duration = member_capture_duration(member, Domain::Key, &values)?;
    let captures = stored_json(
        member["capturePages"]
            .as_str()
            .ok_or_else(|| invalid("缺少原始采集页索引"))?,
    )?;
    let captured_pages = captures
        .as_object()
        .ok_or_else(|| invalid("缺少原始采集页索引"))?;
    let sources = rows(
        connection,
        page_table(Domain::Key),
        page_fields(Domain::Key),
        "WHERE train_id=? ORDER BY page_number,sort",
        &[&id],
    )
    .await?;
    let mut source_by_page: HashMap<i64, Vec<Value>> = HashMap::new();
    for source in sources {
        source_by_page
            .entry(
                source["pageNumber"]
                    .as_i64()
                    .ok_or_else(|| invalid("报底页码缺失"))?,
            )
            .or_default()
            .push(source);
    }
    let mut values_by_page: HashMap<i64, Vec<Value>> = HashMap::new();
    for value in values {
        values_by_page
            .entry(
                value["pageNumber"]
                    .as_i64()
                    .ok_or_else(|| invalid("拍发页码缺失"))?,
            )
            .or_default()
            .push(value);
    }
    let mut statistics = KeySettlementStatistics::default();
    let mut characters = 0_i64;
    let mut pages = source_by_page.keys().copied().collect::<Vec<_>>();
    pages.sort_unstable();
    for page in pages {
        let source_rows = source_by_page
            .get(&page)
            .map(Vec::as_slice)
            .unwrap_or_default();
        if !captured_pages.contains_key(&page.to_string()) {
            continue;
        }
        let page_values = values_by_page
            .get(&page)
            .map(Vec::as_slice)
            .unwrap_or_default();
        let (captured, page_characters) = key_captured_groups(page_values, &mut statistics)?;
        characters += page_characters;
        align_key_groups(source_rows, &captured, &mut statistics)?;
    }
    let total_groups = if train["isCable"] == 1 {
        source_by_page.values().map(|rows| rows.len() as i64).sum()
    } else {
        train["totalNumber"]
            .as_i64()
            .ok_or_else(|| invalid("训练总组数缺失，无法结算"))?
    };
    let total_pages = (total_groups + 99) / 100;
    let remaining = total_groups % 100;
    let captured_count = captured_pages.len() as i64;
    if captured_count < total_pages {
        // Registered Java defect (GeneralKeyPatService.countScore, the missing-page block):
        // the per-missing-page loop and the closed formula below both add to the same lack
        // counters, so a missing page is charged twice. Replicated exactly until both stacks
        // are fixed together (decided 2026-10-11). `missing` is Java's `totalPageNumberList`
        // after `removeAll(capturedPages)`; its last entry takes the remainder branch.
        let captured_numbers = captured_pages
            .keys()
            .filter_map(|page| page.parse::<i64>().ok())
            .collect::<Vec<_>>();
        let missing = (1..=total_pages)
            .filter(|page| !captured_numbers.contains(page))
            .count() as i64;
        for index in 0..missing {
            if index == missing - 1 {
                if total_groups % 100 == 0 {
                    statistics.lack_line += 10;
                } else {
                    statistics.lack_line += remaining / 10;
                    if remaining % 10 > 0 {
                        statistics.more_group += remaining % 10;
                    }
                }
            } else {
                statistics.lack_line += 10;
            }
        }
        if remaining > 0 {
            statistics.lack_group += (total_pages - 1 - captured_count) * 100 + remaining;
            statistics.lack_line += (total_pages - 1 - captured_count) * 10 + remaining / 10
                - (statistics.pat_group / 10 + 1);
        } else {
            statistics.lack_group += (total_pages - captured_count) * 100;
            statistics.lack_line +=
                (total_pages - captured_count) * 10 - (statistics.pat_group / 10 + 1);
        }
    }
    let speed = if duration > 0 {
        (characters as f64 * 15_000.0 / duration as f64).round()
    } else {
        0.0
    };
    let score = score_key_categories(train, &rule, &statistics, speed)?;
    let accuracy_numerator = statistics.pat_group
        - statistics.error
        - statistics.bunch_group
        - statistics.lack
        - statistics.more;
    // Java ScoreMath.accuracy: the ratio is rounded to two places first, then scaled by 100.
    let accuracy_text = if statistics.pat_group <= 0 {
        "0".to_owned()
    } else {
        format!(
            "{:.2}",
            crate::shared::scoring::accuracy_percent(accuracy_numerator, statistics.pat_group)
        )
    };
    let sql = "UPDATE general_key_pat_user SET active_millis=?,duration=?,speed=?,accuracy=?,error_number=?,score=?,deduct_info=?,is_finish=1,finish_time=NOW(6) WHERE id=?";
    sqlx::query(sql)
        .bind(duration)
        .bind((duration / 1000).to_string())
        .bind(score_text(speed))
        .bind(accuracy_text)
        .bind(i32::try_from(statistics.error).map_err(|_| invalid("错误数量超出范围"))?)
        .bind(format!("{:.2}", score.0))
        .bind(score.1.to_string())
        .bind(text(&member["id"]).ok_or_else(|| invalid("参训记录主键缺失"))?)
        .execute(connection)
        .await?;
    Ok(())
}
