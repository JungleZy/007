use crate::platform::envelope::envelope_response;
use crate::shared::handkey_parse::{field_string, PatLog};
use axum::http::StatusCode;
use axum::response::Response;
use serde::Deserialize;
use serde::Serialize;
use serde_json::Value;

#[derive(Deserialize)]
pub(super) struct AddRequest {
    pub(super) name: Option<String>,
    #[serde(rename = "isCable")]
    pub(super) is_cable: Option<i32>,
    #[serde(rename = "type")]
    pub(super) train_type: Option<i32>,
    #[serde(rename = "codeSort")]
    pub(super) code_sort: Option<bool>,
    #[serde(rename = "isRandom")]
    pub(super) is_random: Option<bool>,
    #[serde(rename = "messageNumber")]
    pub(super) message_number: Option<i32>,
    #[serde(rename = "ruleId")]
    pub(super) rule_id: Option<String>,
    #[serde(rename = "isAverage")]
    pub(super) is_average: Option<bool>,
    #[serde(rename = "cableId")]
    pub(super) cable_id: Option<String>,
    #[serde(rename = "startPage")]
    pub(super) start_page: Option<i32>,
}

#[derive(Deserialize)]
pub(super) struct ActionRequest {
    pub(super) id: Option<String>,
    pub(super) attempt: Option<i32>,
}
#[derive(Deserialize)]
pub(super) struct DeleteQuery {
    #[serde(rename = "trainId")]
    pub(super) train_id: Option<String>,
}

#[derive(Deserialize, Serialize, Clone, PartialEq)]
pub(super) struct CaptureInterval {
    #[serde(rename = "startedMs")]
    pub(super) started_ms: i64,
    #[serde(rename = "endedMs")]
    pub(super) ended_ms: i64,
}

#[derive(Deserialize)]
pub(super) struct ContentQuery {
    pub(super) id: Option<String>,
    #[serde(rename = "floorNumber")]
    pub(super) floor_number: Option<i32>,
}

#[derive(Deserialize)]
pub(super) struct ContentUpdate {
    #[serde(rename = "trainId")]
    pub(super) train_id: Option<String>,
    #[serde(rename = "floorNumber")]
    pub(super) floor_number: Option<i32>,
    #[serde(rename = "messageBody")]
    pub(super) message_body: Option<Vec<Value>>,
    pub(super) standard: Option<Vec<Value>>,
    #[serde(rename = "finishInfo")]
    pub(super) finish_info: Option<String>,
    pub(super) attempt: Option<i32>,
    #[serde(rename = "captureIntervals")]
    pub(super) capture_intervals: Option<Vec<CaptureInterval>>,
}

#[derive(Deserialize)]
pub(super) struct AddContentRequest {
    #[serde(rename = "trainId")]
    pub(super) train_id: Option<String>,
    #[serde(rename = "messageBody")]
    pub(super) message_body: Option<Vec<Vec<Value>>>,
}

pub(super) fn err(e: sqlx::Error, op: &'static str) -> Response {
    tracing::warn!(error=%e,operation=op,"post telegram write failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

pub(super) fn invalid(message: &'static str) -> Response {
    envelope_response(StatusCode::OK, 202, message, message)
}

pub(super) fn content_item_json(
    object: &serde_json::Map<String, Value>,
) -> Result<(String, String, String, String, String), &'static str> {
    let mores_key = field_string(object, "moresKey")?.to_owned();
    let mores_value = field_string(object, "moresValue")?.to_owned();
    let mores_time = field_string(object, "moresTime")?.to_owned();
    let pat_keys = field_string(object, "patKeys")?.to_owned();
    let pat_logs = field_string(object, "patLogs")?.to_owned();
    serde_json::from_str::<Vec<String>>(&mores_key).map_err(|_| "拍发内容字段无效")?;
    serde_json::from_str::<Vec<Vec<i32>>>(&mores_value).map_err(|_| "拍发内容字段无效")?;
    serde_json::from_str::<Vec<Vec<f64>>>(&mores_time).map_err(|_| "拍发内容字段无效")?;
    serde_json::from_str::<Vec<Vec<PatLog>>>(&pat_logs).map_err(|_| "拍发内容字段无效")?;
    Ok((mores_key, mores_value, mores_time, pat_keys, pat_logs))
}

pub(super) fn count_characters(body: &[Value]) -> Result<i64, &'static str> {
    let mut count = 0_i64;
    for item in body {
        let Some(object) = item.as_object() else {
            return Err("拍发组无效");
        };
        let mores_key = field_string(object, "moresKey")?;
        let mores_value = field_string(object, "moresValue")?;
        let mores_time = field_string(object, "moresTime")?;
        let pat_keys = field_string(object, "patKeys")?;
        let pat_logs = field_string(object, "patLogs")?;
        serde_json::from_str::<Vec<String>>(mores_key).map_err(|_| "拍发内容字段无效")?;
        let values: Vec<Vec<i32>> =
            serde_json::from_str(mores_value).map_err(|_| "拍发内容字段无效")?;
        let times: Vec<Vec<f64>> =
            serde_json::from_str(mores_time).map_err(|_| "拍发内容字段无效")?;
        let logs: Vec<Vec<PatLog>> =
            serde_json::from_str(pat_logs).map_err(|_| "拍发内容字段无效")?;
        let keys: Vec<String> = serde_json::from_str(pat_keys).map_err(|_| "拍发内容字段无效")?;
        if keys.len() != values.len() || keys.len() != times.len() || keys.len() != logs.len() {
            return Err("拍发字符、码值与时长记录不一致");
        }
        for index in 0..keys.len() {
            if values[index].len() != times[index].len() {
                return Err("拍发字符、码值与时长记录不一致");
            }
            if values[index].iter().any(|value| *value != 0 && *value != 1)
                || times[index]
                    .iter()
                    .any(|value| !value.is_finite() || *value < 0.0)
            {
                return Err("拍发码值或时长记录无效");
            }
            let mut symbol = 0_usize;
            for event in &logs[index] {
                if !(0..=4).contains(&event.key) || !event.value.is_finite() || event.value < 0.0 {
                    return Err("拍发事件类型或时长无效");
                }
                if event.key < 2 {
                    if symbol >= values[index].len()
                        || event.key != values[index][symbol]
                        || event.value != times[index][symbol]
                    {
                        return Err("拍发事件与码值时长不一致");
                    }
                    symbol += 1;
                }
            }
            if symbol != values[index].len() {
                return Err("拍发事件缺少码值记录");
            }
            count = count
                .checked_add(
                    keys[index]
                        .chars()
                        .filter(|character| {
                            !character.is_whitespace()
                                && *character != '?'
                                && *character != '.'
                                && *character != '。'
                        })
                        .count() as i64,
                )
                .ok_or("拍发内容过大")?;
        }
    }
    Ok(count)
}
