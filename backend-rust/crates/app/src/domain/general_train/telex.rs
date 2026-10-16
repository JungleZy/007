use super::capture::derive_telex_capture;
use super::common::*;
use super::generate::frozen_rule;
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

pub(super) async fn telex_add(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    add(s, u, b, Domain::Telex).await
}
pub(super) async fn telex_list(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    list(s, u, b, Domain::Telex).await
}
pub(super) async fn telex_detail(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    detail(s, u, b, Domain::Telex).await
}
pub(super) async fn telex_update(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    let id = b
        .get("trainId")
        .and_then(Value::as_str)
        .unwrap_or_default()
        .to_owned();
    let status = b
        .get("status")
        .and_then(Value::as_i64)
        .and_then(|status| i32::try_from(status).ok())
        .unwrap_or(-1);
    set_status(s, u, id, status, Domain::Telex).await
}
pub(super) async fn telex_finish(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    finish_participant(s, u, b, Domain::Telex).await
}
pub(super) async fn telex_start(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    participant_action(
        s,
        u,
        json!({"trainId":q.train_id,"attempt":q.attempt}),
        Domain::Telex,
        false,
    )
    .await
}
pub(super) async fn telex_delete(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Query(q): Query<QueryId>,
) -> Response {
    delete(s, u, q.train_id.unwrap_or_default(), Domain::Telex).await
}
pub(super) async fn telex_statistics(
    State(s): State<AppState>,
    Extension(u): Extension<AuthUser>,
    Json(b): Json<Value>,
) -> Response {
    statistic(s, u, b, Domain::Telex).await
}
/// Result of settling one telex member as Java returns it from memory: the response carries the
/// freshly computed BigDecimals ("92.00" / "0" accuracy, integer speed), not the stored columns.
pub(super) struct TelexSettled {
    pub(super) accuracy: String,
    pub(super) speed: String,
}

/// Java `countScore` + `settleMember` for the group telex training. Per page that has a raw
/// submission the Java comparison (`telex_handle::handle`) yields counters and the analysis
/// rows (`sort >= 0`); the old analysis rows of the member are replaced, the raw rows
/// (`sort = -1`) stay. Every write is by primary key (the child tables have no other index).
pub(super) async fn settle_telex(
    connection: &mut MySqlConnection,
    train: &Value,
    member: &Value,
    user_id: &str,
) -> Outcome<TelexSettled> {
    use crate::domain::telex::telex_handle::handle;
    use crate::domain::telex::telex_handle::PageKey;
    use crate::domain::telex::telex_handle::Stats;
    use crate::domain::telex::telex_handle::ValueRow;
    let id = text(&train["id"]).ok_or_else(|| invalid("训练ID不能为空"))?;
    let member_id = text(&member["id"]).ok_or_else(|| invalid("参训记录主键缺失"))?;
    let rule = frozen_rule(train, Domain::Telex)?;
    let speed = derive_telex_capture(
        connection,
        &member_id,
        &id,
        user_id,
        train["trainType"].as_i64(),
    )
    .await?;
    let values = rows(
        connection,
        value_table(Domain::Telex),
        value_fields(Domain::Telex),
        "WHERE train_id=? AND user_id=? AND sort=-1 ORDER BY page_number",
        &[&id, user_id],
    )
    .await?;
    member_capture_duration(member, Domain::Telex, &values)?;
    let sources = rows(
        connection,
        page_table(Domain::Telex),
        page_fields(Domain::Telex),
        "WHERE train_id=? ORDER BY page_number,sort",
        &[&id],
    )
    .await?;
    let mut source_by_page: std::collections::BTreeMap<i64, Vec<PageKey>> =
        std::collections::BTreeMap::new();
    for source in sources {
        source_by_page
            .entry(
                source["pageNumber"]
                    .as_i64()
                    .ok_or_else(|| invalid("报底页码缺失"))?,
            )
            .or_default()
            .push(PageKey {
                key: source["key"].as_str().unwrap_or_default().to_owned(),
                sort: source["sort"].as_i64().unwrap_or_default(),
            });
    }
    let mut submitted_by_page = HashMap::new();
    for value in &values {
        let page = value["pageNumber"]
            .as_i64()
            .ok_or_else(|| invalid("拍发页码缺失"))?;
        // Java takes the first raw row of the page.
        submitted_by_page
            .entry(page)
            .or_insert_with(|| value["value"].as_str().map(str::to_owned));
    }
    let mut stats = Stats::default();
    let mut analysis: Vec<ValueRow> = Vec::new();
    for (page, keys) in &source_by_page {
        if let Some(submitted) = submitted_by_page.get(page) {
            // A raw row without text is a Java NullPointerException inside `handle`.
            let submitted = submitted.as_deref().unwrap_or_default();
            handle(*page, keys, submitted, &mut stats, &mut analysis)
                .map_err(|_| java_exception())?;
        }
    }
    // Replace the member's analysis rows: plain-read the ids, delete and insert by primary key.
    let old: Vec<String> = sqlx::query_scalar(&format!(
        "SELECT CAST(id AS CHAR) FROM {} WHERE train_id=? AND user_id=? AND sort>-1 ORDER BY id",
        value_table(Domain::Telex)
    ))
    .bind(&id)
    .bind(user_id)
    .fetch_all(&mut *connection)
    .await?;
    for old_id in old {
        sqlx::query(&format!(
            "DELETE FROM {} WHERE id=?",
            value_table(Domain::Telex)
        ))
        .bind(old_id)
        .execute(&mut *connection)
        .await?;
    }
    for row in &analysis {
        sqlx::query(&format!(
            "INSERT INTO {} (id,train_id,user_id,page_number,sort,`key`,value) VALUES (?,?,?,?,?,?,?)",
            value_table(Domain::Telex)
        ))
        .bind(uuid::Uuid::new_v4().to_string())
        .bind(&id)
        .bind(user_id)
        .bind(row.page_number)
        .bind(row.sort)
        .bind(&row.key)
        .bind(&row.value)
        .execute(&mut *connection)
        .await?;
    }
    use rust_decimal::prelude::ToPrimitive;
    let speed_value = speed.to_i64().unwrap_or(0);
    let score = score_telex_categories(train, &rule, &stats, speed_value)?;
    // Java: no accuracy when the correct total is zero ("0"), otherwise the two-decimal ratio
    // times 100 ("92.00"); the correct total may be negative and is not clamped.
    let correct_total = stats.pat_group - stats.error_code - stats.much_less_code;
    let accuracy = if correct_total == 0 {
        rust_decimal::Decimal::ZERO
    } else if stats.pat_group == 0 {
        return Err(java_exception());
    } else {
        (rust_decimal::Decimal::from(correct_total) / rust_decimal::Decimal::from(stats.pat_group))
            .round_dp_with_strategy(2, rust_decimal::RoundingStrategy::MidpointAwayFromZero)
            * rust_decimal::Decimal::from(100)
    };
    let accuracy_text = if correct_total == 0 {
        "0".to_owned()
    } else {
        format!("{accuracy:.2}")
    };
    // `error_number` is not written: Java leaves the column at its creation value.
    let sql = "UPDATE general_telex_pat_user SET speed=?,accuracy=?,score=?,deduct_info=?,is_finish=1,finish_time=NOW(6) WHERE id=?";
    sqlx::query(sql)
        .bind(speed.to_string())
        .bind(format!("{accuracy:.2}"))
        .bind(format!("{:.2}", score.0))
        .bind(score.1.to_string())
        .bind(&member_id)
        .execute(&mut *connection)
        .await?;
    Ok(TelexSettled {
        accuracy: accuracy_text,
        speed: speed.to_string(),
    })
}

/// A Java runtime exception on the settlement path: HTTP 500 and a rolled-back transaction.
fn java_exception() -> Failure {
    Failure::Database(sqlx::Error::Protocol(
        "java-equivalent runtime exception while settling a telex training".into(),
    ))
}
