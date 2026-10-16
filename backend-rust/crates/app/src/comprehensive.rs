use std::collections::{HashMap, HashSet};
use std::fmt;

use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
};
use rust_decimal::Decimal;
use serde::Deserialize;
use serde_json::{json, Value};
use sqlx::{MySqlPool, Row};

use crate::platform::{
    auth::AuthUser,
    envelope::{envelope_json, envelope_response},
    state::AppState,
};

/// Query parameters used by both year reports.  The Java controller does not
/// validate or parse this value; it passes the string directly to SQL.
#[derive(Debug, Deserialize)]
pub(crate) struct YearQuery {
    pub(crate) year: Option<String>,
}

#[derive(Debug)]
enum ComprehensiveError {
    Database(sqlx::Error),
    Json(serde_json::Error),
    InvalidData(&'static str),
}

impl fmt::Display for ComprehensiveError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Database(error) => write!(formatter, "database query failed: {error}"),
            Self::Json(error) => write!(formatter, "invalid stored JSON: {error}"),
            Self::InvalidData(message) => formatter.write_str(message),
        }
    }
}

impl From<sqlx::Error> for ComprehensiveError {
    fn from(error: sqlx::Error) -> Self {
        Self::Database(error)
    }
}

impl From<serde_json::Error> for ComprehensiveError {
    fn from(error: serde_json::Error) -> Self {
        Self::Json(error)
    }
}

fn failure(error: ComprehensiveError) -> Response {
    tracing::warn!(error = %error, "comprehensive report query failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

fn decimal_json(value: Decimal) -> Result<Value, ComprehensiveError> {
    value
        .to_string()
        .parse::<serde_json::Number>()
        .map(Value::Number)
        .map_err(|_| ComprehensiveError::InvalidData("decimal value is not JSON-compatible"))
}

fn optional_decimal_json(value: Option<Decimal>) -> Result<Value, ComprehensiveError> {
    match value {
        Some(value) => decimal_json(value),
        None => Ok(Value::Null),
    }
}

/// GET /api/comprehensive/getUserOverallInfo
pub(crate) async fn get_user_overall_info(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };

    match user_overall_info(pool, &user.id).await {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(error) => failure(error),
    }
}

/// GET /api/comprehensive/getTheoryYear?year=yyyy
pub(crate) async fn get_theory_year(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<YearQuery>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };

    match theory_year(pool, &user.id, query.year.as_deref()).await {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(error) => failure(error),
    }
}

/// GET /api/comprehensive/getTheoryTestYear?year=yyyy
pub(crate) async fn get_theory_test_year(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<YearQuery>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };

    match theory_test_year(pool, &user.id, query.year.as_deref()).await {
        Ok(data) => envelope_json(StatusCode::OK, 200, Some(data), "ok", ""),
        Err(error) => failure(error),
    }
}

async fn user_overall_info(pool: &MySqlPool, user_id: &str) -> Result<Value, ComprehensiveError> {
    // Deliberately project only the fields represented by the safe user profile
    // response.  In particular, password, token, device_id, and any future
    // credential columns are not selected or serialized here.
    let user_row = sqlx::query(
        "SELECT id, wkno, phone, email, user_account, user_img, user_name, id_card, user_sex, update_flag, status, bday, eday, dday FROM t_user WHERE id = ? LIMIT 1",
    )
    .bind(user_id)
    .fetch_optional(pool)
    .await?
    .ok_or(ComprehensiveError::InvalidData("authenticated user was not found"))?;

    let user_entity = json!({
        "id": user_row.try_get::<String, _>("id")?,
        "wkno": user_row.try_get::<Option<String>, _>("wkno")?,
        "phone": user_row.try_get::<Option<String>, _>("phone")?,
        "email": user_row.try_get::<Option<String>, _>("email")?,
        "userAccount": user_row.try_get::<Option<String>, _>("user_account")?,
        "userImg": user_row.try_get::<Option<String>, _>("user_img")?,
        "userName": user_row.try_get::<Option<String>, _>("user_name")?,
        "idCard": user_row.try_get::<Option<String>, _>("id_card")?,
        "userSex": user_row.try_get::<Option<i32>, _>("user_sex")?,
        "updateFlag": user_row.try_get::<Option<String>, _>("update_flag")?,
        "status": user_row.try_get::<Option<i32>, _>("status")?,
        "bday": user_row.try_get::<Option<String>, _>("bday")?,
        "eday": user_row.try_get::<Option<String>, _>("eday")?,
        "dday": user_row.try_get::<Option<String>, _>("dday")?
    });

    let exam_num = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM t_theory_knowledge_exam_user WHERE user_id = ? AND state >= ?",
    )
    .bind(user_id)
    .bind(4_i32)
    .fetch_one(pool)
    .await? as i32;

    let pass_num = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(eu.score) FROM t_theory_knowledge_exam_user eu LEFT JOIN t_theory_knowledge_exam e ON e.id = eu.exam_id LEFT JOIN t_theory_knowledge_exam_test_paper tp ON e.id = tp.exam_id WHERE eu.user_id = ? AND eu.state = ? AND eu.score >= tp.pass_mark",
    )
    .bind(user_id)
    .bind(4_i32)
    .fetch_one(pool)
    .await? as i32;

    let swf_num = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM t_theory_knowledge_test_user WHERE user_id = ?",
    )
    .bind(user_id)
    .fetch_one(pool)
    .await? as i32;

    let study_time = sqlx::query(
        "SELECT COALESCE(ROUND(SUM(TIMESTAMPDIFF(SECOND, join_time, exit_time)) / 3600, 2), 0) AS study_time FROM t_theory_knowledge_swf_record WHERE user_id = ?",
    )
    .bind(user_id)
    .fetch_one(pool)
    .await?
    .try_get::<Option<Decimal>, _>("study_time")?
    .unwrap_or(Decimal::ZERO);

    let error_topic = count_error_topics(pool, user_id).await?;
    let total_credit = count_total_credit(pool, user_id).await?;

    let max_score = sqlx::query_scalar::<_, Option<i32>>(
        "SELECT MAX(score) FROM t_theory_knowledge_exam_user WHERE user_id = ? AND state = ? AND start_time IS NOT NULL AND end_time IS NOT NULL",
    )
    .bind(user_id)
    .bind(4_i32)
    .fetch_one(pool)
    .await?
    .map(Decimal::from)
    .unwrap_or(Decimal::ZERO);

    let min_score = sqlx::query_scalar::<_, Option<i32>>(
        "SELECT MIN(score) FROM t_theory_knowledge_exam_user WHERE user_id = ? AND state = ? AND start_time IS NOT NULL AND end_time IS NOT NULL",
    )
    .bind(user_id)
    .bind(4_i32)
    .fetch_one(pool)
    .await?
    .map(Decimal::from)
    .unwrap_or(Decimal::ZERO);

    let avg_score = sqlx::query_scalar::<_, Option<Decimal>>(
        "SELECT AVG(score) FROM t_theory_knowledge_exam_user WHERE user_id = ? AND state = ? AND start_time IS NOT NULL AND end_time IS NOT NULL",
    )
    .bind(user_id)
    .bind(4_i32)
    .fetch_one(pool)
    .await?
    .unwrap_or(Decimal::ZERO);

    let study_time_json = decimal_json(study_time)?;
    let total_credit_json = decimal_json(total_credit)?;
    let max_score_json = decimal_json(max_score)?;
    let min_score_json = decimal_json(min_score)?;
    let avg_score_json = decimal_json(avg_score)?;

    Ok(json!({
        "userEntity": user_entity,
        "examNum": exam_num,
        "passNum": pass_num,
        "swfNum": swf_num,
        "studyTime": study_time_json.clone(),
        "errorTopic": error_topic,
        "theoryTime": study_time_json,
        "totalCredit": total_credit_json,
        "theoryTestMaxCredit": max_score_json,
        "theoryTestMinCredit": min_score_json,
        "theoryTestAvgCredit": avg_score_json
    }))
}

async fn theory_year(
    pool: &MySqlPool,
    user_id: &str,
    year: Option<&str>,
) -> Result<Value, ComprehensiveError> {
    let type_credits = count_type_credit(pool, user_id, year).await?;
    let year_info = year_info(pool, user_id, year).await?;

    let base_credit = decimal_json(type_credits.get(&0).copied().unwrap_or(Decimal::ZERO))?;
    let equip_credit = decimal_json(type_credits.get(&1).copied().unwrap_or(Decimal::ZERO))?;
    let work_credit = decimal_json(type_credits.get(&2).copied().unwrap_or(Decimal::ZERO))?;

    Ok(json!({
        "baseTheoryCredit": base_credit,
        "equipCredit": equip_credit,
        "workCredit": work_credit,
        "yearInfoVO": year_info
    }))
}

async fn theory_test_year(
    pool: &MySqlPool,
    user_id: &str,
    year: Option<&str>,
) -> Result<Value, ComprehensiveError> {
    let theory_year_info = year_info(pool, user_id, year).await?;
    let year_pattern = java_year_pattern(year);
    let rows = sqlx::query(
        "SELECT t.month, ROUND(AVG(t.score), 2) AS avg_score FROM (SELECT score, DATE_FORMAT(end_time, '%c') AS month FROM t_theory_knowledge_exam_user WHERE user_id = ? AND state = ? AND start_time IS NOT NULL AND end_time IS NOT NULL AND end_time LIKE ?) t GROUP BY t.month ORDER BY CAST(t.month AS UNSIGNED)",
    )
    .bind(user_id)
    .bind(4_i32)
    .bind(year_pattern)
    .fetch_all(pool)
    .await?;

    let mut avg_scores = Vec::with_capacity(rows.len());
    for row in rows {
        avg_scores.push(json!({
            "month": row.try_get::<Option<String>, _>("month")?,
            "avgScore": optional_decimal_json(row.try_get::<Option<Decimal>, _>("avg_score")?)?
        }));
    }

    Ok(json!({
        "theoryYearInfoVOS": theory_year_info,
        "avgScores": avg_scores
    }))
}

async fn count_type_credit(
    pool: &MySqlPool,
    user_id: &str,
    year: Option<&str>,
) -> Result<HashMap<i32, Decimal>, ComprehensiveError> {
    // This is the SQL equivalent of ComprehensiveService.countTypeCredit:
    // a knowledge item contributes once only when every one of its chapters
    // has at least one answer row for this authenticated user.
    let year_value = year.unwrap_or("null");
    let rows = sqlx::query(
        "SELECT k.type, COALESCE(SUM(CAST(k.credit AS DECIMAL(30, 10))), 0) AS type_credit FROM t_theory_knowledge k WHERE FROM_UNIXTIME(CAST(k.create_time AS UNSIGNED) / 1000, '%Y') = ? AND EXISTS (SELECT 1 FROM t_theory_knowledge_swf ks0 WHERE ks0.knowledge_id = k.id) AND NOT EXISTS (SELECT 1 FROM t_theory_knowledge_swf ks1 WHERE ks1.knowledge_id = k.id AND NOT EXISTS (SELECT 1 FROM t_theory_knowledge_test_user ktu1 WHERE ktu1.knowledge_swf_id = ks1.id AND ktu1.user_id = ?)) GROUP BY k.type",
    )
    .bind(year_value)
    .bind(user_id)
    .fetch_all(pool)
    .await?;

    let mut credits = HashMap::with_capacity(rows.len());
    for row in rows {
        let kind = row
            .try_get::<Option<i32>, _>("type")?
            .ok_or(ComprehensiveError::InvalidData("knowledge type is null"))?;
        let credit = row
            .try_get::<Option<Decimal>, _>("type_credit")?
            .unwrap_or(Decimal::ZERO);
        credits.insert(kind, credit);
    }
    Ok(credits)
}

async fn count_total_credit(
    pool: &MySqlPool,
    user_id: &str,
) -> Result<Decimal, ComprehensiveError> {
    let total = sqlx::query_scalar::<_, Option<Decimal>>(
        "SELECT COALESCE(SUM(CAST(k.credit AS DECIMAL(30, 10))), 0) FROM t_theory_knowledge k WHERE k.credit IS NOT NULL AND EXISTS (SELECT 1 FROM t_theory_knowledge_swf ks0 WHERE ks0.knowledge_id = k.id) AND NOT EXISTS (SELECT 1 FROM t_theory_knowledge_swf ks1 WHERE ks1.knowledge_id = k.id AND NOT EXISTS (SELECT 1 FROM t_theory_knowledge_test_user ktu1 WHERE ktu1.knowledge_swf_id = ks1.id AND ktu1.user_id = ?))",
    )
    .bind(user_id)
    .fetch_one(pool)
    .await?;
    Ok(total.unwrap_or(Decimal::ZERO))
}

async fn year_info(
    pool: &MySqlPool,
    user_id: &str,
    year: Option<&str>,
) -> Result<Vec<Value>, ComprehensiveError> {
    let year_pattern = java_year_pattern(year);
    let swf_rows = sqlx::query(
        "SELECT FROM_UNIXTIME(CAST(create_time AS UNSIGNED) / 1000, '%Y-%m') AS month, COUNT(*) AS swf_num FROM t_theory_knowledge_test_user WHERE user_id = ? AND FROM_UNIXTIME(CAST(create_time AS UNSIGNED) / 1000, '%Y-%m') LIKE ? GROUP BY FROM_UNIXTIME(CAST(create_time AS UNSIGNED) / 1000, '%Y-%m')",
    )
    .bind(user_id)
    .bind(&year_pattern)
    .fetch_all(pool)
    .await?;

    let mut swf_by_month = HashMap::<u32, i32>::with_capacity(swf_rows.len());
    for row in swf_rows {
        let month = month_from_year_month(row.try_get::<Option<String>, _>("month")?)?;
        let count = row.try_get::<i64, _>("swf_num")? as i32;
        swf_by_month.insert(month, count);
    }

    let study_rows = sqlx::query(
        "SELECT DATE_FORMAT(join_time, '%Y-%m') AS month, ROUND(SUM(TIMESTAMPDIFF(SECOND, join_time, exit_time)) / 3600, 2) AS study_time FROM t_theory_knowledge_swf_record WHERE user_id = ? AND join_time LIKE ? GROUP BY DATE_FORMAT(join_time, '%Y-%m')",
    )
    .bind(user_id)
    .bind(&year_pattern)
    .fetch_all(pool)
    .await?;

    let mut study_by_month = HashMap::<u32, Decimal>::with_capacity(study_rows.len());
    for row in study_rows {
        let month = month_from_year_month(row.try_get::<Option<String>, _>("month")?)?;
        let time = row.try_get::<Option<Decimal>, _>("study_time")?.ok_or(
            ComprehensiveError::InvalidData("monthly study time is null"),
        )?;
        study_by_month.insert(month, time);
    }

    let mut result = Vec::with_capacity(12);
    for month in 1..=12 {
        let swf_num = swf_by_month.get(&month).copied();
        let study_time = study_by_month.get(&month).copied();
        if swf_num.is_some() || study_time.is_some() {
            result.push(json!({
                "moth": month.to_string(),
                "swfNum": swf_num,
                "studyTime": optional_decimal_json(study_time)?
            }));
        }
    }
    Ok(result)
}

fn java_year_pattern(year: Option<&str>) -> String {
    match year {
        Some(year) => format!("{year}%"),
        // Java's `year + "%"` turns a null reference into the literal
        // "null%"; it does not produce the parameter-error code 202.
        None => "null%".to_owned(),
    }
}

fn month_from_year_month(value: Option<String>) -> Result<u32, ComprehensiveError> {
    let value = value.ok_or(ComprehensiveError::InvalidData("monthly date is null"))?;
    let (_, month) = value
        .split_once('-')
        .ok_or(ComprehensiveError::InvalidData("monthly date is malformed"))?;
    month
        .parse::<u32>()
        .map_err(|_| ComprehensiveError::InvalidData("monthly date is malformed"))
}

struct SubmittedQuestion {
    id: String,
    answer: String,
}

async fn count_error_topics(
    pool: &MySqlPool,
    user_id: &str,
) -> Result<Vec<Value>, ComprehensiveError> {
    let exam_rows = sqlx::query(
        "SELECT content FROM t_theory_knowledge_exam_user WHERE user_id = ? AND end_time IS NOT NULL AND start_time IS NOT NULL AND state = ?",
    )
    .bind(user_id)
    .bind(4_i32)
    .fetch_all(pool)
    .await?;

    // The Java service uses this table as a read-through cache.  Reading it
    // preserves its number-based invalidation while keeping the Rust path
    // strictly read-only.
    let cached = sqlx::query(
        "SELECT number, content FROM t_theory_knowledge_test_fallible WHERE user_id = ? LIMIT 1",
    )
    .bind(user_id)
    .fetch_optional(pool)
    .await?;
    if let Some(row) = cached {
        let number = row.try_get::<Option<i32>, _>("number")?;
        if number == Some(exam_rows.len() as i32) {
            let content = row.try_get::<Option<String>, _>("content")?.ok_or(
                ComprehensiveError::InvalidData("fallible cache content is null"),
            )?;
            return cached_topics(&content);
        }
    }

    let mut submitted = Vec::new();
    for row in &exam_rows {
        if let Some(content) = row.try_get::<Option<String>, _>("content")? {
            extract_submitted_questions(&content, &mut submitted)?;
        }
    }
    if submitted.is_empty() {
        return Ok(Vec::new());
    }

    let mut unique_ids = Vec::with_capacity(submitted.len());
    let mut seen = HashSet::with_capacity(submitted.len());
    for question in &submitted {
        if seen.insert(question.id.as_str()) {
            unique_ids.push(question.id.as_str());
        }
    }

    let placeholders = vec!["?"; unique_ids.len()].join(",");
    let statement = format!(
        "SELECT id, topic, answer FROM t_theory_knowledge_test_paper_question WHERE id IN ({placeholders})"
    );
    let mut query = sqlx::query(&statement);
    for id in unique_ids {
        query = query.bind(id);
    }
    let question_rows = query.fetch_all(pool).await?;

    let mut questions = HashMap::with_capacity(question_rows.len());
    for row in question_rows {
        let id = row.try_get::<String, _>("id")?;
        questions.insert(
            id,
            (
                row.try_get::<Option<String>, _>("topic")?,
                row.try_get::<Option<String>, _>("answer")?,
            ),
        );
    }

    // Keep first occurrence as the deterministic tie-breaker.  Java's
    // HashMap tie ordering is unspecified, while its primary descending
    // frequency order is preserved here.
    let mut counts = HashMap::<String, (usize, usize, Option<String>)>::new();
    for (index, submitted_question) in submitted.iter().enumerate() {
        let Some((topic, expected_answer)) = questions.get(&submitted_question.id) else {
            continue;
        };
        if expected_answer.as_deref() == Some(submitted_question.answer.as_str()) {
            continue;
        }
        let entry = counts
            .entry(submitted_question.id.clone())
            .or_insert_with(|| (0, index, topic.clone()));
        entry.0 += 1;
    }

    let mut ranked = counts.into_iter().collect::<Vec<_>>();
    ranked.sort_by(
        |(_, (left_count, left_index, _)), (_, (right_count, right_index, _))| {
            right_count
                .cmp(left_count)
                .then_with(|| left_index.cmp(right_index))
        },
    );
    ranked.truncate(5);
    Ok(ranked
        .into_iter()
        .map(|(_, (_, _, topic))| json!(topic))
        .collect())
}

fn cached_topics(content: &str) -> Result<Vec<Value>, ComprehensiveError> {
    let value: Value = serde_json::from_str(content)?;
    let items = value.as_array().ok_or(ComprehensiveError::InvalidData(
        "fallible cache content is not an array",
    ))?;
    Ok(items
        .iter()
        .map(|item| item.get("topic").cloned().unwrap_or(Value::Null))
        .collect())
}

fn extract_submitted_questions(
    content: &str,
    destination: &mut Vec<SubmittedQuestion>,
) -> Result<(), ComprehensiveError> {
    let value: Value = serde_json::from_str(content)?;
    let sections = value.as_object().ok_or(ComprehensiveError::InvalidData(
        "exam content is not an object",
    ))?;
    for section in sections.values() {
        let questions = section.as_array().ok_or(ComprehensiveError::InvalidData(
            "exam question section is not an array",
        ))?;
        for question in questions {
            let question = question.as_object().ok_or(ComprehensiveError::InvalidData(
                "exam question is not an object",
            ))?;
            let id = scalar_string(question.get("id"))?;
            let answer = scalar_string(question.get("answer"))?;
            destination.push(SubmittedQuestion { id, answer });
        }
    }
    Ok(())
}

fn scalar_string(value: Option<&Value>) -> Result<String, ComprehensiveError> {
    let value = value.ok_or(ComprehensiveError::InvalidData(
        "exam question field is missing",
    ))?;
    match value {
        Value::String(value) => Ok(value.clone()),
        Value::Number(value) => Ok(value.to_string()),
        Value::Bool(value) => Ok(value.to_string()),
        Value::Array(_) | Value::Object(_) => java_object_string(value),
        // Java's `ma.get("answer").toString()` throws for a JSON null.
        Value::Null => Err(ComprehensiveError::InvalidData(
            "exam question field is null",
        )),
    }
}

fn java_object_string(value: &Value) -> Result<String, ComprehensiveError> {
    match value {
        Value::String(value) => Ok(value.clone()),
        Value::Number(value) => Ok(value.to_string()),
        Value::Bool(value) => Ok(value.to_string()),
        Value::Null => Ok("null".to_owned()),
        Value::Array(values) => {
            let elements = values
                .iter()
                .map(java_object_string)
                .collect::<Result<Vec<_>, _>>()?;
            Ok(format!("[{}]", elements.join(", ")))
        }
        Value::Object(values) => {
            let elements = values
                .iter()
                .map(|(key, item)| Ok(format!("{key}={}", java_object_string(item)?)))
                .collect::<Result<Vec<_>, ComprehensiveError>>()?;
            Ok(format!("{{{}}}", elements.join(", ")))
        }
    }
}
