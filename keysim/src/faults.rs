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
    pub fn label(&self) -> &'static str {
        match self {
            Fault::DupDown => "重复按下",
            Fault::MissingUp => "缺抬起",
            Fault::MicroPress => "≤10ms 抖动",
            Fault::UnknownByte => "未知字节",
        }
    }

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

/// 默认注入间隔：每 25 个字触发一次（一页 400 字约 16 次）。
/// 早先每种故障只在页首注入一次，100 组的页面等于没注入 —— 那是个缺陷，不是口径。
pub const DEFAULT_EVERY: f64 = 25.0;

/// 一次注入的锚点：正文里的某个字
struct Anchor {
    started_at: f64,
    ended_at: f64,
    leading_gap: f64,
}

/// 按"每 every 个字一次"的概率在整页上抽锚点。
/// 同种子同结果；at least one 保证：页面再短也至少注入一次，否则勾了没反应更像 bug。
fn anchors(timeline: &Timeline, seed: u32, every: f64, salt: u32) -> Vec<Anchor> {
    let mut rng = Rng::new(seed.wrapping_mul(2_654_435_761).wrapping_add(salt));
    let probability = if every.is_finite() && every >= 1.0 { 1.0 / every } else { 1.0 };
    let body: Vec<Anchor> = timeline
        .body_chars()
        .map(|item| Anchor {
            started_at: item.started_at,
            ended_at: item.ended_at,
            leading_gap: item.leading_gap.unwrap_or(0.0),
        })
        .collect();
    if body.is_empty() {
        return Vec::new();
    }
    let mut picked: Vec<Anchor> = Vec::new();
    let mut rolls: Vec<f64> = Vec::with_capacity(body.len());
    for _ in 0..body.len() {
        rolls.push(rng.next_f64());
    }
    for (index, anchor) in body.into_iter().enumerate() {
        if rolls[index] < probability {
            picked.push(anchor);
        }
    }
    if picked.is_empty() {
        // 一次都没抽中：取概率最小的那个字，保证"勾了就有"
        let best = rolls
            .iter()
            .enumerate()
            .min_by(|left, right| left.1.partial_cmp(right.1).unwrap_or(std::cmp::Ordering::Equal))
            .map(|(index, _)| index)
            .unwrap_or(0);
        let anchor = timeline
            .body_chars()
            .nth(best)
            .map(|item| Anchor {
                started_at: item.started_at,
                ended_at: item.ended_at,
                leading_gap: item.leading_gap.unwrap_or(0.0),
            });
        if let Some(anchor) = anchor {
            picked.push(anchor);
        }
    }
    picked
}

/// 重复按下（无抬起）：客户端应忽略它，用真正那次按下计时
fn dup_down(timeline: &mut Timeline, seed: u32, every: f64) -> usize {
    let picks = anchors(timeline, seed, every, 11);
    let mut added = Vec::new();
    for anchor in &picks {
        // 落在这个字前面的间隔里，且不早于上一个字
        let offset = anchor.leading_gap.min(12.0).max(2.0);
        added.push(Event { at: (anchor.started_at - offset).max(0.0), kind: Kind::Down, fault: Some("dup-down") });
    }
    let count = added.len();
    timeline.events.extend(added);
    sort_events(timeline);
    count
}

/// 缺抬起：丢掉这个字的第一次抬起，客户端要在下一次新按下时自恢复
fn missing_up(timeline: &mut Timeline, seed: u32, every: f64) -> usize {
    let picks = anchors(timeline, seed, every, 23);
    let mut doomed: Vec<usize> = Vec::new();
    for anchor in &picks {
        let found = timeline.events.iter().position(|event| {
            event.kind == Kind::Up && event.at >= anchor.started_at && event.at <= anchor.ended_at
        });
        if let Some(index) = found {
            if !doomed.contains(&index) {
                doomed.push(index);
            }
        }
    }
    doomed.sort_unstable();
    for index in doomed.iter().rev() {
        timeline.events.remove(*index);
    }
    doomed.len()
}

/// ≤10ms 抖动按压：必须被丢弃且不污染基准（useTraffic.js:64 的 duration <= 10）
fn micro_press(timeline: &mut Timeline, seed: u32, every: f64) -> usize {
    let picks = anchors(timeline, seed, every, 37);
    let mut added = Vec::new();
    for anchor in &picks {
        // 塞在这个字之前的间隔中段，不与真实按压重叠
        let room = anchor.leading_gap.max(16.0);
        let at = (anchor.started_at - room * 0.5).max(0.0);
        added.push(Event { at, kind: Kind::Down, fault: Some("micro-press") });
        added.push(Event { at: at + 6.0, kind: Kind::Up, fault: Some("micro-press") });
    }
    let count = added.len() / 2;
    timeline.events.extend(added);
    sort_events(timeline);
    count
}

/// 未知字节：既非 1/2 也不在 CODES 里，客户端应告警跳过而不静默丢帧（WebSerial.js:86-89）
fn unknown_byte(timeline: &mut Timeline, seed: u32, every: f64) -> usize {
    let picks = anchors(timeline, seed, every, 53);
    let mut added = Vec::new();
    for anchor in &picks {
        added.push(Event { at: (anchor.started_at - 1.0).max(0.0), kind: Kind::Code(7), fault: Some("unknown-byte") });
    }
    let count = added.len();
    timeline.events.extend(added);
    sort_events(timeline);
    count
}

/// 逐项注入，返回每种故障实际注入了几次（0 次也要如实报出来：
/// 例如电子键没有按下/抬起，重复按下与缺抬起在它上面本就不适用）
pub fn apply(timeline: &mut Timeline, faults: &[Fault], seed: u32, every: f64) -> Vec<(Fault, usize)> {
    let mut report = Vec::new();
    for fault in faults {
        let count = match fault {
            Fault::DupDown => dup_down(timeline, seed, every),
            Fault::MissingUp => missing_up(timeline, seed, every),
            Fault::MicroPress => micro_press(timeline, seed, every),
            Fault::UnknownByte => unknown_byte(timeline, seed, every),
        };
        report.push((*fault, count));
    }
    report
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

#[cfg(test)]
mod injection_tests {
    use super::*;
    use crate::keying::{ElectronOptions, HandOptions};

    fn page(text: &str) -> Timeline {
        crate::keying::hand_timeline(&HandOptions { text: text.into(), ..Default::default() }).unwrap()
    }

    fn micro_positions(timeline: &Timeline) -> Vec<f64> {
        timeline
            .events
            .iter()
            .filter(|event| event.fault == Some("micro-press") && event.kind == Kind::Down)
            .map(|event| event.at)
            .collect()
    }

    #[test]
    /// 故障必须撒在整页上，而不是只在页首来一次：
    /// 早先四种故障都只注入一次，100 组的页面等于没注入
    fn faults_spread_across_the_whole_page() {
        let text = (0..25).map(|_| "ABCD").collect::<Vec<_>>().join(" ");
        let mut timeline = page(&text);
        let span = timeline.duration();
        let report = apply(&mut timeline, &[Fault::MicroPress], 7, DEFAULT_EVERY);
        let count = report[0].1;
        assert!(count >= 2, "100 字一页至少该注入两次，实际 {count}");

        let positions = micro_positions(&timeline);
        assert_eq!(positions.len(), count);
        let last = positions.iter().copied().fold(0.0f64, f64::max);
        assert!(last > span * 0.5, "注入全挤在前半页：最后一次在 {last}ms / 全长 {span}ms");
    }

    #[test]
    /// 次数随页长增长：每 N 字一次意味着长页注入更多
    fn count_scales_with_page_length() {
        let short = (0..5).map(|_| "ABCD").collect::<Vec<_>>().join(" ");
        let long = (0..50).map(|_| "ABCD").collect::<Vec<_>>().join(" ");
        let mut first = page(&short);
        let mut second = page(&long);
        let few = apply(&mut first, &[Fault::UnknownByte], 3, DEFAULT_EVERY)[0].1;
        let many = apply(&mut second, &[Fault::UnknownByte], 3, DEFAULT_EVERY)[0].1;
        assert!(many > few, "长页注入次数（{many}）应多于短页（{few}）");
    }

    #[test]
    /// 同种子同结果，换种子换位置
    fn injection_is_reproducible() {
        let text = (0..25).map(|_| "ABCD").collect::<Vec<_>>().join(" ");
        let mut first = page(&text);
        let mut again = page(&text);
        let mut other = page(&text);
        apply(&mut first, &[Fault::MicroPress], 11, DEFAULT_EVERY);
        apply(&mut again, &[Fault::MicroPress], 11, DEFAULT_EVERY);
        apply(&mut other, &[Fault::MicroPress], 12, DEFAULT_EVERY);
        assert_eq!(micro_positions(&first), micro_positions(&again));
        assert_ne!(micro_positions(&first), micro_positions(&other));
    }

    #[test]
    /// 缺抬起是真的删掉抬起事件：删几次，抬起就比按下少几次
    fn missing_up_removes_real_events() {
        let text = (0..25).map(|_| "ABCD").collect::<Vec<_>>().join(" ");
        let mut timeline = page(&text);
        let downs = timeline.events.iter().filter(|event| event.kind == Kind::Down).count();
        let ups = timeline.events.iter().filter(|event| event.kind == Kind::Up).count();
        assert_eq!(downs, ups, "注入前按下与抬起应当配平");
        let removed = apply(&mut timeline, &[Fault::MissingUp], 5, DEFAULT_EVERY)[0].1;
        assert!(removed >= 2, "应当删掉多次抬起，实际 {removed}");
        let ups_after = timeline.events.iter().filter(|event| event.kind == Kind::Up).count();
        assert_eq!(ups - ups_after, removed);
    }

    #[test]
    /// 电子键没有按下/抬起：重复按下与缺抬起在它上面不适用，必须如实报 0 次
    fn electron_reports_zero_for_inapplicable_faults() {
        let mut timeline = crate::keying::electron_timeline(&ElectronOptions {
            text: "ABCD EFGH IJKL".into(),
            ..Default::default()
        })
        .unwrap();
        let report = apply(&mut timeline, &[Fault::DupDown, Fault::MissingUp, Fault::UnknownByte], 7, DEFAULT_EVERY);
        let counts: Vec<(Fault, usize)> = report;
        assert_eq!(counts[1].1, 0, "电子键上缺抬起应为 0 次");
        assert!(counts[2].1 >= 1, "未知字节在电子键上仍然适用");
    }
}
