use super::{envelope_json, envelope_response, AppState, AuthUser};
use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rand::{seq::SliceRandom, Rng};
use rust_decimal::Decimal;
use serde_json::{json, Value};
use sqlx::{mysql::MySqlRow, MySqlPool, Row};

fn decimal_value(value: Option<Decimal>) -> Value {
    value
        .map(|value| {
            Value::Number(
                value
                    .to_string()
                    .parse()
                    .expect("rust_decimal always serializes as a JSON number"),
            )
        })
        .unwrap_or(Value::Null)
}

fn err(error: sqlx::Error, operation: &'static str) -> Response {
    tracing::warn!(error = %error, operation, "post telegraph read failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

const COLUMNS: &str = "id,title,is_cable,status,duration,total_number,error_number,accuracy,speed,create_user_id,attempt,protocol_version,full_score,content,message_type,rule_id,rule_content,deduct_info,score";

#[derive(serde::Deserialize)]
pub(crate) struct IdRequest {
    pub id: Option<String>,
}

#[derive(serde::Deserialize)]
pub(crate) struct PageQuery {
    #[serde(rename = "trainId")]
    pub train_id: Option<String>,
    #[serde(rename = "pageNumber")]
    pub page_number: Option<i32>,
}

fn json_text(text: Option<String>) -> Value {
    text.and_then(|value| serde_json::from_str(&value).ok())
        .unwrap_or(Value::Null)
}

fn row_json(row: &MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "title": row.get::<Option<String>, _>("title"),
        "isCable": row.get::<Option<i32>, _>("is_cable"),
        "status": row.get::<Option<i32>, _>("status"),
        "duration": row.get::<Option<String>, _>("duration"),
        "totalNumber": row.get::<Option<i32>, _>("total_number"),
        "errorNumber": row.get::<Option<i32>, _>("error_number"),
        "accuracy": row.get::<Option<f64>, _>("accuracy"),
        "speed": row.get::<Option<String>, _>("speed"),
        "attempt": row.get::<Option<i32>, _>("attempt"),
        "protocolVersion": row.get::<Option<i32>, _>("protocol_version"),
        "score": decimal_value(row.get::<Option<Decimal>, _>("score")),
        "content": json_text(row.get::<Option<String>, _>("content")),
        "messageType": row.get::<Option<i32>, _>("message_type"),
        "ruleContent": row.get::<Option<String>, _>("rule_content"),
        "deductInfo": row.get::<Option<String>, _>("deduct_info"),
        "serverElapsedMs": 0,
        "trainType": null,
        "pageNumber": null,
        "resolverMessage": null,
        "existPage": null,
        "moreGroupLine": null,
        "pageAnalyzeVOS": null,
        "savedPages": null,
        "savedPageNumbers": null,
    })
}

fn message_json(row: &MySqlRow) -> Value {
    json!({
        "id": row.get::<String, _>("id"),
        "trainId": row.get::<Option<String>, _>("train_id"),
        "pageNumber": row.get::<Option<i32>, _>("page_number"),
        "key": row.get::<Option<String>, _>("key"),
        "value": row.get::<Option<String>, _>("value"),
        "time": row.get::<Option<String>, _>("time"),
        "sort": row.get::<Option<i32>, _>("sort"),
    })
}

fn raw_values(raw: &MySqlRow) -> Result<Value, ()> {
    serde_json::from_str(&raw.get::<String, _>("value")).map_err(|_| ())
}
fn random_groups(message_type: i32, count: usize) -> Vec<String> {
    let alphabet: &[u8] = match message_type {
        0 => b"0123456789",
        1 => b"ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        _ => b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
    };
    let mut rng = rand::thread_rng();
    let mut result = Vec::with_capacity(count);
    for _ in 0..count {
        let group = loop {
            let candidate = if message_type == 2 {
                let target_digits = match rng.gen_range(0..100) {
                    0..50 => 2,
                    50..75 => 3,
                    _ => 1,
                };
                let mut positions = [0usize, 1, 2, 3];
                positions.shuffle(&mut rng);
                let digit_positions = &positions[..target_digits];
                let mut used = Vec::with_capacity(4);
                for position in 0..4 {
                    let source = if digit_positions.contains(&position) {
                        b"0123456789".as_slice()
                    } else {
                        b"ABCDEFGHIJKLMNOPQRSTUVWXYZ".as_slice()
                    };
                    let value = loop {
                        let value = *source.choose(&mut rng).expect("non-empty report alphabet");
                        if !used.contains(&value) {
                            break value;
                        }
                    };
                    used.push(value);
                }
                String::from_utf8(used).expect("report alphabet is ASCII")
            } else {
                alphabet
                    .choose_multiple(&mut rng, 4)
                    .map(|value| char::from(*value))
                    .collect()
            };
            let duplicate = result
                .iter()
                .rev()
                .take(10)
                .any(|value| value == &candidate);
            if !duplicate {
                break candidate;
            }
        };
        result.push(group);
    }
    result
}

async fn train_query(pool: &MySqlPool, id: &str) -> Result<Option<MySqlRow>, sqlx::Error> {
    let sql = format!(
        "SELECT {COLUMNS}, CAST(COALESCE(GREATEST(0,TIMESTAMPDIFF(MICROSECOND,begin_time,NOW(6)) DIV 1000),0) AS SIGNED) AS elapsed_ms FROM t_post_telegraph_key_pat_train WHERE id=? LIMIT 1"
    );
    sqlx::query(&sql).bind(id).fetch_optional(pool).await
}

async fn saved_pages(
    pool: &MySqlPool,
    train_id: &str,
    row: &MySqlRow,
) -> Result<(Value, Value), sqlx::Error> {
    let raw_rows = sqlx::query("SELECT id,train_id,page_number,attempt,value,capture_intervals FROM t_post_telegraph_key_pat_train_raw_page WHERE train_id=? ORDER BY page_number")
        .bind(train_id).fetch_all(pool).await?;
    let mut pages = Vec::with_capacity(raw_rows.len());
    let mut numbers = Vec::with_capacity(raw_rows.len());
    for raw in &raw_rows {
        let value =
            raw_values(raw).map_err(|_| sqlx::Error::Protocol("invalid raw page JSON".into()))?;
        pages.push(json!({
            "protocolVersion": row.get::<Option<i32>, _>("protocol_version"),
            "attempt": row.get::<Option<i32>, _>("attempt"),
            "serverElapsedMs": row.get::<Option<i64>, _>("elapsed_ms").unwrap_or(0),
            "submitted": true,
            "savedCaptureIntervals": json_text(Some(raw.get::<String, _>("capture_intervals"))),
            "messageVO": value,
            "resolverMessage": [], "moreGroup": [], "moreLine": [],
        }));
        numbers.push(raw.get::<i32, _>("page_number"));
    }
    Ok((Value::Array(pages), json!(numbers)))
}

pub(crate) async fn owned_train_json(
    pool: &MySqlPool,
    train_id: &str,
    user_id: &str,
) -> Result<Result<Value, i32>, sqlx::Error> {
    let Some(row) = train_query(pool, train_id).await? else {
        return Ok(Err(202));
    };
    if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user_id) {
        return Ok(Err(207));
    }
    let (pages, numbers) = saved_pages(pool, train_id, &row).await?;
    let mut item = row_json(&row);
    item["serverElapsedMs"] = json!(row.get::<Option<i64>, _>("elapsed_ms").unwrap_or(0));
    item["savedPages"] = pages;
    item["savedPageNumbers"] = numbers;
    Ok(Ok(item))
}

pub(crate) async fn list(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
) -> Response {
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let sql = format!("SELECT {COLUMNS}, CAST(COALESCE(GREATEST(0,TIMESTAMPDIFF(MICROSECOND,begin_time,NOW(6)) DIV 1000),0) AS SIGNED) AS elapsed_ms FROM t_post_telegraph_key_pat_train WHERE create_user_id=? ORDER BY create_time DESC");
    let rows = match sqlx::query(&sql).bind(&user.id).fetch_all(pool).await {
        Ok(rows) => rows,
        Err(error) => return err(error, "list-trains"),
    };
    let items = rows.iter().map(row_json).collect::<Vec<_>>();
    envelope_json(StatusCode::OK, 200, Some(Value::Array(items)), "ok", "")
}

pub(crate) async fn detail(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Json(body): Json<IdRequest>,
) -> Response {
    let Some(id) = body.id.filter(|value| !value.trim().is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "请求参数错误");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let row = match train_query(pool, &id).await {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到训练", "未查询到训练"),
        Err(error) => return err(error, "detail-train"),
    };
    if row.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let (pages, numbers) = match saved_pages(pool, &id, &row).await {
        Ok(value) => value,
        Err(error) => return err(error, "detail-saved-pages"),
    };
    let mut item = row_json(&row);
    item["serverElapsedMs"] = json!(row.get::<Option<i64>, _>("elapsed_ms").unwrap_or(0));
    item["savedPages"] = pages;
    item["savedPageNumbers"] = numbers;
    let generated = match sqlx::query("SELECT DISTINCT page_number FROM t_post_telegraph_key_pat_train_page WHERE train_id=? ORDER BY page_number").bind(&id).fetch_all(pool).await {
        Ok(rows) => rows.into_iter().filter_map(|value| value.try_get::<Option<i32>, _>("page_number").ok().flatten()).map(|page| json!(page)).collect::<Vec<_>>(),
        Err(error) => return err(error, "detail-generated-pages"),
    };
    item["existPage"] = json!(generated);
    let preview_sources = match sqlx::query("SELECT id,train_id,page_number,`key`,value,time,sort FROM t_post_telegraph_key_pat_train_page WHERE train_id=? AND (page_number=1 OR page_number=2) ORDER BY page_number,sort")
        .bind(&id).fetch_all(pool).await {
        Ok(rows) => rows,
        Err(error) => return err(error, "detail-source-pages"),
    };
    let preview_values = match sqlx::query("SELECT id,train_id,page_number,`key`,value,time,sort FROM t_post_telegraph_key_pat_train_page_value WHERE train_id=? AND (page_number=1 OR page_number=2) ORDER BY page_number,sort")
        .bind(&id).fetch_all(pool).await {
        Ok(rows) => rows,
        Err(error) => return err(error, "detail-value-pages"),
    };
    let mut preview = if row.get::<Option<i32>, _>("status") == Some(2) {
        preview_values.iter().map(message_json).collect::<Vec<_>>()
    } else {
        preview_sources.iter().map(message_json).collect::<Vec<_>>()
    };
    if row.get::<Option<i32>, _>("status") == Some(2)
        && !preview.is_empty()
        && preview.len() < preview_sources.len()
    {
        let start = preview_sources.len() - preview.len();
        preview.extend(preview_sources[start..].iter().map(message_json));
    }
    item["content"] = Value::Array(preview);
    item["pageAnalyzeVOS"] = json!([]);
    item["moreGroupLine"] = json!([]);
    envelope_json(StatusCode::OK, 200, Some(item), "ok", "")
}

pub(crate) async fn get_page(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    Query(query): Query<PageQuery>,
) -> Response {
    let Some(train_id) = query.train_id.filter(|value| !value.trim().is_empty()) else {
        return envelope_response(StatusCode::OK, 202, "请求参数错误", "请求参数错误");
    };
    let Some(page_number) = query.page_number else {
        return envelope_response(StatusCode::OK, 202, "页码不能为空", "页码不能为空");
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return err(error, "get-page-begin"),
    };
    let sql = format!(
        "SELECT {COLUMNS}, CAST(COALESCE(GREATEST(0,TIMESTAMPDIFF(MICROSECOND,begin_time,NOW(6)) DIV 1000),0) AS SIGNED) AS elapsed_ms FROM t_post_telegraph_key_pat_train WHERE id=? LIMIT 1 FOR UPDATE"
    );
    let train = match sqlx::query(&sql)
        .bind(&train_id)
        .fetch_optional(&mut *tx)
        .await
    {
        Ok(Some(row)) => row,
        Ok(None) => return envelope_response(StatusCode::OK, 202, "未查询到训练", "未查询到训练"),
        Err(error) => return err(error, "get-page-train"),
    };
    if train.get::<Option<String>, _>("create_user_id").as_deref() != Some(user.id.as_str()) {
        return envelope_response(StatusCode::OK, 207, "无权限", "");
    }
    let protocol = train.get::<Option<i32>, _>("protocol_version").unwrap_or(0);
    let status = train.get::<Option<i32>, _>("status").unwrap_or(0);
    if protocol != 1 && status != 2 {
        return envelope_response(
            StatusCode::OK,
            208,
            "旧训练缺少采集时间轴，请终止旧训练并新建训练",
            "旧训练缺少采集时间轴，请终止旧训练并新建训练",
        );
    }
    let groups = if train.get::<Option<i32>, _>("is_cable").unwrap_or(0) == 1 {
        match sqlx::query(
            "SELECT COUNT(*) AS total FROM t_post_telegraph_key_pat_train_page WHERE train_id=?",
        )
        .bind(&train_id)
        .fetch_one(&mut *tx)
        .await
        {
            Ok(row) => row.get::<i64, _>("total"),
            Err(error) => return err(error, "get-page-count"),
        }
    } else {
        i64::from(train.get::<Option<i32>, _>("total_number").unwrap_or(0))
    };
    let page_count = (groups + 99) / 100;
    if page_number < 1 || i64::from(page_number) > page_count {
        return envelope_response(StatusCode::OK, 202, "页码超出训练范围", "页码超出训练范围");
    }
    let user_rows = match sqlx::query("SELECT id,train_id,page_number,sort,`key`,value,time FROM t_post_telegraph_key_pat_train_page_value WHERE train_id=? AND page_number=? ORDER BY sort")
        .bind(&train_id).bind(page_number).fetch_all(&mut *tx).await {
        Ok(rows) => rows,
        Err(error) => return err(error, "get-page-values"),
    };
    let mut generated_rows = match sqlx::query("SELECT id,train_id,page_number,sort,`key`,value,time FROM t_post_telegraph_key_pat_train_page WHERE train_id=? AND page_number=? ORDER BY sort")
        .bind(&train_id).bind(page_number).fetch_all(&mut *tx).await {
        Ok(rows) => rows,
        Err(error) => return err(error, "get-page-sources"),
    };
    if generated_rows.is_empty() {
        if status == 2 {
            return envelope_response(
                StatusCode::OK,
                208,
                "已完成训练缺少该页报底",
                "已完成训练缺少该页报底",
            );
        }
        if train.get::<Option<i32>, _>("is_cable").unwrap_or(0) == 0 {
            let total = train.get::<Option<i32>, _>("total_number").unwrap_or(0);
            let remaining = total.saturating_sub((page_number - 1).saturating_mul(100));
            let count = remaining.min(100) as usize;
            let message_type = train.get::<Option<i32>, _>("message_type").unwrap_or(0);
            for (sort, group) in random_groups(message_type, count).into_iter().enumerate() {
                let key = serde_json::to_string(
                    &group
                        .chars()
                        .map(|character| character.to_string())
                        .collect::<Vec<_>>(),
                )
                .unwrap_or_else(|_| "[]".to_owned());
                if let Err(error) = sqlx::query("INSERT INTO t_post_telegraph_key_pat_train_page (id,train_id,page_number,sort,`key`,value,time) VALUES (?,?,?,?,?,?,?)")
                    .bind(uuid::Uuid::new_v4().to_string()).bind(&train_id).bind(page_number)
                    .bind(sort as i32).bind(key).bind("[]").bind("[]")
                    .execute(&mut *tx).await {
                    return err(error, "get-page-generate");
                }
            }
            generated_rows = match sqlx::query("SELECT id,train_id,page_number,sort,`key`,value,time FROM t_post_telegraph_key_pat_train_page WHERE train_id=? AND page_number=? ORDER BY sort")
                .bind(&train_id).bind(page_number).fetch_all(&mut *tx).await {
                Ok(rows) => rows,
                Err(error) => return err(error, "get-page-generated-refresh"),
            };
        }
    }
    let raw = match sqlx::query("SELECT attempt,value,capture_intervals FROM t_post_telegraph_key_pat_train_raw_page WHERE train_id=? AND page_number=? LIMIT 1")
        .bind(&train_id).bind(page_number).fetch_optional(&mut *tx).await {
        Ok(row) => row,
        Err(error) => return err(error, "get-page-raw"),
    };
    if let Some(raw_row) = raw.as_ref() {
        if raw_row.get::<Option<i32>, _>("attempt") != train.get::<Option<i32>, _>("attempt") {
            return envelope_response(
                StatusCode::OK,
                208,
                "训练协议或轮次已失效，请重新读取训练",
                "训练协议或轮次已失效，请重新读取训练",
            );
        }
    }
    let source_rows = if user_rows.is_empty() {
        &generated_rows
    } else {
        &user_rows
    };
    let mut messages: Value = Value::Array(source_rows.iter().map(message_json).collect());
    if let Some(raw_row) = raw.as_ref() {
        if status != 2 {
            messages = match raw_values(raw_row) {
                Ok(value) => value,
                Err(_) => {
                    tracing::warn!(train_id = %train_id, page_number, operation = "get-page-raw-json", "stored raw page JSON is invalid");
                    return envelope_response(
                        StatusCode::INTERNAL_SERVER_ERROR,
                        500,
                        "服务器错误",
                        "",
                    );
                }
            };
        }
    }
    let resolver = Value::Array(
        generated_rows
            .iter()
            .filter_map(|row| row.get::<Option<String>, _>("value"))
            .map(|value| json!(value))
            .collect(),
    );
    let more = match sqlx::query("SELECT more_group,more_line FROM t_post_telegraph_key_pat_train_more WHERE train_id=? AND page_number=? LIMIT 1")
        .bind(&train_id).bind(page_number).fetch_optional(&mut *tx).await {
        Ok(Some(row)) => (json_text(row.get::<Option<String>, _>("more_group")), json_text(row.get::<Option<String>, _>("more_line"))),
        Ok(None) => (json!([]), json!([])),
        Err(error) => return err(error, "get-page-more"),
    };
    let response = json!({
        "protocolVersion": protocol,
        "attempt": train.get::<Option<i32>, _>("attempt").unwrap_or(0),
        "serverElapsedMs": train.get::<Option<i64>, _>("elapsed_ms").unwrap_or(0),
        "submitted": raw.is_some(),
        "savedCaptureIntervals": raw.as_ref().map(|row| json_text(row.get::<Option<String>, _>("capture_intervals"))).unwrap_or_else(|| json!([])),
        "messageVO": messages,
        "resolverMessage": resolver,
        "moreGroup": more.0,
        "moreLine": more.1,
    });
    if let Err(error) = tx.commit().await {
        return err(error, "get-page-commit");
    }
    envelope_json(StatusCode::OK, 200, Some(response), "ok", "")
}
