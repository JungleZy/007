use crate::platform::{
    auth::AuthUser,
    envelope::{envelope_json, envelope_response},
    state::AppState,
};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rand::{seq::SliceRandom, Rng};
use rust_decimal::{Decimal, RoundingStrategy};
use serde_json::{json, Map, Value};
use sqlx::{mysql::MySqlRow, MySql, MySqlPool, Row, Transaction};
use std::collections::{HashMap, HashSet};
use uuid::Uuid;

const NOT_STARTED: i32 = 0;
const UNDERWAY: i32 = 1;
const FINISHED: i32 = 2;

#[derive(Debug, serde::Deserialize)]
pub(crate) struct DeleteQuery {
    #[serde(rename = "trainId")]
    train_id: Option<String>,
}

fn database_error(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "military term training database operation failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

/// DECIMAL 列经 CAST(... AS CHAR) 读出，sqlx 无法直接把 DECIMAL 解码成 f64/String。
fn decimal_json(value: Option<String>) -> Value {
    value
        .and_then(|value| value.parse::<f64>().ok())
        .map_or(Value::Null, |value| json!(value))
}

fn train_json(row: &MySqlRow, types: Vec<String>, papers: Option<Vec<Value>>) -> Value {
    let mut result = json!({
        "id": row.get::<String, _>("id"),
        "userId": row.get::<Option<String>, _>("user_id"),
        "types": types,
        "name": row.get::<Option<String>, _>("name"),
        "status": row.get::<Option<i32>, _>("status"),
        "totalNumber": row.get::<Option<i32>, _>("total_number"),
        "correctNumber": row.get::<Option<i32>, _>("correct_number"),
        "errorNumber": row.get::<Option<i32>, _>("error_number"),
        "accuracy": decimal_json(row.get::<Option<String>, _>("accuracy")),
        "duration": row.get::<Option<i32>, _>("duration"),
        "score": decimal_json(row.get::<Option<String>, _>("score"))
    });
    // Java 的 VO 在无试卷时输出 "testPaperList": null。
    result["testPaperList"] = papers.map_or(Value::Null, Value::Array);
    result
}

fn parse_types(value: Option<String>) -> Result<Vec<String>, String> {
    let Some(value) = value else {
        return Ok(Vec::new());
    };
    serde_json::from_str(&value).map_err(|_| "训练类型数据无效".to_owned())
}

fn option_json(row: &MySqlRow, completed: bool) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "trainId": row.get::<Option<String>, _>("train_id"),
        "title": row.get::<Option<String>, _>("title"),
        "option": row.get::<Option<String>, _>("option"),
        "correctAnswer": if completed { row.get::<Option<String>, _>("correct_answer") } else { None },
        "userAnswer": row.get::<Option<String>, _>("user_answer")
    })
}

async fn type_names(pool: &MySqlPool, ids: &[String]) -> Result<Vec<String>, sqlx::Error> {
    if ids.is_empty() {
        return Ok(Vec::new());
    }
    let placeholders = std::iter::repeat("?")
        .take(ids.len())
        .collect::<Vec<_>>()
        .join(",");
    let query = format!("SELECT id, `key` FROM t_military_term_data WHERE id IN ({placeholders})");
    let mut statement = sqlx::query(&query);
    for id in ids {
        statement = statement.bind(id);
    }
    let mut names = HashMap::new();
    for row in statement.fetch_all(pool).await? {
        names.insert(
            row.get::<String, _>("id"),
            row.get::<Option<String>, _>("key").unwrap_or_default(),
        );
    }
    Ok(ids.iter().filter_map(|id| names.get(id).cloned()).collect())
}

async fn train_types(pool: &MySqlPool, row: &MySqlRow) -> Result<Vec<String>, String> {
    let ids = parse_types(row.get::<Option<String>, _>("types"))?;
    type_names(pool, &ids)
        .await
        .map_err(|_| "服务器错误".to_owned())
}

async fn papers_for_view(
    pool: &MySqlPool,
    train_id: &str,
    completed: bool,
) -> Result<Vec<Value>, sqlx::Error> {
    let rows = sqlx::query(
        "SELECT id,train_id,title,`option`,correct_answer,user_answer \
         FROM t_post_military_term_train_test_paper WHERE train_id=?",
    )
    .bind(train_id)
    .fetch_all(pool)
    .await?;
    Ok(rows.iter().map(|row| option_json(row, completed)).collect())
}

async fn load_owned(
    pool: &MySqlPool,
    id: &str,
    user: &AuthUser,
) -> Result<Result<MySqlRow, Response>, sqlx::Error> {
    let query = "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,CAST(accuracy AS CHAR) AS accuracy,duration,CAST(score AS CHAR) AS score \
         FROM t_post_military_term_train WHERE id=? LIMIT 1";
    match sqlx::query(query).bind(id).fetch_optional(pool).await? {
        Some(row)
            if row.get::<Option<String>, _>("user_id").as_deref() == Some(user.id.as_str()) =>
        {
            Ok(Ok(row))
        }
        Some(_) => Ok(Err(envelope_response(StatusCode::OK, 207, "无权限", ""))),
        None => Ok(Err(envelope_response(
            StatusCode::OK,
            202,
            "未查询到该训练",
            "",
        ))),
    }
}

pub(crate) async fn list_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let rows = match sqlx::query(
        "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,CAST(accuracy AS CHAR) AS accuracy,duration,CAST(score AS CHAR) AS score \
         FROM t_post_military_term_train WHERE user_id=? ORDER BY create_time DESC",
    )
    .bind(&user.id)
    .fetch_all(pool)
    .await {
        Ok(rows) => rows,
        Err(error) => return database_error(error, "list military term training"),
    };
    let mut result = Vec::with_capacity(rows.len());
    for row in &rows {
        let types = match train_types(pool, row).await {
            Ok(types) => types,
            Err(_) => {
                return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
            }
        };
        result.push(train_json(row, types, None));
    }
    envelope_json(StatusCode::OK, 200, Some(Value::Array(result)), "ok", "")
}

pub(crate) async fn details(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|id| !id.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let row = match load_owned(pool, id, &user).await {
        Ok(Ok(row)) => row,
        Ok(Err(response)) => return response,
        Err(error) => return database_error(error, "load military term training details"),
    };
    let types = match train_types(pool, &row).await {
        Ok(types) => types,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    let completed = row.get::<Option<i32>, _>("status") == Some(FINISHED);
    let papers = match papers_for_view(pool, id, completed).await {
        Ok(papers) => papers,
        Err(error) => return database_error(error, "load military term training papers"),
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(train_json(&row, types, Some(papers))),
        "ok",
        "",
    )
}

#[derive(Debug)]
struct CategoryTerm {
    key: String,
    value: String,
}

async fn category_terms(
    transaction: &mut Transaction<'_, MySql>,
    ids: &[String],
) -> Result<HashMap<String, Vec<CategoryTerm>>, sqlx::Error> {
    if ids.is_empty() {
        return Ok(HashMap::new());
    }
    let placeholders = std::iter::repeat("?")
        .take(ids.len())
        .collect::<Vec<_>>()
        .join(",");
    let query = format!("SELECT parent_id,`key`,value FROM t_military_term_data WHERE parent_id IN ({placeholders})");
    let mut result: HashMap<String, Vec<CategoryTerm>> = HashMap::new();
    let mut statement = sqlx::query(&query);
    for id in ids {
        statement = statement.bind(id);
    }
    for row in statement.fetch_all(&mut **transaction).await? {
        let Some(parent_id) = row.get::<Option<String>, _>("parent_id") else {
            continue;
        };
        let Some(key) = row.get::<Option<String>, _>("key") else {
            continue;
        };
        let Some(value) = row.get::<Option<String>, _>("value") else {
            continue;
        };
        result
            .entry(parent_id)
            .or_default()
            .push(CategoryTerm { key, value });
    }
    Ok(result)
}

/// Java String.hashCode：按 UTF-16 码元计算。
fn java_hash(value: &str) -> i32 {
    value.encode_utf16().fold(0i32, |hash, unit| {
        hash.wrapping_mul(31).wrapping_add(i32::from(unit))
    })
}

/// 出题失败：Business 对应 Java 的 IllegalArgumentException（202），
/// Fault 对应 Java 的 NumberFormatException 等未处理异常（500）。
#[derive(Debug, PartialEq)]
enum PaperError {
    Business(String),
    Fault,
}

/// Java 正则 `.` 不匹配的行终止符。
fn is_line_terminator(ch: char) -> bool {
    matches!(ch, '\n' | '\r' | '\u{85}' | '\u{2028}' | '\u{2029}')
}

/// Java String.trim：去掉两端所有 <= U+0020 的字符。
fn java_trim(value: &str) -> &str {
    value.trim_matches(|ch: char| ch <= ' ')
}

/// Java `split(str)`：丢弃末尾的空串。
fn java_split<'a>(value: &'a str, separator: char) -> Vec<&'a str> {
    let mut parts = value.split(separator).collect::<Vec<_>>();
    while parts.len() > 1 && parts.last().is_some_and(|part| part.is_empty()) {
        parts.pop();
    }
    if parts.len() == 1 && parts[0].is_empty() && !value.is_empty() {
        parts.clear();
    }
    parts
}

/// `Pattern.matches("^.*\\d{1,}.{0,3}～\\d{1,}.*")`
fn matches_number_range(answer: &str) -> bool {
    if answer.chars().any(is_line_terminator) {
        return false;
    }
    let chars = answer.chars().collect::<Vec<_>>();
    (0..chars.len()).any(|p| {
        chars[p] == '～'
            && chars.get(p + 1).is_some_and(char::is_ascii_digit)
            && (p.saturating_sub(4)..p).any(|q| chars[q].is_ascii_digit())
    })
}

/// `Pattern.matches("^.*、.{1,}、.{1,}$.*")`
fn matches_enumeration(answer: &str) -> bool {
    if answer.chars().any(is_line_terminator) {
        return false;
    }
    let chars = answer.chars().collect::<Vec<_>>();
    let marks = (0..chars.len())
        .filter(|i| chars[*i] == '、')
        .collect::<Vec<_>>();
    marks
        .iter()
        .any(|i| marks.iter().any(|j| *j >= i + 2 && j + 2 <= chars.len()))
}

fn replace_first(answer: &str, from: &str, to: &str) -> String {
    answer.replacen(from, to, 1)
}

fn push_if_new(options: &mut Vec<String>, candidate: String) -> bool {
    if options.contains(&candidate) {
        false
    } else {
        options.push(candidate);
        true
    }
}

/// 数字区间：把 `～` 两侧的数字各加一个数量级单位（Java 逐字符口径，含 int 溢出回绕与 NumberFormatException）。
fn number_range_variant(answer: &str) -> Result<String, PaperError> {
    let parts = java_split(answer, '～');
    let (Some(first), Some(second)) = (parts.first(), parts.get(1)) else {
        return Err(PaperError::Fault);
    };
    let first_units = java_trim(first).encode_utf16().collect::<Vec<_>>();
    let second_units = java_trim(second).encode_utf16().collect::<Vec<_>>();
    let is_digit = |unit: u16| (48..=57).contains(&unit);
    let mut first_digits = Vec::new();
    for index in (0..first_units.len()).rev() {
        let unit = first_units[index];
        if is_digit(unit) {
            first_digits.push(unit);
            continue;
        }
        if (index as i64) < (first_units.len() as i64 - 1) - 3 {
            break;
        }
    }
    let mut last_digits = Vec::new();
    for unit in &second_units {
        if is_digit(*unit) {
            last_digits.push(*unit);
            continue;
        }
        break;
    }
    first_digits.reverse();
    let first_text = String::from_utf16(&first_digits).map_err(|_| PaperError::Fault)?;
    let last_text = String::from_utf16(&last_digits).map_err(|_| PaperError::Fault)?;
    let first_add = format!("1{}", "0".repeat(first_text.len().saturating_sub(1)));
    let last_add = format!("1{}", "0".repeat(last_text.len().saturating_sub(1)));
    let parse = |text: &str| text.parse::<i32>().map_err(|_| PaperError::Fault);
    let new_first = parse(&first_add)?
        .wrapping_add(parse(&first_text)?)
        .to_string();
    let new_last = parse(&last_add)?
        .wrapping_add(parse(&last_text)?)
        .to_string();
    Ok(replace_first(
        &replace_first(answer, &last_text, &new_last),
        &first_text,
        &new_first,
    ))
}

/// Java `checkKeyword` 的完整移植：按相同顺序尝试各规则，命中且不与已有选项重复则追加并返回 true。
fn check_keyword(
    answer: &str,
    options: &mut Vec<String>,
    rng: &mut impl Rng,
) -> Result<bool, PaperError> {
    let mut flag = false;
    if matches_number_range(answer) {
        let variant = number_range_variant(answer)?;
        if push_if_new(options, variant) {
            flag = true;
        }
    }
    if !flag && answer.contains("无线") {
        flag = push_if_new(options, replace_first(answer, "无线", "有线"));
    }
    if !flag && (answer.contains("出口") || answer.contains("入口")) {
        let variant = if answer.contains("出口") {
            replace_first(answer, "出口", "入口")
        } else {
            replace_first(answer, "入口", "出口")
        };
        flag = push_if_new(options, variant);
    }
    if !flag && answer.contains("干线") {
        flag = push_if_new(options, replace_first(answer, "干线", "主线"));
    }
    if !flag && answer.contains("小时") {
        flag = push_if_new(options, answer.replace("小时", "分钟"));
    }
    if !flag && (answer.contains("线状") || answer.contains("面状")) {
        let variant = if answer.contains("线状") {
            replace_first(answer, "线状", "面状")
        } else {
            replace_first(answer, "面状", "线状")
        };
        flag = push_if_new(options, variant);
    }
    if !flag && (answer.contains("接收") || answer.contains("发射")) {
        let variant = if answer.contains("接收") {
            replace_first(answer, "接收", "发射")
        } else {
            replace_first(answer, "发射", "接收")
        };
        flag = push_if_new(options, variant);
    }
    if !flag && (answer.contains("战术") || answer.contains("战役")) {
        let variant = if answer.contains("战术") {
            replace_first(answer, "战术", "战役")
        } else {
            replace_first(answer, "战役", "战术")
        };
        flag = push_if_new(options, variant);
    }
    if !flag && matches_enumeration(answer) {
        let mut parts = java_split(answer, '、');
        if parts.len() > 3 {
            parts.remove(1);
            flag = push_if_new(options, parts.join("、"));
        }
    }
    if !flag
        && !answer.chars().any(is_line_terminator)
        && answer.chars().any(|ch| ch.is_ascii_digit())
    {
        let start = answer.find(|ch: char| ch.is_ascii_digit());
        if let Some(start) = start {
            let number = answer[start..]
                .chars()
                .take_while(char::is_ascii_digit)
                .collect::<String>();
            let parsed = number.parse::<i32>().map_err(|_| PaperError::Fault)?;
            let mut replacement = rng.gen_range(1..=9);
            while replacement == parsed {
                replacement = rng.gen_range(1..=9);
            }
            let variant = replace_first(answer, &number, &replacement.to_string());
            flag = push_if_new(options, variant);
        }
    }
    Ok(flag)
}

/// 对应 Java generateTestPaper 的单题逻辑（含 100 次尝试后的顺序补足降级）。
fn make_paper(
    category_id: &str,
    category: &[CategoryTerm],
    rng: &mut impl Rng,
) -> Result<(String, String, String), PaperError> {
    let distinct = category
        .iter()
        .filter(|term| !term.value.is_empty())
        .map(|term| term.value.as_str())
        .collect::<HashSet<_>>();
    if distinct.len() < 4 {
        return Err(PaperError::Business(format!(
            "类型 {category_id} 有效题目不足4条，无法生成干扰项"
        )));
    }
    let title_index = if category.len() == 1 {
        0
    } else {
        rng.gen_range(0..category.len())
    };
    let answer = &category[title_index];
    let mut options = vec![answer.value.clone()];
    let mut flag = 1;
    if check_keyword(&answer.value, &mut options, rng)? {
        flag = 2;
    }
    let mut attempts = 0;
    while flag <= 3 {
        attempts += 1;
        if attempts > 100 {
            for term in category {
                if !term.value.is_empty() && !options.contains(&term.value) {
                    options.push(term.value.clone());
                    if options.len() >= 4 {
                        break;
                    }
                }
            }
            break;
        }
        let option_id = if category.len() == 1 {
            0
        } else {
            rng.gen_range(0..category.len())
        };
        if title_index != option_id || option_id == 0 {
            let value = &category[option_id].value;
            if !value.is_empty() {
                if options.contains(value) {
                    if check_keyword(value, &mut options, rng)? {
                        flag += 1;
                    }
                } else {
                    options.push(value.clone());
                    flag += 1;
                }
            }
        }
    }
    options.sort_by_key(|value| java_hash(value));
    let mut correct = String::new();
    let mut map = Map::new();
    for (index, option) in options.into_iter().enumerate() {
        let letter = ((b'A' + index as u8) as char).to_string();
        if option == answer.value {
            correct = letter.clone();
        }
        map.insert(letter, Value::String(option));
    }
    Ok((
        answer.key.clone(),
        serde_json::to_string(&map).map_err(|_| PaperError::Fault)?,
        correct,
    ))
}

pub(crate) async fn add(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let total = match body.get("totalNumber").and_then(Value::as_i64) {
        Some(value) if (0..=i32::MAX as i64).contains(&value) => value as i32,
        _ => return envelope_response(StatusCode::OK, 202, "数量不能为空", ""),
    };
    let requested_types = match body.get("types") {
        None | Some(Value::Null) => Vec::new(),
        Some(Value::Array(values)) => {
            let mut types = Vec::with_capacity(values.len());
            for value in values {
                let Some(value) = value.as_str() else {
                    return envelope_response(StatusCode::OK, 202, "训练类型格式无效", "");
                };
                types.push(value.to_owned());
            }
            types
        }
        Some(_) => return envelope_response(StatusCode::OK, 202, "训练类型格式无效", ""),
    };
    let effective_types = if requested_types.is_empty() {
        match sqlx::query(
            "SELECT id FROM t_military_term_data WHERE parent_id='0' ORDER BY sort,id",
        )
        .fetch_all(pool)
        .await
        {
            Ok(rows) => rows
                .into_iter()
                .map(|row| row.get::<String, _>("id"))
                .collect::<Vec<_>>(),
            Err(error) => return database_error(error, "load military term categories"),
        }
    } else {
        requested_types.clone()
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start military term training transaction"),
    };
    let categories = match category_terms(&mut transaction, &effective_types).await {
        Ok(categories) => categories,
        Err(error) => return database_error(error, "load military term data"),
    };
    let valid_types = effective_types
        .iter()
        .filter(|id| categories.get(*id).is_some_and(|terms| terms.len() >= 4))
        .cloned()
        .collect::<Vec<_>>();
    // Java 仅在所选类型下一条题目都没有（dataMap 为空）时抛错；有题但都不足 4 条时，
    // totalNumber=0 不进出题循环，照常成功（total>0 才会在出题处失败，此处仍返回 202）。
    if valid_types.is_empty() && (total > 0 || categories.is_empty()) {
        let _ = transaction.rollback().await;
        return envelope_response(
            StatusCode::OK,
            202,
            &format!(
                "类型 {} 该类型条目少于4条，不能生成训练，请重新选择类型",
                effective_types.join(",")
            ),
            "",
        );
    }
    let id = Uuid::new_v4().to_string();
    let stored_types = match serde_json::to_string(&requested_types) {
        Ok(value) => value,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if let Err(error) = sqlx::query(
        "INSERT INTO t_post_military_term_train \
         (id,user_id,types,name,status,total_number,correct_number,error_number,accuracy,duration,score,create_time) \
         VALUES (?,?,?,?,0,?,?,?,0,0,0,NOW())",
    )
    .bind(&id)
    .bind(&user.id)
    .bind(stored_types)
    .bind(body.get("name").and_then(Value::as_str))
    .bind(total)
    .bind(0i32)
    .bind(0i32)
    .execute(&mut *transaction)
    .await {
        return database_error(error, "create military term training");
    }
    let generated_papers = {
        let mut rng = rand::thread_rng();
        let mut generated = Vec::with_capacity(total as usize);
        for _ in 0..total {
            let Some(category_id) = valid_types.choose(&mut rng) else {
                return envelope_response(StatusCode::OK, 202, "训练类型为空", "");
            };
            let Some(terms) = categories.get(category_id) else {
                return envelope_response(StatusCode::OK, 202, "训练类型无效", "");
            };
            let paper = match make_paper(category_id, terms, &mut rng) {
                Ok(paper) => paper,
                Err(PaperError::Business(message)) => {
                    return envelope_response(StatusCode::OK, 202, &message, &message)
                }
                Err(PaperError::Fault) => {
                    return envelope_response(
                        StatusCode::INTERNAL_SERVER_ERROR,
                        500,
                        "服务器错误",
                        "",
                    )
                }
            };
            generated.push(paper);
        }
        generated
    };
    for (title, options, correct) in generated_papers {
        if let Err(error) = sqlx::query(
            "INSERT INTO t_post_military_term_train_test_paper (id,train_id,title,`option`,correct_answer,user_answer) VALUES (?,?,?,?,?,NULL)",
        )
        .bind(Uuid::new_v4().to_string())
        .bind(&id)
        .bind(title)
        .bind(options)
        .bind(correct)
        .execute(&mut *transaction)
        .await
        {
            return database_error(error, "create military term training paper");
        }
    }
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit military term training");
    }
    let types = match type_names(pool, &valid_types).await {
        Ok(types) => types,
        Err(error) => return database_error(error, "load military term names"),
    };
    let row = match sqlx::query(
        "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,CAST(accuracy AS CHAR) AS accuracy,duration,CAST(score AS CHAR) AS score \
         FROM t_post_military_term_train WHERE id=?",
    ).bind(&id).fetch_one(pool).await {
        Ok(row) => row,
        Err(error) => return database_error(error, "reload military term training"),
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(train_json(&row, types, None)),
        "ok",
        "",
    )
}

pub(crate) async fn begin(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|id| !id.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start military term begin transaction"),
    };
    let row = match sqlx::query(
        "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,CAST(accuracy AS CHAR) AS accuracy,duration,CAST(score AS CHAR) AS score \
         FROM t_post_military_term_train WHERE id=? LIMIT 1 FOR UPDATE",
    ).bind(id).fetch_optional(&mut *transaction).await {
        Ok(Some(row)) if row.get::<Option<String>, _>("user_id").as_deref() == Some(user.id.as_str()) => row,
        Ok(Some(_)) => return envelope_response(StatusCode::OK, 207, "无权限", ""),
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该训练", ""),
        Err(error) => return database_error(error, "load military term for begin"),
    };
    match row.get::<Option<i32>, _>("status") {
        Some(FINISHED) => {
            return envelope_response(StatusCode::OK, 208, "训练已完成，不能重新开始", "")
        }
        Some(UNDERWAY) => {}
        Some(NOT_STARTED) => {
            if let Err(error) = sqlx::query(
                "UPDATE t_post_military_term_train SET status=1,start_time=NOW() WHERE id=?",
            )
            .bind(id)
            .execute(&mut *transaction)
            .await
            {
                return database_error(error, "begin military term training");
            }
        }
        _ => return envelope_response(StatusCode::OK, 202, "训练状态无效", ""),
    }
    let current = match sqlx::query(
        "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,CAST(accuracy AS CHAR) AS accuracy,duration,CAST(score AS CHAR) AS score \
         FROM t_post_military_term_train WHERE id=?",
    ).bind(id).fetch_one(&mut *transaction).await {
        Ok(row) => row,
        Err(error) => return database_error(error, "reload military term after begin"),
    };
    let type_ids = match parse_types(current.get::<Option<String>, _>("types")) {
        Ok(ids) => ids,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit military term begin");
    }
    let types = match type_names(pool, &type_ids).await {
        Ok(types) => types,
        Err(error) => return database_error(error, "load military term names after begin"),
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(train_json(&current, types, None)),
        "ok",
        "",
    )
}

fn answer_value(body: &Value) -> Option<&str> {
    body.get("userAnswer").and_then(Value::as_str)
}

pub(crate) async fn finish(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<Value>,
) -> Response {
    let Some(id) = body
        .get("id")
        .and_then(Value::as_str)
        .filter(|id| !id.is_empty())
    else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    let submitted: &[Value] = match body.get("testPaperList") {
        None | Some(Value::Null) => &[],
        Some(Value::Array(values)) => values,
        Some(_) => return envelope_response(StatusCode::OK, 202, "提交的答案格式无效", ""),
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start military term finish transaction"),
    };
    let train = match sqlx::query(
        "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,CAST(accuracy AS CHAR) AS accuracy,duration,CAST(score AS CHAR) AS score,IF(start_time IS NULL,0,1) AS has_start_time \
         FROM t_post_military_term_train WHERE id=? LIMIT 1 FOR UPDATE",
    ).bind(id).fetch_optional(&mut *transaction).await {
        Ok(Some(row)) if row.get::<Option<String>, _>("user_id").as_deref() == Some(user.id.as_str()) => row,
        Ok(Some(_)) => return envelope_response(StatusCode::OK, 207, "无权限", ""),
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该训练", ""),
        Err(error) => return database_error(error, "load military term for finish"),
    };
    let papers = match sqlx::query(
        "SELECT id,train_id,title,`option`,correct_answer,user_answer FROM t_post_military_term_train_test_paper WHERE train_id=?",
    ).bind(id).fetch_all(&mut *transaction).await {
        Ok(rows) => rows,
        Err(error) => return database_error(error, "load military term papers"),
    };
    let paper_ids = papers
        .iter()
        .filter_map(|paper| paper.get::<Option<String>, _>("id"))
        .collect::<HashSet<_>>();
    let mut seen = HashSet::new();
    let mut answers = HashMap::new();
    for answer in submitted {
        let Some(answer_id) = answer
            .get("id")
            .and_then(Value::as_str)
            .filter(|id| !id.is_empty())
        else {
            return envelope_response(StatusCode::OK, 202, "提交的题目无效或重复", "");
        };
        if !paper_ids.contains(answer_id) || !seen.insert(answer_id.to_owned()) {
            return envelope_response(StatusCode::OK, 202, "提交的题目无效或重复", "");
        }
        if answer
            .get("userAnswer")
            .is_some_and(|value| !value.is_null() && !value.is_string())
        {
            return envelope_response(StatusCode::OK, 202, "提交的答案格式无效", "");
        }
        answers.insert(
            answer_id.to_owned(),
            answer_value(answer).map(ToOwned::to_owned),
        );
    }
    for paper in &papers {
        let Some(paper_id) = paper.get::<Option<String>, _>("id") else {
            continue;
        };
        let Some(Some(answer)) = answers.get(&paper_id) else {
            continue;
        };
        let options: HashMap<String, String> = match serde_json::from_str::<HashMap<String, String>>(
            paper
                .get::<Option<String>, _>("option")
                .as_deref()
                .unwrap_or(""),
        ) {
            Ok(options) if !options.is_empty() => options,
            _ => return envelope_response(StatusCode::OK, 202, "题目选项无效", ""),
        };
        let Some(correct) = paper.get::<Option<String>, _>("correct_answer") else {
            return envelope_response(StatusCode::OK, 202, "提交的答案无效", "");
        };
        if !options.contains_key(&correct) || !options.contains_key(answer) {
            return envelope_response(StatusCode::OK, 202, "提交的答案无效", "");
        }
    }
    if train.get::<Option<i32>, _>("status") == Some(FINISHED) {
        let unchanged = papers.iter().all(|paper| {
            let id = paper.get::<Option<String>, _>("id");
            id.as_ref().is_some_and(|id| {
                paper.get::<Option<String>, _>("user_answer") == answers.get(id).cloned().flatten()
            })
        });
        if unchanged {
            let type_ids = match parse_types(train.get::<Option<String>, _>("types")) {
                Ok(ids) => ids,
                Err(_) => {
                    return envelope_response(
                        StatusCode::INTERNAL_SERVER_ERROR,
                        500,
                        "服务器错误",
                        "",
                    )
                }
            };
            if let Err(error) = transaction.commit().await {
                return database_error(error, "commit idempotent military term finish");
            }
            let types = match type_names(pool, &type_ids).await {
                Ok(types) => types,
                Err(error) => return database_error(error, "load completed military term names"),
            };
            return envelope_json(
                StatusCode::OK,
                200,
                Some(train_json(&train, types, None)),
                "ok",
                "",
            );
        }
        return envelope_response(StatusCode::OK, 208, "训练已完成，不能修改结果", "");
    }
    if train.get::<Option<i32>, _>("status") != Some(UNDERWAY) {
        return envelope_response(StatusCode::OK, 202, "训练尚未开始", "");
    }
    if train.get::<Option<i32>, _>("has_start_time") != Some(1) {
        return envelope_response(StatusCode::OK, 208, "训练开始时间缺失，不能完成", "");
    }
    let mut correct = 0i32;
    let mut answered = 0i32;
    for paper in &papers {
        let Some(paper_id) = paper.get::<Option<String>, _>("id") else {
            continue;
        };
        let answer = answers.get(&paper_id).cloned().flatten();
        if let Some(answer_value) = &answer {
            answered += 1;
            let options: HashMap<String, String> = serde_json::from_str(
                paper
                    .get::<Option<String>, _>("option")
                    .as_deref()
                    .unwrap_or("{}"),
            )
            .unwrap_or_default();
            if options.get(answer_value)
                == options.get(
                    paper
                        .get::<Option<String>, _>("correct_answer")
                        .as_deref()
                        .unwrap_or(""),
                )
            {
                correct += 1;
            }
        }
        if let Err(error) =
            sqlx::query("UPDATE t_post_military_term_train_test_paper SET user_answer=? WHERE id=?")
                .bind(answer)
                .bind(paper_id)
                .execute(&mut *transaction)
                .await
        {
            return database_error(error, "save military term answer");
        }
    }
    // Java: accuracy = round(c/answered, 2) * 100，score = round(c/n, 3) * 100（先舍入再 ×100，已知 Java 口径）。
    let hundred = Decimal::from(100_i64);
    let accuracy = if answered == 0 {
        Decimal::ZERO
    } else {
        (Decimal::from(correct) / Decimal::from(answered))
            .round_dp_with_strategy(2, RoundingStrategy::MidpointAwayFromZero)
            * hundred
    };
    let score = if papers.is_empty() {
        Decimal::ZERO
    } else {
        (Decimal::from(correct) / Decimal::from(papers.len() as i64))
            .round_dp_with_strategy(3, RoundingStrategy::MidpointAwayFromZero)
            * hundred
    };
    if let Err(error) = sqlx::query(
        "UPDATE t_post_military_term_train SET status=2,end_time=NOW(),correct_number=?,error_number=?,accuracy=?,score=?,duration=TIMESTAMPDIFF(SECOND,start_time,NOW()) WHERE id=?",
    ).bind(correct).bind(answered - correct).bind(accuracy.to_string()).bind(score.to_string()).bind(id).execute(&mut *transaction).await {
        return database_error(error, "finish military term training");
    }
    let current = match sqlx::query(
        "SELECT id,user_id,types,name,status,total_number,correct_number,error_number,CAST(accuracy AS CHAR) AS accuracy,duration,CAST(score AS CHAR) AS score FROM t_post_military_term_train WHERE id=?",
    ).bind(id).fetch_one(&mut *transaction).await {
        Ok(row) => row,
        Err(error) => return database_error(error, "reload military term after finish"),
    };
    let type_ids = match parse_types(current.get::<Option<String>, _>("types")) {
        Ok(ids) => ids,
        Err(_) => {
            return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
        }
    };
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit military term finish");
    }
    let types = match type_names(pool, &type_ids).await {
        Ok(types) => types,
        Err(error) => return database_error(error, "load military term names after finish"),
    };
    envelope_json(
        StatusCode::OK,
        200,
        Some(train_json(&current, types, None)),
        "ok",
        "",
    )
}

pub(crate) async fn delete(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<DeleteQuery>,
) -> Response {
    let Some(id) = query.train_id.filter(|id| !id.is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "训练ID不能为空", "");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut transaction = match pool.begin().await {
        Ok(transaction) => transaction,
        Err(error) => return database_error(error, "start military term delete transaction"),
    };
    let owner = match sqlx::query(
        "SELECT user_id FROM t_post_military_term_train WHERE id=? LIMIT 1 FOR UPDATE",
    )
    .bind(&id)
    .fetch_optional(&mut *transaction)
    .await
    {
        Ok(Some(row)) => row.get::<Option<String>, _>("user_id"),
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到该训练", ""),
        Err(error) => return database_error(error, "load military term for delete"),
    };
    if owner.as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    // 试卷表只有主键索引：训练行已按主键锁住，这里普通读出试卷 id，再逐个按主键删除，
    // 避免 `WHERE train_id=?` 全表扫描锁住所有训练的试卷（会挡住别人的 add）。
    let paper_ids = match sqlx::query_scalar::<_, String>(
        "SELECT id FROM t_post_military_term_train_test_paper WHERE train_id=?",
    )
    .bind(&id)
    .fetch_all(&mut *transaction)
    .await
    {
        Ok(ids) => ids,
        Err(error) => return database_error(error, "load military term papers for delete"),
    };
    for paper_id in &paper_ids {
        if let Err(error) =
            sqlx::query("DELETE FROM t_post_military_term_train_test_paper WHERE id=?")
                .bind(paper_id)
                .execute(&mut *transaction)
                .await
        {
            return database_error(error, "delete military term papers");
        }
    }
    if let Err(error) =
        sqlx::query("DELETE FROM t_post_military_term_train WHERE id=? AND user_id=?")
            .bind(&id)
            .bind(&user.id)
            .execute(&mut *transaction)
            .await
    {
        return database_error(error, "delete military term training");
    }
    if let Err(error) = transaction.commit().await {
        return database_error(error, "commit military term delete");
    }
    envelope_json(StatusCode::OK, 200, Some(json!(true)), "ok", "")
}

#[cfg(test)]
mod tests {
    use super::*;

    type Case = (
        &'static str,
        &'static [&'static str],
        Option<(bool, &'static [&'static str])>,
    );

    /// 期望值由 Java `PostMilitaryTermTrainService.checkKeyword` 实跑得到（确定性规则；含边界与异常）。
    #[test]
    fn check_keyword_matches_java_outputs() {
        let cases: &[Case] = &[
            (
                "由喷火手和单兵火箭手4～6人混合编成",
                &["由喷火手和单兵火箭手4～6人混合编成"],
                Some((
                    true,
                    &[
                        "由喷火手和单兵火箭手4～6人混合编成",
                        "由喷火手和单兵火箭手5～7人混合编成",
                    ],
                )),
            ),
            (
                "装备2～3具",
                &["装备2～3具"],
                Some((true, &["装备2～3具", "装备3～4具"])),
            ),
            (
                "共10～20人",
                &["共10～20人"],
                Some((true, &["共10～20人", "共20～30人"])),
            ),
            (
                "数量9～99",
                &["数量9～99"],
                Some((true, &["数量9～99", "数量10～109"])),
            ),
            (
                "12345～6",
                &["12345～6"],
                Some((true, &["12345～6", "22345～7"])),
            ),
            (
                "a1～2～3",
                &["a1～2～3"],
                Some((true, &["a1～2～3", "a2～3～3"])),
            ),
            ("～1～2", &["～1～2"], None),
            (
                "无线电台",
                &["无线电台"],
                Some((true, &["无线电台", "有线电台"])),
            ),
            (
                "无线无线",
                &["无线无线"],
                Some((true, &["无线无线", "有线无线"])),
            ),
            (
                "出口通道",
                &["出口通道"],
                Some((true, &["出口通道", "入口通道"])),
            ),
            (
                "入口通道",
                &["入口通道"],
                Some((true, &["入口通道", "出口通道"])),
            ),
            (
                "出口入口",
                &["出口入口"],
                Some((true, &["出口入口", "入口入口"])),
            ),
            (
                "干线传输",
                &["干线传输"],
                Some((true, &["干线传输", "主线传输"])),
            ),
            (
                "每小时三小时",
                &["每小时三小时"],
                Some((true, &["每小时三小时", "每分钟三分钟"])),
            ),
            (
                "线状天线",
                &["线状天线"],
                Some((true, &["线状天线", "面状天线"])),
            ),
            (
                "面状天线",
                &["面状天线"],
                Some((true, &["面状天线", "线状天线"])),
            ),
            (
                "线状面状",
                &["线状面状"],
                Some((true, &["线状面状", "面状面状"])),
            ),
            ("接收机", &["接收机"], Some((true, &["接收机", "发射机"]))),
            ("发射机", &["发射机"], Some((true, &["发射机", "接收机"]))),
            (
                "接收发射",
                &["接收发射"],
                Some((true, &["接收发射", "发射发射"])),
            ),
            ("战术级", &["战术级"], Some((true, &["战术级", "战役级"]))),
            ("战役级", &["战役级"], Some((true, &["战役级", "战术级"]))),
            (
                "战术战役",
                &["战术战役"],
                Some((true, &["战术战役", "战役战役"])),
            ),
            (
                "甲、乙、丙、丁",
                &["甲、乙、丙、丁"],
                Some((true, &["甲、乙、丙、丁", "甲、丙、丁"])),
            ),
            (
                "甲、乙、丙",
                &["甲、乙、丙"],
                Some((false, &["甲、乙、丙"])),
            ),
            (
                "甲、乙、丙、丁、戊、",
                &["甲、乙、丙、丁、戊、"],
                Some((true, &["甲、乙、丙、丁、戊、", "甲、丙、丁、戊"])),
            ),
            ("、、、、", &["、、、、"], Some((false, &["、、、、"]))),
            (
                "甲、乙、丙、丁、",
                &["甲、乙、丙、丁、"],
                Some((true, &["甲、乙、丙、丁、", "甲、丙、丁"])),
            ),
            ("一二三", &["一二三"], Some((false, &["一二三"]))),
            (
                "无线\n电台",
                &["无线\n电台"],
                Some((true, &["无线\n电台", "有线\n电台"])),
            ),
            ("3～4\n", &["3～4\n"], Some((false, &["3～4\n"]))),
            (
                "6～7无线",
                &["6～7无线"],
                Some((true, &["6～7无线", "7～8无线"])),
            ),
            ("99999999999～1", &["99999999999～1"], None),
            ("1～99999999999", &["1～99999999999"], None),
            (
                "x2147483647～1",
                &["x2147483647～1"],
                Some((true, &["x2147483647～1", "x2247483647～1"])),
            ),
            (
                "2147483647～5",
                &["2147483647～5"],
                Some((true, &["2147483647～5", "-1147483649～6"])),
            ),
            (
                "a、b、c、d1无线",
                &["a、b、c、d1无线"],
                Some((true, &["a、b、c、d1无线", "a、b、c、d1有线"])),
            ),
            ("行李", &["行李"], Some((false, &["行李"]))),
            ("", &[""], Some((false, &[""]))),
            (
                "小时无线干线",
                &["小时无线干线"],
                Some((true, &["小时无线干线", "小时有线干线"])),
            ),
            (
                "出口2～3",
                &["出口2～3"],
                Some((true, &["出口2～3", "出口3～4"])),
            ),
            (
                "😀😀😀1～2",
                &["😀😀😀1～2"],
                Some((true, &["😀😀😀1～2", "😀😀😀2～3"])),
            ),
            (
                "，，，，，1～2",
                &["，，，，，1～2"],
                Some((true, &["，，，，，1～2", "，，，，，2～3"])),
            ),
            (
                "无线电台",
                &["无线电台", "有线电台"],
                Some((false, &["无线电台", "有线电台"])),
            ),
            (
                "无线电台出口",
                &["无线电台出口", "有线电台出口"],
                Some((true, &["无线电台出口", "有线电台出口", "无线电台入口"])),
            ),
            (
                "线状天线",
                &["线状天线", "面状天线"],
                Some((false, &["线状天线", "面状天线"])),
            ),
        ];
        let mut rng = rand::thread_rng();
        for (answer, start, expected) in cases {
            let mut options = start
                .iter()
                .map(|value| (*value).to_owned())
                .collect::<Vec<_>>();
            let actual = check_keyword(answer, &mut options, &mut rng);
            match expected {
                None => assert_eq!(actual, Err(PaperError::Fault), "answer {answer:?}"),
                Some((flag, expected_options)) => {
                    assert_eq!(actual, Ok(*flag), "answer {answer:?}");
                    assert_eq!(&options, expected_options, "answer {answer:?}");
                }
            }
        }
    }

    /// 最后一条规则（改一个数字）是随机的：只校验换掉了第一个数字、其余字符不变。
    #[test]
    fn check_keyword_digit_rule_changes_first_number_only() {
        let mut rng = rand::thread_rng();
        for _ in 0..50 {
            let mut options = vec!["约 5 ～ 8个".to_owned()];
            assert_eq!(
                check_keyword("约 5 ～ 8个", &mut options, &mut rng),
                Ok(true)
            );
            assert_eq!(options.len(), 2);
            let variant = &options[1];
            assert!(
                variant.starts_with("约 ") && variant.ends_with(" ～ 8个"),
                "{variant}"
            );
            assert_ne!(variant, "约 5 ～ 8个");
        }
    }

    #[test]
    fn make_paper_always_yields_four_distinct_options_with_correct_letter() {
        let category = ["线路", "无线电台", "出口通道", "主线", "2～3具"]
            .iter()
            .enumerate()
            .map(|(index, value)| CategoryTerm {
                key: format!("题{index}"),
                value: (*value).to_owned(),
            })
            .collect::<Vec<_>>();
        let mut rng = rand::thread_rng();
        for _ in 0..200 {
            let (title, options, correct) = make_paper("t", &category, &mut rng).unwrap();
            let options: Map<String, Value> = serde_json::from_str(&options).unwrap();
            assert_eq!(options.len(), 4, "{options:?}");
            let values = options.values().collect::<HashSet<_>>();
            assert_eq!(values.len(), 4);
            let index = category.iter().position(|term| term.key == title).unwrap();
            assert_eq!(
                options[&correct],
                Value::String(category[index].value.clone())
            );
        }
    }

    #[test]
    fn make_paper_rejects_fewer_than_four_distinct_values_with_type_prefix() {
        let category = ["甲", "甲", "乙", "", "丙"]
            .iter()
            .map(|value| CategoryTerm {
                key: "k".to_owned(),
                value: (*value).to_owned(),
            })
            .collect::<Vec<_>>();
        let mut rng = rand::thread_rng();
        assert_eq!(
            make_paper("T9", &category, &mut rng),
            Err(PaperError::Business(
                "类型 T9 有效题目不足4条，无法生成干扰项".to_owned()
            ))
        );
    }
}
