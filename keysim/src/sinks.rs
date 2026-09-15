//! 输出层：把时间轴变成串口字节、桥接帧、后端上传载荷。
//!
//! 字节协议同 WebSerial.handleData（WebSerial.js:69-98）：
//! - 首字节 1 = 按下，整帧 2 字节；
//! - 首字节 2 = 抬起，整帧 3 字节；
//! - 首字节落在 CODES=[11..45] = 电子键单字节码，整帧 1 字节；
//! 填充字节不参与解析（客户端只按帧长跳过），统一填 0。

use serde_json::{json, Value};

use crate::faults::{self, Chunk, Chunking};
use crate::timeline::{Kind, Timeline};

pub const DOWN: [u8; 2] = [1, 0];
pub const UP: [u8; 3] = [2, 0, 0];

pub fn to_bytes(timeline: &Timeline) -> Vec<Chunk> {
    timeline
        .events
        .iter()
        .map(|event| Chunk {
            at: event.at,
            bytes: match event.kind {
                Kind::Down => DOWN.to_vec(),
                Kind::Up => UP.to_vec(),
                Kind::Code(code) => vec![code],
            },
        })
        .collect()
}

pub fn to_byte_stream(timeline: &Timeline, mode: Chunking, window: f64, seed: u32) -> Vec<Chunk> {
    faults::chunk(&to_bytes(timeline), mode, window, seed)
}

pub fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|byte| format!("{byte:02x}")).collect::<Vec<_>>().join(" ")
}

/// 桥接/注入共用的帧对象（MessageWebSocket.js:148-155 只取 parsed.data）。
/// 不声明 receivedAt：页面会用自己的 performance.now() 打标（useTraffic.js:11），
/// 这与真实桥接一致；点划时长只取 d 的差值，所以 d 用模拟器时钟即可精确。
pub fn to_frames(timeline: &Timeline, base: f64) -> Vec<(f64, Value)> {
    timeline
        .events
        .iter()
        .map(|event| {
            let at = base + event.at;
            let frame = match event.kind {
                Kind::Down => json!({"t": 0, "k": 0, "d": at}),
                Kind::Up => json!({"t": 0, "k": 1, "d": at}),
                Kind::Code(code) => json!({"t": 1, "k": 0, "d": [code]}),
            };
            (at, frame)
        })
        .collect()
}


fn round3(value: f64) -> f64 {
    (value * 1000.0).round() / 1000.0
}

/// 把时间轴压进真实可用窗口；服务端采集区间上界是 captureBound
/// （GeneralTickerPatService.java:813-816），所以要么实时回放，要么按此缩放。
pub fn fit_scale(timeline: &Timeline, elapsed_ms: f64) -> Result<f64, String> {
    let usable = elapsed_ms - 20.0;
    if !(usable.is_finite() && usable > 0.0) {
        return Err(format!("可用采集窗口 {elapsed_ms}ms 太短"));
    }
    Ok((usable / timeline.duration().max(1.0)).min(1.0))
}

pub struct Capture {
    pub attempt: i64,
    pub server_elapsed_ms: f64,
}

fn capture_intervals(timeline: &Timeline, capture: &Capture, scale: f64) -> Value {
    let Some(first) = timeline.body_chars().next() else {
        return json!([]);
    };
    let started = (capture.server_elapsed_ms + first.started_at * scale).floor().max(0.0);
    let ended = (capture.server_elapsed_ms + timeline.duration() * scale).ceil().max(0.0);
    if ended <= started {
        return json!([]);
    }
    json!([{"startedMs": started as i64, "endedMs": ended as i64}])
}

/// 手键 uploadResult 请求体，字段与构造顺序同 handkeyZuXun 学生端 handKeyTrain.js:632-641。
/// 服务端会按 measurePage 校验：每字的点划数与时长数必须相等，
/// 且逐符时长之和不得超过采集区间（GeneralTickerPatService.java:596-600,837-873）。
pub fn hand_upload_payload(
    timeline: &Timeline,
    train_id: i64,
    floor_number: i64,
    capture: &Capture,
    scale: f64,
    off_size: i64,
) -> Value {
    let mut groups: Vec<(Vec<String>, Vec<Vec<u8>>, Vec<Vec<f64>>, Vec<Value>)> = Vec::new();
    for item in timeline.body_chars() {
        let index = item.group.unwrap_or(0);
        while groups.len() <= index {
            groups.push((Vec::new(), Vec::new(), Vec::new(), Vec::new()));
        }
        let slot = &mut groups[index];
        slot.0.push(item.value.clone());
        slot.1.push(item.codes.clone());
        slot.2.push(item.durations.iter().map(|value| round3(value * scale)).collect());

        let mut logs: Vec<Value> = Vec::new();
        if let Some(gap) = item.leading_gap {
            logs.push(json!({"name": "间隔", "key": 2, "value": round3(gap * scale)}));
        }
        for (index, code) in item.codes.iter().enumerate() {
            logs.push(json!({
                "name": if *code == 1 { "划" } else { "点" },
                "key": code,
                "value": round3(item.durations[index] * scale)
            }));
            if let Some(gap) = item.gaps.get(index) {
                logs.push(json!({"name": "间隔", "key": 2, "value": round3(gap * scale)}));
            }
        }
        slot.3.push(Value::Array(logs));
    }

    let message_body: Vec<Value> = groups
        .into_iter()
        .map(|(keys, values, times, logs)| {
            json!({
                "moresKey": "#",
                "patKeys": serde_json::to_string(&keys).unwrap_or_else(|_| "[]".into()),
                "moresValue": serde_json::to_string(&values).unwrap_or_else(|_| "[]".into()),
                "moresTime": serde_json::to_string(&times).unwrap_or_else(|_| "[]".into()),
                "patLogs": serde_json::to_string(&logs).unwrap_or_else(|_| "[]".into())
            })
        })
        .collect();

    let standard = json!({
        "dot": (timeline.plan.dot * scale).round() as i64,
        "line": (timeline.plan.dash * scale).round() as i64,
        "codeGap": (timeline.plan.gap * scale).round() as i64,
        "wordGap": (timeline.plan.word * scale).round() as i64,
        "groupGap": (timeline.plan.group * scale).round() as i64,
        "offSize": off_size
    });
    let mut finish_info = standard.clone();
    finish_info["patLogs"] = json!([]);

    json!({
        "trainId": train_id,
        "floorNumber": floor_number,
        "messageBody": message_body,
        "attempt": capture.attempt,
        "captureIntervals": capture_intervals(timeline, capture, scale),
        "standard": [standard],
        "finishInfo": finish_info.to_string()
    })
}

/// 电子键 uploadResult 请求体，字段同 electronKeyZuXun 学生端 handKeyTrain.js:467-473。
/// 服务端要求 pageValue 按 sort 严格递增，Σtime 不超过采集区间
/// （GeneralKeyPatService.java:517-539）。
pub fn electron_upload_payload(
    timeline: &Timeline,
    train_id: i64,
    page_number: i64,
    capture: &Capture,
    scale: f64,
) -> Value {
    let mut groups: Vec<(Vec<String>, Vec<i64>)> = Vec::new();
    let mut previous_end: Option<f64> = None;
    for item in timeline.body_chars() {
        let index = item.group.unwrap_or(0);
        while groups.len() <= index {
            groups.push((Vec::new(), Vec::new()));
        }
        let span = previous_end.map(|end| item.ended_at - end).unwrap_or(0.0);
        previous_end = Some(item.ended_at);
        let slot = &mut groups[index];
        slot.0.push(item.value.clone());
        slot.1.push((span * scale).round() as i64);
    }

    let page_value: Vec<Value> = groups
        .into_iter()
        .enumerate()
        .map(|(sort, (values, times))| {
            json!({
                "id": Value::Null,
                "pageNumber": page_number,
                "sort": sort,
                "trainId": train_id,
                "key": "[\"#\"]",
                "value": serde_json::to_string(&values).unwrap_or_else(|_| "[]".into()),
                "time": serde_json::to_string(&times).unwrap_or_else(|_| "[]".into())
            })
        })
        .collect();

    json!({
        "trainId": train_id,
        "pageNumber": page_number,
        "pageValue": page_value,
        "attempt": capture.attempt,
        "captureIntervals": capture_intervals(timeline, capture, scale)
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::keying::{electron_timeline, hand_timeline, ElectronOptions, HandOptions};

    fn interval_millis(payload: &Value) -> f64 {
        payload["captureIntervals"]
            .as_array()
            .map(|list| {
                list.iter()
                    .map(|item| item["endedMs"].as_f64().unwrap_or(0.0) - item["startedMs"].as_f64().unwrap_or(0.0))
                    .sum()
            })
            .unwrap_or(0.0)
    }

    /// 复现服务端 measurePage 的校验，逐条断言
    fn measure_hand(payload: &Value) -> (usize, f64) {
        let mut characters = 0;
        let mut symbol_millis = 0.0;
        for group in payload["messageBody"].as_array().expect("messageBody") {
            let keys: Vec<String> = serde_json::from_str(group["patKeys"].as_str().unwrap()).unwrap();
            let values: Vec<Vec<u8>> = serde_json::from_str(group["moresValue"].as_str().unwrap()).unwrap();
            let times: Vec<Vec<f64>> = serde_json::from_str(group["moresTime"].as_str().unwrap()).unwrap();
            assert_eq!(values.len(), keys.len(), "原始码与字符数量不一致");
            assert_eq!(times.len(), keys.len(), "时长与字符数量不一致");
            for (index, key) in keys.iter().enumerate() {
                assert_eq!(key.chars().count(), 1, "拍发事件 {key} 不是单个字符");
                assert!(!values[index].is_empty(), "原始点划不能为空");
                assert_eq!(times[index].len(), values[index].len(), "点划与时长数量不一致");
                for code in &values[index] {
                    assert!(*code == 0 || *code == 1, "原始码只能是点或划");
                }
                for duration in &times[index] {
                    assert!(duration.is_finite() && *duration >= 0.0, "时长必须有限非负");
                    symbol_millis += duration;
                }
                characters += 1;
            }
        }
        (characters, symbol_millis)
    }

    #[test]
    /// 手键载荷满足服务端全部约束，逐符时长之和不超过采集区间
    fn hand_payload_satisfies_server_rules() {
        let timeline = hand_timeline(&HandOptions { text: "ABCD EFGH".into(), ..Default::default() }).unwrap();
        let capture = Capture { attempt: 0, server_elapsed_ms: 2000.0 };
        let payload = hand_upload_payload(&timeline, 75, 1, &capture, 1.0, 51);
        let (characters, symbol_millis) = measure_hand(&payload);
        assert_eq!(characters, 8, "控制符不应进 messageBody");
        let duration = interval_millis(&payload);
        assert!(duration > 0.0, "有字符就必须有非零采集区间");
        assert!(symbol_millis <= duration + 1.0, "逐符时长 {symbol_millis} 超过采集区间 {duration}");
        assert!(payload["captureIntervals"][0]["startedMs"].as_f64().unwrap() >= 2000.0);
    }

    #[test]
    /// patLogs 与点划一一对应，前导间隔归到下一个字
    fn hand_payload_logs_match_symbols() {
        let options = HandOptions { text: "AB".into(), preamble: false, tail: "none".into(), ..Default::default() };
        let timeline = hand_timeline(&options).unwrap();
        let payload = hand_upload_payload(&timeline, 1, 1, &Capture { attempt: 0, server_elapsed_ms: 0.0 }, 1.0, 51);
        let logs: Vec<Vec<Value>> = serde_json::from_str(payload["messageBody"][0]["patLogs"].as_str().unwrap()).unwrap();
        let values: Vec<Vec<u8>> = serde_json::from_str(payload["messageBody"][0]["moresValue"].as_str().unwrap()).unwrap();
        assert_eq!(logs[0].iter().filter(|entry| entry["key"] != 2).count(), values[0].len());
        assert_ne!(logs[0][0]["key"], 2, "首字不应有前导间隔");
        assert_eq!(logs[1][0]["key"], 2, "第二个字第一项应是字间隔");
    }

    #[test]
    /// 压缩到更短窗口后仍满足"逐符时长 ≤ 采集区间"
    fn scaling_keeps_server_invariant() {
        let timeline = hand_timeline(&HandOptions { text: "ABCD EFGH IJKL".into(), ..Default::default() }).unwrap();
        let scale = fit_scale(&timeline, 3000.0).unwrap();
        assert!(scale < 1.0);
        let payload = hand_upload_payload(&timeline, 1, 1, &Capture { attempt: 0, server_elapsed_ms: 0.0 }, scale, 51);
        let (_, symbol_millis) = measure_hand(&payload);
        let duration = interval_millis(&payload);
        assert!(duration <= 3000.0, "采集区间 {duration} 超出真实窗口");
        assert!(symbol_millis <= duration + 1.0);
    }

    #[test]
    /// 电子键载荷按页内位置严格递增，且节拍能让服务端重算出目标码率
    fn electron_payload_rate_matches() {
        let rate = 20.0;
        let options = ElectronOptions { text: "ABCD EFGH IJKL MNOP".into(), rate, ..Default::default() };
        let timeline = electron_timeline(&options).unwrap();
        let payload = electron_upload_payload(&timeline, 1, 1, &Capture { attempt: 0, server_elapsed_ms: 0.0 }, 1.0);
        let sorts: Vec<i64> = payload["pageValue"]
            .as_array()
            .unwrap()
            .iter()
            .map(|group| group["sort"].as_i64().unwrap())
            .collect();
        assert_eq!(sorts, vec![0, 1, 2, 3]);
        let characters: usize = payload["pageValue"]
            .as_array()
            .unwrap()
            .iter()
            .map(|group| serde_json::from_str::<Vec<String>>(group["value"].as_str().unwrap()).unwrap().len())
            .sum();
        let active = interval_millis(&payload);
        // 服务端 FOUR_CHARACTER_GROUPS_PER_MINUTE.rate = chars*60000/(millis*4)，HALF_UP 0 位
        let computed = (characters as f64 * 60000.0 / (active * 4.0)).round();
        assert_eq!(computed, rate, "服务端会算出 {computed} 组/分");
    }

    #[test]
    /// 分包方式不改变字节总量与顺序
    fn chunking_preserves_bytes() {
        let timeline = hand_timeline(&HandOptions { text: "AB CD".into(), ..Default::default() }).unwrap();
        let flat: Vec<u8> = to_bytes(&timeline).into_iter().flat_map(|chunk| chunk.bytes).collect();
        for mode in [Chunking::Exact, Chunking::Split, Chunking::Merge, Chunking::Random] {
            let chunked: Vec<u8> = to_byte_stream(&timeline, mode, 5.0, 3)
                .into_iter()
                .flat_map(|chunk| chunk.bytes)
                .collect();
            assert_eq!(chunked, flat, "{mode:?} 改变了字节流");
        }
    }
}
