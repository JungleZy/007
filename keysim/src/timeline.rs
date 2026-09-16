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

/// 拍发风格。
///
/// `Machine` 是原来的口径：每个点、每个划、每段间隔都精确等于节拍表，
/// 抖动（如果开了）是均匀白噪声——纸带上一眼就能看出是机器发的。
///
/// `Human` 模拟一个真人的"手感"：
/// - 系统性偏差：这个人的点偏短、划偏长、符内间隔偏宽，整页一致（同种子同手感）；
/// - 慢漂移：两条不同周期的正弦叠加，对应开局热身变快、后段疲劳变慢；
/// - 逐符小噪声：截断高斯，不是均匀分布；
/// - 组首犹豫：偶尔在组间隔上多停一下。
///
/// 三项之和被钳在 ±jitter 的信封内，所以客户端的硬边界（划 > 点的两倍、
/// 点 > 10ms、抖动 ≤ 规则偏移）仍由 `check_hand_plan` 一处保证，不需要另算一套。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Style {
    Machine,
    Human,
}

impl Style {
    pub fn parse(name: &str) -> Option<Style> {
        match name {
            "machine" => Some(Style::Machine),
            "human" => Some(Style::Human),
            _ => None,
        }
    }

    /// 真人手感在没指定抖动时的默认信封：±12%
    /// （低于 0.2 的硬上界，也低于默认规则偏移 51%）
    pub const HUMAN_DEFAULT_JITTER: f64 = 0.12;
}

/// 节拍里的一个槽位：真人对不同槽位的偏差是不一样的
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Slot {
    Dot,
    Dash,
    /// 符内间隔
    Gap,
    /// 字间隔
    Word,
    /// 组间隔
    Group,
}

/// 一个人的手感。全部由种子导出，同种子同手感。
#[derive(Debug, Clone, Copy)]
struct Fist {
    bias_dot: f64,
    bias_dash: f64,
    bias_gap: f64,
    bias_word: f64,
    bias_group: f64,
    drift: f64,
    periods: (f64, f64),
    phases: (f64, f64),
    noise: f64,
    hesitate: f64,
}

impl Fist {
    fn derive(rng: &mut Rng) -> Fist {
        let spread = |rng: &mut Rng| rng.next_f64() * 2.0 - 1.0;
        Fist {
            // 系统性偏差占信封的三分之一：这是"谁在发"的部分，整页不变
            bias_dot: spread(rng) * 0.34,
            bias_dash: spread(rng) * 0.34,
            bias_gap: spread(rng) * 0.40,
            bias_word: spread(rng) * 0.30,
            bias_group: spread(rng) * 0.30,
            // 慢漂移占三分之一：热身与疲劳
            drift: 0.34,
            periods: (7000.0 + rng.next_f64() * 6000.0, 19000.0 + rng.next_f64() * 14000.0),
            phases: (rng.next_f64() * std::f64::consts::TAU, rng.next_f64() * std::f64::consts::TAU),
            // 逐符噪声占三分之一
            noise: 0.32,
            hesitate: 0.09,
        }
    }

    fn bias(&self, slot: Slot) -> f64 {
        match slot {
            Slot::Dot => self.bias_dot,
            Slot::Dash => self.bias_dash,
            Slot::Gap => self.bias_gap,
            Slot::Word => self.bias_word,
            Slot::Group => self.bias_group,
        }
    }

    /// 两条正弦叠加，取值落在 [-1,1]
    fn wander(&self, at: f64) -> f64 {
        let first = (at / self.periods.0 * std::f64::consts::TAU + self.phases.0).sin();
        let second = (at / self.periods.1 * std::f64::consts::TAU + self.phases.1).sin();
        (first + second) / 2.0
    }
}

pub struct Builder {
    key: &'static str,
    plan: Timing,
    jitter: f64,
    /// 有手感就是真人风格；机械风格没有手感对象
    fist: Option<Fist>,
    rng: Rng,
    cursor: f64,
    events: Vec<Event>,
    chars: Vec<Char>,
}

impl Builder {
    pub fn new(key: &'static str, plan: Timing, jitter: f64, seed: u32) -> Self {
        Builder::styled(key, plan, jitter, seed, Style::Machine)
    }

    pub fn styled(key: &'static str, plan: Timing, jitter: f64, seed: u32, style: Style) -> Self {
        let mut rng = Rng::new(seed);
        let fist = match style {
            Style::Human => Some(Fist::derive(&mut rng)),
            Style::Machine => None,
        };
        Builder { key, plan, jitter, fist, rng, cursor: 0.0, events: Vec::new(), chars: Vec::new() }
    }

    pub fn cursor(&self) -> f64 {
        self.cursor
    }

    /// 机械风格：均匀白噪声。真人风格：系统偏差 + 慢漂移 + 截断高斯，和钳在信封内。
    fn deviation(&mut self, slot: Option<Slot>) -> f64 {
        if self.jitter <= 0.0 {
            return 0.0;
        }
        match (self.fist, slot) {
            (Some(fist), Some(slot)) => {
                let at = self.cursor;
                // 截断高斯：两次均匀取样相加再折半，落在 [-1,1]，中间密两头疏
                let noise = self.rng.next_f64() + self.rng.next_f64() - 1.0;
                let total = fist.bias(slot) + fist.drift * fist.wander(at) + fist.noise * noise;
                total.clamp(-1.0, 1.0) * self.jitter
            }
            _ => (self.rng.next_f64() * 2.0 - 1.0) * self.jitter,
        }
    }

    fn jit(&mut self, value: f64, slot: Option<Slot>) -> f64 {
        if self.jitter <= 0.0 {
            value
        } else {
            round(value * (1.0 + self.deviation(slot)))
        }
    }

    /// 空走一段间隔，返回实际走过的毫秒（不带槽位信息，真人模型退化为白噪声）
    pub fn wait(&mut self, ms: f64) -> f64 {
        let applied = self.jit(ms, None);
        self.cursor = round(self.cursor + applied);
        applied
    }

    /// 空走一个已知槽位的间隔：真人在组首偶尔会多停一下
    pub fn wait_slot(&mut self, slot: Slot) -> f64 {
        let nominal = match slot {
            Slot::Dot => self.plan.dot,
            Slot::Dash => self.plan.dash,
            Slot::Gap => self.plan.gap,
            Slot::Word => self.plan.word,
            Slot::Group => self.plan.group,
        };
        let mut applied = self.jit(nominal, Some(slot));
        if let Some(fist) = self.fist {
            // 犹豫只加长组间隔：客户端按"大于阈值"判组，长一点只会更清楚
            if slot == Slot::Group && self.rng.next_f64() < fist.hesitate {
                applied = round(applied * (1.0 + self.rng.next_f64() * 0.6));
            }
        }
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
        self.press_slot(ms, None)
    }

    /// 一次点或划：真人的点与划各有自己的系统性偏差
    pub fn press_element(&mut self, dash: bool) -> f64 {
        let nominal = if dash { self.plan.dash } else { self.plan.dot };
        self.press_slot(nominal, Some(if dash { Slot::Dash } else { Slot::Dot }))
    }

    fn press_slot(&mut self, ms: f64, slot: Option<Slot>) -> f64 {
        let duration = self.jit(ms, slot);
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
/// - 每次成组（不只翻页）都会触发 pageResetPatStandard 重算并把 codeGap 夹到 ≥60ms
///   （handKeyTrain.js:553-557 → patStandard.js:41-43；2026-09-16 数码报 e2e 实测：
///   120 字/分时第一组之后组间隔 184ms < 60×3.51=210.6ms，再也编译不出组）。
///   所以夹值无条件生效：字间隔须 > 60×(1+skew/100)、组间隔须 > 60×(3+skew/100)。
pub fn check_hand_plan(plan: &Timing, skew: f64, jitter: f64) -> Result<(), String> {
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
    let clamped = plan.gap.max(60.0);
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

    /// 归一化偏差序列：真实时长 / 名义时长 - 1
    fn deviations(style: Style, jitter: f64, slot: Slot) -> Vec<f64> {
        let plan = crate::morse::timing(70.0, "characters", "letter", crate::morse::Ratio::default(), false).unwrap();
        let mut builder = Builder::styled("hand", plan, jitter, 7, style);
        let nominal = match slot {
            Slot::Dot => plan.dot,
            Slot::Dash => plan.dash,
            Slot::Gap => plan.gap,
            Slot::Word => plan.word,
            Slot::Group => plan.group,
        };
        (0..300)
            .map(|_| match slot {
                Slot::Dot => builder.press_element(false),
                Slot::Dash => builder.press_element(true),
                _ => builder.wait_slot(slot),
            })
            .map(|actual| actual / nominal - 1.0)
            .collect()
    }

    fn mean(values: &[f64]) -> f64 {
        values.iter().sum::<f64>() / values.len() as f64
    }

    /// 滞后 1 自相关：白噪声约为 0，慢漂移会明显为正
    fn autocorrelation(values: &[f64]) -> f64 {
        let center = mean(values);
        let variance: f64 = values.iter().map(|value| (value - center).powi(2)).sum();
        let covariance: f64 = values
            .windows(2)
            .map(|pair| (pair[0] - center) * (pair[1] - center))
            .sum();
        if variance <= f64::EPSILON {
            0.0
        } else {
            covariance / variance
        }
    }

    #[test]
    /// 真人手感不能越出信封：客户端的"划 > 点两倍""点 > 10ms"全靠这条守住
    fn human_style_stays_inside_the_envelope() {
        for slot in [Slot::Dot, Slot::Dash, Slot::Gap, Slot::Word] {
            for value in deviations(Style::Human, 0.12, slot) {
                assert!(value.abs() <= 0.12 + 1e-9, "{slot:?} 偏差 {value} 越出 ±12% 信封");
            }
        }
    }

    #[test]
    /// 真人手感必须真的"像人"：相邻符之间有慢漂移的相关性，且整页带系统性偏差；
    /// 机械风格的白噪声两项都没有——这正是"一看就像机器发的"的根源
    fn human_style_differs_from_white_noise() {
        let human = deviations(Style::Human, 0.12, Slot::Dot);
        let machine = deviations(Style::Machine, 0.12, Slot::Dot);
        let human_corr = autocorrelation(&human);
        let machine_corr = autocorrelation(&machine);
        assert!(human_corr > 0.25, "真人应有慢漂移（相邻相关 {human_corr}）");
        assert!(machine_corr.abs() < 0.2, "机械风格应是白噪声（相邻相关 {machine_corr}）");
        assert!(mean(&human).abs() > 0.01, "真人整页应有系统性偏差，实际均值 {}", mean(&human));
        assert!(mean(&machine).abs() < 0.02, "机械风格不该有系统偏差，实际 {}", mean(&machine));
    }

    #[test]
    /// 点与划的手感是各自独立的：不同人身上两者的偏差差值明显不同，
    /// 不能退化成"同一个偏差乘到所有槽位"
    fn dot_and_dash_biases_are_independent() {
        let plan = crate::morse::timing(70.0, "characters", "letter", crate::morse::Ratio::default(), false).unwrap();
        let mut gaps = Vec::new();
        for seed in 1..=8u32 {
            let dot: f64 = {
                let mut builder = Builder::styled("hand", plan, 0.12, seed, Style::Human);
                let values: Vec<f64> = (0..120).map(|_| builder.press_element(false) / plan.dot - 1.0).collect();
                mean(&values)
            };
            let dash: f64 = {
                let mut builder = Builder::styled("hand", plan, 0.12, seed, Style::Human);
                let values: Vec<f64> = (0..120).map(|_| builder.press_element(true) / plan.dash - 1.0).collect();
                mean(&values)
            };
            gaps.push((dot - dash).abs());
        }
        let widest = gaps.iter().copied().fold(0.0f64, f64::max);
        assert!(widest > 0.02, "八个人里没有一个点划手感明显不同：最大差 {widest}");
    }

    #[test]
    /// 同种子同一个人：手感可复现，换种子换人
    fn fist_is_reproducible_per_seed() {
        let first = deviations(Style::Human, 0.12, Slot::Dot);
        let again = deviations(Style::Human, 0.12, Slot::Dot);
        assert_eq!(first, again);
        let plan = crate::morse::timing(70.0, "characters", "letter", crate::morse::Ratio::default(), false).unwrap();
        let other: Vec<f64> = {
            let mut builder = Builder::styled("hand", plan, 0.12, 99, Style::Human);
            (0..40).map(|_| builder.press_element(false) / plan.dot - 1.0).collect()
        };
        assert_ne!(first[..40].to_vec(), other, "换种子应当换一个人");
    }

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
