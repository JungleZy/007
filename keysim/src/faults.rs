//! 故障注入：都是评审已记录的真实串口故障模式
//! （docs/reviews/2026-09-10-customer-issue-analysis.md §6、plans T05）：
//! 分包/粘包、重复按下、缺抬起、未知字节、≤10ms 抖动。

use crate::timeline::{Event, Kind, Rng, Timeline};

/// 客户端对重复按下的容忍窗口：max(2000, lineLimit*8)（useTraffic.js:44）
pub const DUP_DOWN_WINDOW: f64 = 2000.0;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Fault {
    DupDown,
    MissingUp,
    MicroPress,
    UnknownByte,
}

impl Fault {
    pub fn parse(name: &str) -> Option<Fault> {
        match name {
            "dupDown" | "dup-down" => Some(Fault::DupDown),
            "missingUp" | "missing-up" => Some(Fault::MissingUp),
            "microPress" | "micro-press" => Some(Fault::MicroPress),
            "unknownByte" | "unknown-byte" => Some(Fault::UnknownByte),
            _ => None,
        }
    }

    pub fn all() -> [&'static str; 4] {
        ["dupDown", "missingUp", "microPress", "unknownByte"]
    }
}

fn sort_events(timeline: &mut Timeline) {
    timeline
        .events
        .sort_by(|left, right| left.at.partial_cmp(&right.at).unwrap_or(std::cmp::Ordering::Equal));
}

/// 在首次按压前插入一次重复按下（无抬起）：客户端应忽略它并用真正那次按下计时
pub fn dup_down(timeline: &mut Timeline) {
    if let Some(first) = timeline.events.iter().find(|event| event.kind == Kind::Down) {
        let at = (first.at - 5.0).max(0.0);
        timeline.events.push(Event { at, kind: Kind::Down, fault: Some("dup-down") });
        sort_events(timeline);
    }
}

/// 丢掉首次抬起：客户端要在下一次新按下时自恢复
pub fn missing_up(timeline: &mut Timeline) {
    if let Some(index) = timeline.events.iter().position(|event| event.kind == Kind::Up) {
        timeline.events.remove(index);
    }
}

/// 插入一次 ≤10ms 的抖动按压：必须被丢弃且不污染基准（useTraffic.js:64）
pub fn micro_press(timeline: &mut Timeline) {
    let at = timeline.chars.first().map(|item| (item.started_at - 20.0).max(0.0)).unwrap_or(0.0);
    timeline.events.push(Event { at, kind: Kind::Down, fault: Some("micro-press") });
    timeline.events.push(Event { at: at + 6.0, kind: Kind::Up, fault: Some("micro-press") });
    sort_events(timeline);
}

/// 插入一个既非 1/2 也不在 CODES 里的字节：应告警跳过而不静默丢帧（WebSerial.js:86-89）
pub fn unknown_byte(timeline: &mut Timeline) {
    let at = timeline.chars.first().map(|item| (item.started_at - 1.0).max(0.0)).unwrap_or(0.0);
    timeline.events.push(Event { at, kind: Kind::Code(7), fault: Some("unknown-byte") });
    sort_events(timeline);
}

pub fn apply(timeline: &mut Timeline, faults: &[Fault]) {
    for fault in faults {
        match fault {
            Fault::DupDown => dup_down(timeline),
            Fault::MissingUp => missing_up(timeline),
            Fault::MicroPress => micro_press(timeline),
            Fault::UnknownByte => unknown_byte(timeline),
        }
    }
}

/// 串口分包方式
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Chunking {
    /// 一帧一次 read（理想串口）
    Exact,
    /// 每帧拆成单字节多次 read（分包）
    Split,
    /// 相邻帧并成一次 read（粘包）
    Merge,
    /// 由 seed 决定的混合切分
    Random,
}

impl Chunking {
    pub fn parse(name: &str) -> Option<Chunking> {
        match name {
            "exact" => Some(Chunking::Exact),
            "split" => Some(Chunking::Split),
            "merge" => Some(Chunking::Merge),
            "random" => Some(Chunking::Random),
            _ => None,
        }
    }
}

#[derive(Debug, Clone)]
pub struct Chunk {
    pub at: f64,
    pub bytes: Vec<u8>,
}

pub fn chunk(frames: &[Chunk], mode: Chunking, window: f64, seed: u32) -> Vec<Chunk> {
    match mode {
        Chunking::Exact => frames.to_vec(),
        Chunking::Split => frames
            .iter()
            .flat_map(|frame| frame.bytes.iter().map(move |byte| Chunk { at: frame.at, bytes: vec![*byte] }))
            .collect(),
        Chunking::Merge => {
            let mut out: Vec<Chunk> = Vec::new();
            for frame in frames {
                match out.last_mut() {
                    Some(last) if frame.at - last.at <= window => last.bytes.extend_from_slice(&frame.bytes),
                    _ => out.push(frame.clone()),
                }
            }
            out
        }
        Chunking::Random => {
            let mut rng = Rng::new(seed);
            let mut out: Vec<Chunk> = Vec::new();
            for frame in frames {
                let roll = rng.next_f64();
                let mergeable = out
                    .last()
                    .map(|last| frame.at - last.at <= window.max(1.0))
                    .unwrap_or(false);
                if roll < 0.34 && mergeable {
                    out.last_mut().expect("mergeable 保证非空").bytes.extend_from_slice(&frame.bytes);
                } else if roll < 0.67 {
                    for byte in &frame.bytes {
                        out.push(Chunk { at: frame.at, bytes: vec![*byte] });
                    }
                } else {
                    out.push(frame.clone());
                }
            }
            out
        }
    }
}
