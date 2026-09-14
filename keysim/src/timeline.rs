//! 时间轴：模拟器唯一的中间表示，所有输出通道都只消费它。
//!
//! 硬件不带时间戳（WebSerial.js:70-72 只有 JS 接收时刻），所以"何时发这一帧"必须由
//! 这里决定；一次性灌帧会让客户端把全部按压时长算错。

use crate::morse::Timing;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Kind {
    Down,
    Up,
    Code(u8),
}

#[derive(Debug, Clone, Copy)]
pub struct Event {
    pub at: f64,
    pub kind: Kind,
    /// 故障注入打的标记，仅用于日志与断言
    pub fault: Option<&'static str>,
}

/// 一个已拍发字符，payload 层据此复现 moresValue/moresTime/patLogs
#[derive(Debug, Clone)]
pub struct Char {
    pub value: String,
    pub started_at: f64,
    pub ended_at: f64,
    pub group: Option<usize>,
    pub control: bool,
    /// 手键：逐元素点(0)/划(1)
    pub codes: Vec<u8>,
    /// 手键：逐元素按压毫秒
    pub durations: Vec<f64>,
    /// 手键：符内间隔毫秒
    pub gaps: Vec<f64>,
    /// 手键：该字前导的字/组间隔（客户端把它算进本字的 patLogs）
    pub leading_gap: Option<f64>,
    /// 电子键：按下的键码序列
    pub keys: Vec<u8>,
}

impl Char {
    fn new(value: impl Into<String>, started_at: f64, ended_at: f64) -> Self {
        Char {
            value: value.into(),
            started_at,
            ended_at,
            group: None,
            control: false,
            codes: Vec::new(),
            durations: Vec::new(),
            gaps: Vec::new(),
            leading_gap: None,
            keys: Vec::new(),
        }
    }
}

#[derive(Debug, Clone)]
pub struct Timeline {
    pub key: &'static str,
    pub alphabet: String,
    pub groups: Vec<String>,
    pub events: Vec<Event>,
    pub chars: Vec<Char>,
    pub plan: Timing,
    pub jitter: f64,
}

impl Timeline {
    pub fn duration(&self) -> f64 {
        self.events.last().map(|event| event.at).unwrap_or(0.0)
    }

    pub fn body_chars(&self) -> impl Iterator<Item = &Char> {
        self.chars.iter().filter(|item| !item.control)
    }
}

/// 确定性伪随机（mulberry32），同 seed 同结果，抖动可复现
pub struct Rng(u32);

impl Rng {
    pub fn new(seed: u32) -> Self {
        Rng(if seed == 0 { 1 } else { seed })
    }

    pub fn next_f64(&mut self) -> f64 {
        self.0 = self.0.wrapping_add(0x6D2B_79F5);
        let mut t = self.0;
        t = (t ^ (t >> 15)).wrapping_mul(1 | t);
        t = t.wrapping_add((t ^ (t >> 7)).wrapping_mul(61 | t)) ^ t;
        ((t ^ (t >> 14)) as f64) / 4_294_967_296.0
    }

    pub fn pick<T: Copy>(&mut self, items: &[T]) -> T {
        items[(self.next_f64() * items.len() as f64) as usize % items.len()]
    }
}

fn round(value: f64) -> f64 {
    (value * 1000.0).round() / 1000.0
}

pub struct Builder {
    key: &'static str,
    plan: Timing,
    jitter: f64,
    rng: Rng,
    cursor: f64,
    events: Vec<Event>,
    chars: Vec<Char>,
}

impl Builder {
    pub fn new(key: &'static str, plan: Timing, jitter: f64, seed: u32) -> Self {
        Builder {
            key,
            plan,
            jitter,
            rng: Rng::new(seed),
            cursor: 0.0,
            events: Vec::new(),
            chars: Vec::new(),
        }
    }

    pub fn cursor(&self) -> f64 {
        self.cursor
    }

    fn jit(&mut self, value: f64) -> f64 {
        if self.jitter <= 0.0 {
            value
        } else {
            round(value * (1.0 + (self.rng.next_f64() * 2.0 - 1.0) * self.jitter))
        }
    }

    /// 空走一段间隔，返回实际走过的毫秒
    pub fn wait(&mut self, ms: f64) -> f64 {
        let applied = self.jit(ms);
        self.cursor = round(self.cursor + applied);
        applied
    }

    /// 精确空走（不抖动）：用于收尾静默、按目标节拍对齐
    pub fn wait_exact(&mut self, ms: f64) -> f64 {
        self.cursor = round(self.cursor + ms);
        ms
    }

    /// 一次按压：按下 + 抬起，返回实际按压毫秒
    pub fn press(&mut self, ms: f64) -> f64 {
        let duration = self.jit(ms);
        self.events.push(Event { at: round(self.cursor), kind: Kind::Down, fault: None });
        self.cursor = round(self.cursor + duration);
        self.events.push(Event { at: self.cursor, kind: Kind::Up, fault: None });
        duration
    }

    /// 一个电子键单字节码
    pub fn code(&mut self, code: u8) {
        self.events.push(Event { at: round(self.cursor), kind: Kind::Code(code), fault: None });
    }

    pub fn push_char(&mut self, mut item: Char) {
        item.started_at = round(item.started_at);
        item.ended_at = round(self.cursor);
        self.chars.push(item);
    }

    pub fn char_at(&self, value: impl Into<String>, started_at: f64) -> Char {
        Char::new(value, started_at, self.cursor)
    }

    pub fn build(mut self, alphabet: &str, groups: Vec<String>) -> Timeline {
        self.events.sort_by(|left, right| left.at.partial_cmp(&right.at).unwrap_or(std::cmp::Ordering::Equal));
        Timeline {
            key: self.key,
            alphabet: alphabet.to_string(),
            groups,
            events: self.events,
            chars: self.chars,
            plan: self.plan,
            jitter: self.jitter,
        }
    }
}

/// 手键节拍可行性校验。这些不是风格偏好，是客户端判定的硬边界：
/// - 点 ≤ 10ms 的按压被直接丢弃（useTraffic.js:64）；
/// - 试机要求划 > 点的两倍（handKeyTrain.js:360）；
/// - 抖动必须落在评分规则 skew 容差内，否则开始符校验不通过（:357-359）；
/// - 翻页会触发每页重算并把 codeGap 夹到 ≥60ms（patStandard.js:41-43），此后字间隔须
///   > 60×(1+skew/100)、组间隔须 > 60×(3+skew/100)，否则翻页后再也编译不出字码。
pub fn check_hand_plan(plan: &Timing, skew: f64, jitter: f64, page_turns: bool) -> Result<(), String> {
    let mut problems: Vec<String> = Vec::new();
    let low = 1.0 - jitter;
    let high = 1.0 + jitter;
    if plan.dot * low <= 10.0 {
        problems.push(format!("点时长 {:.1}ms ≤ 10ms，会被 useTraffic.js:64 丢弃", plan.dot * low));
    }
    if plan.dash * low <= plan.dot * high * 2.0 {
        problems.push(format!(
            "划 {:.1}ms 未超过点 {:.1}ms 的两倍，开始符校验必失败",
            plan.dash * low,
            plan.dot * high
        ));
    }
    if jitter * 100.0 > skew {
        problems.push(format!("抖动 ±{}% 超过规则容差 ±{}%，开始符校验必失败", jitter * 100.0, skew));
    }
    let clamped = if page_turns { plan.gap.max(60.0) } else { plan.gap };
    if plan.word <= clamped * (1.0 + skew / 100.0) {
        problems.push(format!(
            "字间隔 {:.1}ms 未超过 codeGap 阈值 {:.1}ms，无法成字",
            plan.word,
            clamped * (1.0 + skew / 100.0)
        ));
    }
    if plan.group <= clamped * (3.0 + skew / 100.0) {
        problems.push(format!(
            "组间隔 {:.1}ms 未超过组阈值 {:.1}ms，无法成组",
            plan.group,
            clamped * (3.0 + skew / 100.0)
        ));
    }
    if problems.is_empty() {
        Ok(())
    } else {
        Err(format!("手键节拍不可用：\n- {}", problems.join("\n- ")))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    /// 同种子同结果，不同种子不同结果
    fn jitter_is_reproducible() {
        let mut first = Rng::new(11);
        let mut second = Rng::new(11);
        let mut other = Rng::new(12);
        let a: Vec<f64> = (0..5).map(|_| first.next_f64()).collect();
        let b: Vec<f64> = (0..5).map(|_| second.next_f64()).collect();
        let c: Vec<f64> = (0..5).map(|_| other.next_f64()).collect();
        assert_eq!(a, b);
        assert_ne!(a, c);
    }
}
