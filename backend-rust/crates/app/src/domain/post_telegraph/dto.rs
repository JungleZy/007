use crate::platform::envelope::envelope_response;
use axum::http::StatusCode;
use axum::response::Response;
use rand::{seq::SliceRandom, Rng};
use serde::Deserialize;
use serde::Serialize;

#[derive(Deserialize)]
pub(super) struct AddRequest {
    pub(super) title: Option<String>,
    #[serde(rename = "isCable")]
    pub(super) is_cable: Option<i32>,
    #[serde(rename = "cableId")]
    pub(super) cable_id: Option<String>,
    #[serde(rename = "startPage")]
    pub(super) start_page: Option<i32>,
    #[serde(rename = "totalNumber")]
    pub(super) total_number: Option<i32>,
    #[serde(rename = "messageType")]
    pub(super) message_type: Option<i32>,
    #[serde(rename = "ruleId")]
    pub(super) rule_id: Option<String>,
}

#[derive(Deserialize)]
pub(super) struct ActionRequest {
    pub(super) id: Option<String>,
    #[serde(rename = "protocolVersion")]
    pub(super) protocol_version: Option<i32>,
    pub(super) attempt: Option<i32>,
}
#[derive(Deserialize)]
pub(super) struct DeleteQuery {
    #[serde(rename = "trainId")]
    pub(super) train_id: Option<String>,
}

#[derive(Clone, Deserialize, Serialize, PartialEq)]
pub(super) struct CaptureInterval {
    #[serde(rename = "startedMs")]
    pub(super) started_ms: i64,
    #[serde(rename = "endedMs")]
    pub(super) ended_ms: i64,
}

#[derive(Clone, Deserialize)]
pub(super) struct PageMessage {
    #[serde(rename = "trainId")]
    pub(super) train_id: Option<String>,
    #[serde(rename = "pageNumber")]
    pub(super) page_number: Option<i32>,
    pub(super) key: Option<String>,
    pub(super) value: Option<String>,
    pub(super) time: Option<String>,
    pub(super) sort: Option<i32>,
}

#[derive(Deserialize)]
pub(super) struct FinishPageRequest {
    pub(super) id: Option<String>,
    #[serde(rename = "pageNumber")]
    pub(super) page_number: Option<i32>,
    #[serde(rename = "protocolVersion")]
    pub(super) protocol_version: Option<i32>,
    pub(super) attempt: Option<i32>,
    pub(super) value: Option<Vec<PageMessage>>,
    #[serde(rename = "captureIntervals")]
    pub(super) capture_intervals: Option<Vec<CaptureInterval>>,
}

pub(super) fn err(e: sqlx::Error, op: &'static str) -> Response {
    tracing::warn!(error=%e, operation=op, "post telegraph write failed");
    envelope_response(StatusCode::INTERNAL_SERVER_ERROR, 500, "服务器错误", "")
}

pub(super) fn business(code: i32, message: &'static str) -> Response {
    let status = if code == 500 {
        StatusCode::INTERNAL_SERVER_ERROR
    } else {
        StatusCode::OK
    };
    envelope_response(
        status,
        code,
        message,
        if matches!(code, 202 | 208) {
            message
        } else {
            ""
        },
    )
}
pub(super) fn generated_groups(message_type: i32, count: usize) -> Vec<String> {
    let alphabet: &[u8] = match message_type {
        0 => b"0123456789",
        1 => b"ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        _ => b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
    };
    let mut rng = rand::thread_rng();
    let mut groups = Vec::with_capacity(count);
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
                    let source: &[u8] = if digit_positions.contains(&position) {
                        b"0123456789"
                    } else {
                        b"ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    };
                    loop {
                        let value = source[rng.gen_range(0..source.len())];
                        if !used.contains(&value) {
                            used.push(value);
                            break;
                        }
                    }
                }
                String::from_utf8(used).expect("report alphabet is ASCII")
            } else {
                alphabet
                    .choose_multiple(&mut rng, 4)
                    .map(|value| char::from(*value))
                    .collect()
            };
            if !groups
                .iter()
                .rev()
                .take(10)
                .any(|value| value == &candidate)
            {
                break candidate;
            }
        };
        groups.push(group);
    }
    groups
}
