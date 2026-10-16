use super::group_net::{
    deserialize_id, detail_json, failure, finish, invalid, require_owner, score_json, Error,
    DETAIL_SELECT,
};
use crate::platform::{auth::AuthUser, envelope::envelope_response, state::AppState};
use axum::{
    extract::{rejection::JsonRejection, Extension, State},
    http::StatusCode,
    response::Response,
    Json,
};
use rust_decimal::Decimal;
use serde::Deserialize;
use serde_json::{json, Map, Value};
use sqlx::{MySqlConnection, Row};
use std::collections::{HashMap, HashSet};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct AddRequest {
    #[serde(default, deserialize_with = "deserialize_id")]
    device_type: Option<i32>,
    #[serde(default, deserialize_with = "deserialize_id")]
    device_id: Option<i32>,
    topic: Option<String>,
}

#[derive(Deserialize)]
pub(crate) struct AnswerRequest {
    #[serde(default, deserialize_with = "deserialize_id")]
    id: Option<i32>,
    answer: Option<String>,
    #[serde(flatten)]
    unsupported: Map<String, Value>,
}

fn parse(content: Option<&str>, name: &str) -> Result<Value, Error> {
    let content = content
        .filter(|value| !value.trim().is_empty())
        .ok_or_else(|| invalid(format!("{name}不能为空")))?;
    serde_json::from_str(content).map_err(|_| invalid(format!("{name}格式无效")))
}

fn object<'a>(value: &'a Value, name: &str) -> Result<&'a Map<String, Value>, Error> {
    value
        .as_object()
        .ok_or_else(|| invalid(format!("{name}必须是对象")))
}

fn array<'a>(value: &'a Value, name: &str) -> Result<&'a [Value], Error> {
    value
        .as_array()
        .map(Vec::as_slice)
        .ok_or_else(|| invalid(format!("{name}必须是数组")))
}

fn optional_array<'a>(value: &'a Map<String, Value>, name: &str) -> Result<&'a [Value], Error> {
    match value.get(name) {
        None | Some(Value::Null) => Ok(&[]),
        Some(value) => array(value, name),
    }
}

// Compare arbitrary-length decimal answers without a floating-point or Decimal precision loss.
#[derive(PartialEq, Eq)]
struct DecimalText<'a> {
    negative: bool,
    whole: &'a str,
    fraction: &'a str,
}

fn decimal<'a>(value: Option<&'a Value>, name: &str) -> Result<DecimalText<'a>, Error> {
    let text = match value {
        Some(Value::String(text)) => text.as_str(),
        Some(Value::Number(number)) => number.as_str(),
        _ => return Err(invalid(format!("{name}必须是完整十进制数"))),
    };
    let unsigned = text.strip_prefix(['+', '-']).unwrap_or(text);
    let (whole, fraction) = match unsigned.split_once('.') {
        Some((whole, fraction)) if !fraction.is_empty() => (whole, fraction),
        None => (unsigned, ""),
        _ => return Err(invalid(format!("{name}必须是完整十进制数"))),
    };
    if whole.is_empty()
        || !whole.bytes().all(|c| c.is_ascii_digit())
        || !fraction.bytes().all(|c| c.is_ascii_digit())
    {
        return Err(invalid(format!("{name}必须是完整十进制数")));
    }
    let whole = whole.trim_start_matches('0');
    let fraction = fraction.trim_end_matches('0');
    Ok(DecimalText {
        negative: text.starts_with('-') && (!whole.is_empty() || !fraction.is_empty()),
        whole,
        fraction,
    })
}

fn integer(value: Option<&Value>, name: &str, min: i32, max: i32) -> Result<i32, Error> {
    let number = decimal(value, name)?;
    if number.negative || !number.fraction.is_empty() {
        return Err(invalid(format!("{name}超出范围")));
    }
    let value = if number.whole.is_empty() {
        0
    } else {
        number
            .whole
            .parse::<i32>()
            .map_err(|_| invalid(format!("{name}超出范围")))?
    };
    if value < min || value > max {
        return Err(invalid(format!("{name}超出范围")));
    }
    Ok(value)
}

fn bounded_decimal(number: &DecimalText<'_>, max: u32, scale: usize) -> Option<Decimal> {
    if number.negative || number.fraction.len() > scale {
        return None;
    }
    let whole = if number.whole.is_empty() {
        0
    } else {
        number.whole.parse::<u32>().ok()?
    };
    if whole > max || (whole == max && !number.fraction.is_empty()) {
        return None;
    }
    let fraction = if number.fraction.is_empty() {
        0
    } else {
        number.fraction.parse::<i64>().ok()?
    };
    Some(Decimal::from(whole) + Decimal::new(fraction, number.fraction.len() as u32))
}

fn rules(values: &Value) -> Result<[Decimal; 3], Error> {
    let mut weights = [None; 3];
    for value in array(values, "评分规则")? {
        let rule = object(value, "评分规则项")?;
        let name = rule
            .get("paramName")
            .and_then(Value::as_str)
            .ok_or_else(|| invalid("规则参数名称必须是字符串"))?;
        let weight = decimal(rule.get("weight"), "规则权重")?;
        let index = match name {
            "网路地址" => 0,
            "单台地址" => 1,
            "信道" => 2,
            _ => {
                return Err(invalid(
                    "评分规则参数重复、未知或权重超出0至100（最多两位小数）",
                ))
            }
        };
        let weight = bounded_decimal(&weight, 100, 2)
            .ok_or_else(|| invalid("评分规则参数重复、未知或权重超出0至100（最多两位小数）"))?;
        if weights[index].replace(weight).is_some() {
            return Err(invalid(
                "评分规则参数重复、未知或权重超出0至100（最多两位小数）",
            ));
        }
    }
    match weights {
        [Some(network), Some(address), Some(channel)] => Ok([network, address, channel]),
        _ => Err(invalid("必须完整配置网路地址、单台地址和信道规则")),
    }
}

fn first_receive_column(row: i32) -> i32 {
    match row {
        4 => 8,
        13 => 1,
        _ => 7,
    }
}

fn template_parameter(row: i32, col: i32) -> Result<&'static str, Error> {
    if row == 4 && col == 2 {
        return Ok("网路地址");
    }
    if (4..=11).contains(&row) {
        if col == 1 {
            return Ok("序号");
        }
        if col == if row == 4 { 3 } else { 2 } {
            return Ok("单台地址");
        }
    }
    if (4..=13).contains(&row) {
        let offset = col - first_receive_column(row);
        if offset >= 0 && offset / 3 < 7 && offset % 3 < 2 {
            return Ok("信道");
        }
    }
    Err(invalid("题目坐标不属于J210-742可编辑参数"))
}

enum Target {
    Network,
    Address(usize),
    Channel { index: i32, receive: bool },
}

struct Cell<'a> {
    xy: &'a Value,
    expected: DecimalText<'a>,
    target: Target,
}

struct Topic<'a> {
    cells: Vec<Cell<'a>>,
    serials: [usize; 14],
}

fn validate_topic(value: &Value) -> Result<Topic<'_>, Error> {
    let values = array(value, "训练题目")?;
    let mut coordinates = HashSet::with_capacity(157);
    let mut addresses = HashSet::with_capacity(8);
    let mut topic = Topic {
        cells: Vec::with_capacity(149),
        serials: [0; 14],
    };
    for value in values {
        let item = object(value, "题目项")?;
        let xy = item.get("xy").ok_or_else(|| invalid("题目坐标无效"))?;
        let coordinates_value = xy
            .as_array()
            .filter(|xy| xy.len() == 2)
            .ok_or_else(|| invalid("题目坐标无效"))?;
        let row = integer(coordinates_value.first(), "题目行", 0, 100)?;
        let col = integer(coordinates_value.get(1), "题目列", 0, 100)?;
        if !coordinates.insert((row, col)) {
            return Err(invalid("题目坐标重复"));
        }
        let cell = item
            .get("value")
            .and_then(Value::as_object)
            .ok_or_else(|| invalid("题目单元格必须是对象"))?;
        let name = cell
            .get("isParameter")
            .and_then(Value::as_str)
            .ok_or_else(|| invalid("题目参数类型必须是字符串"))?;
        if template_parameter(row, col)? != name {
            return Err(invalid("题目坐标与J210-742参数类型不匹配"));
        }
        if name != "信道" && (cell.contains_key("xdValues") || cell.contains_key("xdValuef")) {
            return Err(invalid("非信道题目不能配置收发编号"));
        }
        let target = match name {
            "序号" => {
                topic.serials[row as usize] = integer(cell.get("value"), "序号", 0, 19)? as usize;
                continue;
            }
            "网路地址" => {
                integer(cell.get("value"), "网路地址", 300, 319)?;
                Target::Network
            }
            "单台地址" => {
                let address = integer(cell.get("value"), "单台地址", 0, 299)?;
                if (20..100).contains(&address) {
                    return Err(invalid("单台地址不在0至19或100至299"));
                }
                if !addresses.insert(address) {
                    return Err(invalid("单台地址重复"));
                }
                Target::Address(row as usize)
            }
            "信道" => {
                let expected = decimal(cell.get("value"), "题目值")?;
                bounded_decimal(&expected, 29, 5)
                    .ok_or_else(|| invalid("信道应在0至29且最多五位小数"))?;
                let offset = col - first_receive_column(row);
                let receive = offset % 3 == 0;
                let (key, other) = if receive {
                    ("xdValues", "xdValuef")
                } else {
                    ("xdValuef", "xdValues")
                };
                if !cell.contains_key(key) || cell.contains_key(other) {
                    return Err(invalid("信道收发映射无效"));
                }
                let index = integer(cell.get(key), "信道编号", 1, 70)?;
                if index != (offset / 3) * 10 + row - 3 {
                    return Err(invalid("信道编号与J210-742模板位置不匹配"));
                }
                Target::Channel { index, receive }
            }
            _ => return Err(invalid("不支持的题目参数")),
        };
        topic.cells.push(Cell {
            xy,
            expected: decimal(cell.get("value"), "题目值")?,
            target,
        });
    }
    if coordinates.len() != 157 {
        return Err(invalid("J210-742题目必须包含完整的157个参数单元格"));
    }
    Ok(topic)
}

fn freeze(
    device_id: i32,
    rule_content: Option<&str>,
    topic_content: Option<&str>,
) -> Result<String, Error> {
    let values = parse(rule_content, "评分规则")?;
    rules(&values)?;
    let topic = parse(topic_content, "训练题目")?;
    validate_topic(&topic)?;
    Ok(serde_json::to_string(
        &json!({"version": 1, "deviceId": device_id, "rules": values}),
    )?)
}

fn address_answer<'a>(
    answers: &'a [Value],
    index: usize,
    key: &str,
) -> Result<Option<&'a Value>, Error> {
    match answers.get(index) {
        None | Some(Value::Null) => Ok(None),
        Some(answer) => Ok(object(answer, "地址答案")?.get(key)),
    }
}

fn calculate(
    device_id: Option<i32>,
    topic_content: Option<&str>,
    snapshot_content: &str,
    answer_content: Option<&str>,
) -> Result<(Decimal, String), Error> {
    let snapshot = parse(Some(snapshot_content), "冻结评分规则")?;
    let snapshot = object(&snapshot, "冻结评分规则")?;
    if device_id.is_none()
        || integer(snapshot.get("version"), "冻结规则版本", 1, i32::MAX)? != 1
        || Some(integer(
            snapshot.get("deviceId"),
            "冻结规则设备",
            1,
            i32::MAX,
        )?) != device_id
    {
        return Err(invalid("冻结评分规则版本或设备不匹配，请重新创建训练"));
    }
    let frozen_rules = snapshot
        .get("rules")
        .filter(|value| value.is_array())
        .ok_or_else(|| invalid("冻结评分规则格式无效"))?;
    let weights = rules(frozen_rules)?;
    let weights_json = [
        score_json(Some(weights[0].to_string()))?,
        score_json(Some(weights[1].to_string()))?,
        score_json(Some(weights[2].to_string()))?,
    ];
    let topic = parse(topic_content, "训练题目")?;
    let topic = validate_topic(&topic)?;
    let answer = parse(answer_content, "答案")?;
    let answer = object(&answer, "答案")?;
    let channel_answers = optional_array(answer, "chananel1")?;
    let mut channels = HashMap::with_capacity(channel_answers.len());
    for item in channel_answers {
        let channel = object(item, "信道答案")?;
        let index = integer(channel.get("indexs"), "答案信道编号", 0, 999)?;
        if channels.insert(index, channel).is_some() {
            return Err(invalid("答案信道编号重复"));
        }
    }
    let serial_answers = optional_array(answer, "serialNumbers")?;
    let mut total = Decimal::ZERO;
    let mut details = Vec::with_capacity(topic.cells.len());
    let unanswered_value = Value::String(String::new());
    let zero = json!(0);
    for cell in topic.cells {
        let (weight_index, actual) = match cell.target {
            Target::Channel { index, receive } => (
                2,
                channels.get(&index).and_then(|channel| {
                    channel.get(if receive {
                        "receptionChananel"
                    } else {
                        "sendChananel"
                    })
                }),
            ),
            Target::Network => (0, address_answer(serial_answers, 0, "networkdress")?),
            Target::Address(row) => (
                1,
                address_answer(serial_answers, topic.serials[row], "dressname")?,
            ),
        };
        let actual = actual.filter(|value| {
            !value.is_null() && !value.as_str().is_some_and(|text| text.trim().is_empty())
        });
        let correct = match actual {
            Some(actual) => cell.expected == decimal(Some(actual), "答案值")?,
            None => false,
        };
        if correct {
            total += weights[weight_index];
        }
        details.push(json!({
            "xy": cell.xy,
            "actual": actual.unwrap_or(&unanswered_value),
            "correct": correct,
            "weight": weights_json[weight_index],
            "score": if correct { &weights_json[weight_index] } else { &zero },
        }));
    }
    Ok((total, serde_json::to_string(&details)?))
}

// Retry equality permits numeric exponents and scale differences, but not changed low digits.
fn number_signature(text: &str) -> Result<(bool, &str, i64), Error> {
    let (mantissa, exponent) = match text.find(['e', 'E']) {
        Some(index) => (
            &text[..index],
            i64::from(
                text[index + 1..]
                    .parse::<i32>()
                    .map_err(|_| invalid("答案数字指数超出范围"))?,
            ),
        ),
        None => (text, 0),
    };
    let unsigned = mantissa.strip_prefix('-').unwrap_or(mantissa);
    let Some(first) = unsigned.bytes().position(|c| matches!(c, b'1'..=b'9')) else {
        return Ok((false, "", 0));
    };
    let last = unsigned
        .bytes()
        .rposition(|c| matches!(c, b'1'..=b'9'))
        .expect("nonzero digit exists");
    let fraction = unsigned
        .find('.')
        .map_or(0, |index| unsigned.len() - index - 1);
    let trailing = unsigned[last + 1..].bytes().filter(|c| *c != b'.').count();
    Ok((
        mantissa.starts_with('-'),
        &unsigned[first..=last],
        exponent - fraction as i64 + trailing as i64,
    ))
}

fn same_json(left: &Value, right: &Value) -> Result<bool, Error> {
    match (left, right) {
        (Value::Object(left), Value::Object(right)) => {
            if left.len() != right.len() {
                return Ok(false);
            }
            for (key, value) in left {
                let Some(other) = right.get(key) else {
                    return Ok(false);
                };
                if !same_json(value, other)? {
                    return Ok(false);
                }
            }
            Ok(true)
        }
        (Value::Array(left), Value::Array(right)) => {
            if left.len() != right.len() {
                return Ok(false);
            }
            for (value, other) in left.iter().zip(right) {
                if !same_json(value, other)? {
                    return Ok(false);
                }
            }
            Ok(true)
        }
        (Value::Number(left), Value::Number(right)) => {
            let (left_negative, left_digits, left_exponent) = number_signature(left.as_str())?;
            let (right_negative, right_digits, right_exponent) = number_signature(right.as_str())?;
            Ok(left_negative == right_negative
                && left_exponent == right_exponent
                && left_digits
                    .bytes()
                    .filter(|c| *c != b'.')
                    .eq(right_digits.bytes().filter(|c| *c != b'.')))
        }
        _ => Ok(left == right),
    }
}

fn same_answer(saved: Option<&str>, submitted: Option<&str>) -> Result<bool, Error> {
    let saved = parse(saved, "已提交答案")?;
    let submitted = parse(submitted, "答案")?;
    object(&saved, "已提交答案")?;
    object(&submitted, "答案")?;
    same_json(&saved, &submitted)
}

async fn save_train(
    connection: &mut MySqlConnection,
    user: &AuthUser,
    body: AddRequest,
) -> Result<Value, Error> {
    let device_id = body.device_id.ok_or_else(|| invalid("设备不能为空"))?;
    let device = sqlx::query("SELECT device_type_id,device_number FROM t_device WHERE id=?")
        .bind(device_id)
        .fetch_optional(&mut *connection)
        .await?
        .ok_or_else(|| invalid("未查询到设备"))?;
    let device_type = device.try_get::<Option<i32>, _>("device_type_id")?;
    if device_type != body.device_type {
        return Err(invalid("设备与设备分类不匹配"));
    }
    if device
        .try_get::<Option<String>, _>("device_number")?
        .as_deref()
        != Some("J210-742")
    {
        return Err(invalid("该设备尚无综合组网题目与评分映射"));
    }
    let rules = sqlx::query("SELECT rule_content FROM device_scoring_rule WHERE device_id=?")
        .bind(device_id)
        .fetch_all(&mut *connection)
        .await?;
    if rules.len() != 1 {
        return Err(invalid("该设备必须配置唯一的有效评分规则"));
    }
    let rule_content = rules[0].try_get::<Option<String>, _>("rule_content")?;
    let snapshot = freeze(device_id, rule_content.as_deref(), body.topic.as_deref())?;
    let saved = sqlx::query("INSERT INTO group_net_train (device_type,device_id,topic,scoring_rule_content,create_user,create_time) VALUES (?,?,?,?,?,NOW(6))")
        .bind(device_type).bind(device_id).bind(body.topic).bind(snapshot).bind(&user.id)
        .execute(&mut *connection).await?;
    let row = sqlx::query(DETAIL_SELECT)
        .bind(saved.last_insert_id())
        .fetch_one(&mut *connection)
        .await?;
    detail_json(&row)
}

pub(crate) async fn save(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    body: Result<Json<Option<AddRequest>>, JsonRejection>,
) -> Response {
    let body = match body {
        Ok(Json(Some(body))) => body,
        Ok(Json(None)) => return failure(invalid("设备不能为空")),
        Err(_) => return failure(invalid("请求参数错误")),
    };
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return failure(error.into()),
    };
    let result = save_train(&mut tx, &user, body).await;
    finish(tx, result).await
}

async fn submit_answer(
    connection: &mut MySqlConnection,
    user: &AuthUser,
    id: i32,
    answer: Option<String>,
) -> Result<Value, Error> {
    let row = sqlx::query("SELECT id,device_type,device_id,topic,answer,scoring_rule_content,CAST(score AS CHAR) AS score,content,create_user FROM group_net_train WHERE id=? FOR UPDATE")
        .bind(id).fetch_optional(&mut *connection).await?
        .ok_or_else(|| invalid("未查询到训练"))?;
    require_owner(&row, user)?;
    if row.try_get::<Option<String>, _>("score")?.is_some() {
        let saved = row.try_get::<Option<String>, _>("answer")?;
        if !same_answer(saved.as_deref(), answer.as_deref())? {
            return Err(Error::Terminal);
        }
        return detail_json(&row);
    }
    let snapshot = row.try_get::<Option<String>, _>("scoring_rule_content")?;
    let snapshot = snapshot
        .as_deref()
        .filter(|value| !value.trim().is_empty())
        .ok_or_else(|| invalid("旧训练没有冻结评分规则，请重新创建训练；历史成绩不会重算"))?;
    let topic = row.try_get::<Option<String>, _>("topic")?;
    let (score, details) = calculate(
        row.try_get("device_id")?,
        topic.as_deref(),
        snapshot,
        answer.as_deref(),
    )?;
    sqlx::query("UPDATE group_net_train SET answer=?,score=?,content=? WHERE id=?")
        .bind(answer)
        .bind(score.to_string())
        .bind(details)
        .bind(id)
        .execute(&mut *connection)
        .await?;
    let row = sqlx::query(DETAIL_SELECT)
        .bind(id)
        .fetch_one(&mut *connection)
        .await?;
    detail_json(&row)
}

pub(crate) async fn submit(
    State(state): State<AppState>,
    Extension(user): Extension<AuthUser>,
    body: Result<Json<Option<AnswerRequest>>, JsonRejection>,
) -> Response {
    let body = match body {
        Ok(Json(Some(body))) => body,
        Ok(Json(None)) => return failure(invalid("训练id不能为空")),
        Err(_) => return failure(invalid("请求参数错误")),
    };
    let Some(id) = body.id else {
        return failure(invalid("训练id不能为空"));
    };
    if !body.unsupported.is_empty() {
        return failure(invalid(
            "提交只接受训练id和原始答案，不接受客户端成绩等字段",
        ));
    }
    let Some(pool) = state.pool.as_ref() else {
        return envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "");
    };
    let mut tx = match pool.begin().await {
        Ok(tx) => tx,
        Err(error) => return failure(error.into()),
    };
    let result = submit_answer(&mut tx, &user, id, body.answer).await;
    finish(tx, result).await
}

#[cfg(test)]
mod tests {
    use super::*;

    const RULES: &str = r#"[{"paramName":"网路地址","weight":2},{"paramName":"单台地址","weight":3},{"paramName":"信道","weight":0.25}]"#;
    const TOPIC: &str =
        include_str!("../../../../backend/src/test/resources/group-net-j210-topic.json");

    fn answer() -> Value {
        let mut serials = vec![json!({}); 20];
        serials[0] = json!({"networkdress": "0319"});
        serials[19] = json!({"dressname": 299});
        json!({
            "chananel1": [{"indexs": 1, "receptionChananel": 0, "sendChananel": "29.0"}],
            "serialNumbers": serials,
        })
    }

    #[test]
    fn scores_each_decimal_cell_using_its_serial_and_channel_mapping() {
        let frozen = freeze(7, Some(RULES), Some(TOPIC)).unwrap();
        let (score, details) =
            calculate(Some(7), Some(TOPIC), &frozen, Some(&answer().to_string())).unwrap();
        assert_eq!(score, Decimal::new(550, 2));
        let details: Vec<Value> = serde_json::from_str(&details).unwrap();
        assert_eq!(details.len(), 149);
        assert_eq!(
            details[0],
            json!({"xy": [4, 2], "actual": "0319", "correct": true, "weight": 2, "score": 2})
        );
        assert_eq!(details[1]["actual"], 299);
        assert_eq!(details[2]["actual"], 0);
        assert_eq!(details[3]["correct"], true);
        assert_eq!(details[4]["actual"], "");
        assert_eq!(details[4]["correct"], false);
        assert!(matches!(
            calculate(Some(8), Some(TOPIC), &frozen, Some("{}")),
            Err(Error::Invalid(_))
        ));
    }

    #[test]
    fn zero_weights_preserve_correctness_without_awarding_unanswered_zero_channels() {
        let rules = RULES
            .replace("2}", "0}")
            .replace("3}", "0}")
            .replace("0.25}", "0}");
        let frozen = freeze(7, Some(&rules), Some(TOPIC)).unwrap();
        let (score, details) =
            calculate(Some(7), Some(TOPIC), &frozen, Some(&answer().to_string())).unwrap();
        assert_eq!(score, Decimal::ZERO);
        let details: Vec<Value> = serde_json::from_str(&details).unwrap();
        assert_eq!(details[2]["correct"], true);
        assert_eq!(details[4]["correct"], false);
        let (score, details) = calculate(Some(7), Some(TOPIC), &frozen, Some("{}")).unwrap();
        assert_eq!(score, Decimal::ZERO);
        let details: Vec<Value> = serde_json::from_str(&details).unwrap();
        assert!(details
            .iter()
            .all(|cell| cell["correct"] == false && cell["actual"] == ""));
    }

    #[test]
    fn invalid_or_incomplete_templates_cannot_freeze_scoring_rules() {
        let mut topic: Value = serde_json::from_str(TOPIC).unwrap();
        topic.as_array_mut().unwrap().pop();
        assert!(matches!(
            freeze(7, Some(RULES), Some(&topic.to_string())),
            Err(Error::Invalid(_))
        ));

        let mut topic: Value = serde_json::from_str(TOPIC).unwrap();
        topic[1]["xy"] = json!([0, 0]);
        assert!(matches!(
            freeze(7, Some(RULES), Some(&topic.to_string())),
            Err(Error::Invalid(_))
        ));

        let mut topic: Value = serde_json::from_str(TOPIC).unwrap();
        topic[3]["value"]["xdValues"] = json!(2);
        assert!(matches!(
            freeze(7, Some(RULES), Some(&topic.to_string())),
            Err(Error::Invalid(_))
        ));
        assert!(matches!(
            freeze(7, Some("[]"), Some(TOPIC)),
            Err(Error::Invalid(_))
        ));
        assert!(matches!(
            freeze(7, Some(&RULES.replace("0.25", "0.251")), Some(TOPIC)),
            Err(Error::Invalid(_))
        ));
    }

    #[test]
    fn rejects_duplicate_channels_and_malformed_decimal_answers() {
        let frozen = freeze(7, Some(RULES), Some(TOPIC)).unwrap();
        let mut duplicate = answer();
        let channel = duplicate["chananel1"][0].clone();
        duplicate["chananel1"].as_array_mut().unwrap().push(channel);
        assert!(matches!(
            calculate(Some(7), Some(TOPIC), &frozen, Some(&duplicate.to_string())),
            Err(Error::Invalid(_))
        ));

        let mut malformed = answer();
        malformed["serialNumbers"][0]["networkdress"] = json!("319garbage");
        assert!(matches!(
            calculate(Some(7), Some(TOPIC), &frozen, Some(&malformed.to_string())),
            Err(Error::Invalid(_))
        ));
        malformed["serialNumbers"][0]["networkdress"] = json!(true);
        assert!(matches!(
            calculate(Some(7), Some(TOPIC), &frozen, Some(&malformed.to_string())),
            Err(Error::Invalid(_))
        ));

        let mut precise = answer();
        precise["chananel1"][0]["sendChananel"] = json!("29.000000000000000000000000000001");
        let (score, _) =
            calculate(Some(7), Some(TOPIC), &frozen, Some(&precise.to_string())).unwrap();
        assert_eq!(score, Decimal::new(525, 2));
    }

    #[test]
    fn retries_compare_arbitrary_precision_numbers_without_changing_json_types() {
        assert!(same_answer(
            Some(r#"{"a":1.00,"b":[null,"x"]}"#),
            Some(r#"{"b":[null,"x"],"a":1e0}"#)
        )
        .unwrap());
        assert!(!same_answer(
            Some(r#"{"a":[0.10000000000000001]}"#),
            Some(r#"{"a":[0.10000000000000002]}"#)
        )
        .unwrap());
        assert!(!same_answer(Some(r#"{"a":"1"}"#), Some(r#"{"a":1}"#)).unwrap());
        assert!(same_answer(
            Some(r#"{"a":1000.00,"z":-0.0}"#),
            Some(r#"{"a":1e3,"z":0}"#)
        )
        .unwrap());
        assert!(!same_answer(Some(r#"{"a":[1,2]}"#), Some(r#"{"a":[2,1]}"#)).unwrap());
    }
}
