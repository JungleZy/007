//! 电码表与节拍模型。
//!
//! 口径来源（对表测试 tests/morse_drift.rs 会逐项核对，漂移即失败）：
//! - 点划表：bw-frontend/frontend/src/common/mixin/useMorse.js 的 morseCode
//! - 电子键键位：.../electronKeyZuXun/train/student/js/keyCode.js 的 codeInKey / codeOnKey
//! - 手键控制符：.../handkeyZuXun/train/student/js/handKeyTrain.js:41-48 的 initSymbol
//! - 节拍模型：.../common/utils/voice/MorseVoiceHighPerformance.js 的 calculateTiming
//!
//! forwardTable（播报侧用 0=点 1=划 2=符内间隔 表示）不另存一份：它就是点划串中间
//! 插入 2，本文件按需派生，避免两份数据各自漂移。

use std::collections::HashMap;
use std::sync::LazyLock;

pub const ALPHABETS: [&str; 4] = ["letter", "short", "long", "mix"];

const LETTERS: [(char, &str); 26] = [
    ('A', "01"), ('B', "1000"), ('C', "1010"), ('D', "100"), ('E', "0"),
    ('F', "0010"), ('G', "110"), ('H', "0000"), ('I', "00"), ('J', "0111"),
    ('K', "101"), ('L', "0100"), ('M', "11"), ('N', "10"), ('O', "111"),
    ('P', "0110"), ('Q', "1101"), ('R', "010"), ('S', "000"), ('T', "1"),
    ('U', "001"), ('V', "0001"), ('W', "011"), ('X', "1001"), ('Y', "1011"),
    ('Z', "1100"),
];

const SHORT_DIGITS: [(char, &str); 10] = [
    ('0', "1"), ('1', "01"), ('2', "001"), ('3', "00011"), ('4', "00001"),
    ('5', "00000"), ('6', "10000"), ('7', "11000"), ('8', "100"), ('9', "10"),
];

const LONG_DIGITS: [(char, &str); 10] = [
    ('0', "11111"), ('1', "01111"), ('2', "00111"), ('3', "00011"), ('4', "00001"),
    ('5', "00000"), ('6', "10000"), ('7', "11000"), ('8', "11100"), ('9', "11110"),
];

/// 手键控制符，取值同 handKeyTrain.js:41-48 的 initSymbol。
/// turn 是三个独立字符（原值 "00,00,00" 以逗号分字）。
pub const CONTROL_START: &str = "10001";
pub const CONTROL_END: &str = "01010";
pub const CONTROL_TURN: [&str; 3] = ["00", "00", "00"];
pub const CONTROL_ALTER: &str = "001100";
pub const CONTROL_NEXT: &str = "001011";

/// 电子键键位：码 -> (按键名, F1/F3 直出文本, 已成形的点划组)
pub const ELECTRON_KEYS: [(u8, &str, &str, &str); 20] = [
    (11, "control", "控制", ""),
    (12, "F1", "F1", ""),
    (13, "F2", "F2", ""),
    (14, "F3", "F3", ""),
    (15, "F4", "F4", ""),
    (21, "1", "1", "0"),
    (22, "2", "2", "00"),
    (23, "3", "3", "1"),
    (24, "4", "4", "10"),
    (25, "5", "5", "01"),
    (31, "6", "6", "0"),
    (32, "7", "7", "00"),
    (33, "8", "8", "1"),
    (34, "9", "9", "10"),
    (35, "0", "0", "01"),
    (41, "enter", "", ""),
    (42, "?", "?", "000"),
    (43, "FM", "/", "11"),
    (44, "start", "开始", "101"),
    (45, "period", "句号", "011"),
];

pub const FN_F1: u8 = 12;
pub const FN_F2: u8 = 13;
pub const FN_F3: u8 = 14;
pub const FN_ENTER: u8 = 41;
pub const FN_START: u8 = 44;
pub const FN_PERIOD: u8 = 45;

/// 电子键 F2 装配表：点划串 -> 字母（keyCode.js 的 codeOnKey）。
/// 它只收字母与开始符，数码必须走 F1 直出 —— 数码的点划串在这里会还原成字母。
pub fn code_on_key(elements: &str) -> Option<char> {
    for (character, code) in LETTERS {
        if code == elements {
            return Some(character);
        }
    }
    None
}

static TABLES: LazyLock<HashMap<&'static str, HashMap<char, &'static str>>> = LazyLock::new(|| {
    let mut all = HashMap::new();
    all.insert("letter", LETTERS.iter().copied().collect::<HashMap<_, _>>());
    all.insert("short", SHORT_DIGITS.iter().copied().collect());
    all.insert("long", LONG_DIGITS.iter().copied().collect());
    let mut mixed: HashMap<char, &'static str> = LETTERS.iter().copied().collect();
    mixed.extend(LONG_DIGITS.iter().copied());
    all.insert("mix", mixed);
    all
});

/// 某字母表下的点划表
pub fn alphabet(name: &str) -> Option<&'static HashMap<char, &'static str>> {
    TABLES.get(name)
}

/// 字符 -> 点划串
pub fn elements_of(alphabet_name: &str, character: char) -> Result<&'static str, String> {
    alphabet(alphabet_name)
        .ok_or_else(|| format!("未知字母表 {alphabet_name}"))?
        .get(&character)
        .copied()
        .ok_or_else(|| format!("字母表 {alphabet_name} 里没有字符 {character}"))
}

/// 点划串 -> 字符（该字母表内唯一反查；客户端查不到时落 '#'）
pub fn char_of(alphabet_name: &str, elements: &str) -> Option<char> {
    alphabet(alphabet_name)?
        .iter()
        .find(|(_, code)| **code == elements)
        .map(|(character, _)| *character)
}

/// 该字母表下按字符排序的可拍字符集（随机报文用）
pub fn characters(alphabet_name: &str) -> Result<Vec<char>, String> {
    let mut list: Vec<char> = alphabet(alphabet_name)
        .ok_or_else(|| format!("未知字母表 {alphabet_name}"))?
        .keys()
        .copied()
        .collect();
    list.sort_unstable();
    Ok(list)
}

// ------------------------------------------------------------------ 节拍模型

/// 各段配比，同 MorseVoiceHighPerformance.js 的 DEFAULT_RATIO
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Ratio {
    pub dot: f64,
    pub dash: f64,
    pub gap: f64,
    pub word: f64,
    pub suite: f64,
    pub leaf: f64,
}

impl Default for Ratio {
    fn default() -> Self {
        Ratio { dot: 1.0, dash: 3.0, gap: 1.0, word: 3.0, suite: 5.0, leaf: 7.0 }
    }
}

/// 校准页：400 字符，四字一组循环该字母表（CALIBRATION_TEXT 的构造方式）
fn calibration_counts(alphabet_name: &str) -> Result<[f64; 6], String> {
    // 组间隔/字间隔的固定基数与 JS 一致：counts 起始 [0,0,0,300,100,0]
    let mut counts = [0.0f64, 0.0, 0.0, 300.0, 100.0, 0.0];
    let set: Vec<char> = match alphabet_name {
        "letter" => ('A'..='Z').collect(),
        "mix" => ('A'..='Z').chain('0'..='9').collect(),
        _ => ('0'..='9').collect(),
    };
    let table = alphabet(alphabet_name).ok_or_else(|| format!("未知字母表 {alphabet_name}"))?;
    for index in 0..400usize {
        let character = set[index % set.len()];
        let code = table
            .get(&character)
            .ok_or_else(|| format!("字母表 {alphabet_name} 缺字符 {character}"))?;
        // 点划 + 符内间隔：符内间隔数 = 元素数 - 1
        for element in code.chars() {
            counts[if element == '1' { 1 } else { 0 }] += 1.0;
        }
        counts[2] += (code.chars().count() - 1) as f64;
    }
    Ok(counts)
}

/// 一次拍发的节拍（毫秒）
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Timing {
    pub criterion: f64,
    pub ratio: Ratio,
    pub dot: f64,
    pub dash: f64,
    pub gap: f64,
    pub word: f64,
    pub group: f64,
    pub page: f64,
}

impl Timing {
    /// 按倍率缩放基准时长，比例不变。用于按"实测码率"口径反算节拍。
    pub fn scaled(&self, factor: f64) -> Timing {
        build(self.criterion * factor, self.ratio)
    }
}

/// 由码率推出基准时长，公式与 calculateTiming 一致。
/// unit: "characters" | "groups" | "wpm"；low_rate 对应低速模式（点划钉在 35 字符/分，只拉长间隔）。
pub fn timing(rate: f64, unit: &str, alphabet_name: &str, ratio: Ratio, low_rate: bool) -> Result<Timing, String> {
    if !(rate.is_finite() && rate > 0.0) {
        return Err("码率必须大于零".into());
    }
    let mut resolved = ratio;
    for value in [resolved.dot, resolved.dash, resolved.gap, resolved.word, resolved.suite, resolved.leaf] {
        if !(value.is_finite() && value > 0.0) {
            return Err("节拍配比必须为正数".into());
        }
    }
    if unit == "wpm" && !low_rate {
        let criterion = 1200.0 / rate;
        return Ok(build(criterion, resolved));
    }
    if !matches!(unit, "characters" | "groups" | "wpm") {
        return Err(format!("未知码率单位 {unit}"));
    }
    let characters_per_minute = if unit == "groups" { rate * 4.0 } else { rate };
    if low_rate && characters_per_minute > 35.0 {
        return Err("低速模式平均速度不得超过35字符/分".into());
    }
    let counts = calibration_counts(alphabet_name)?;
    let keys = [resolved.dot, resolved.dash, resolved.gap, resolved.word, resolved.suite, resolved.leaf];
    let units: f64 = counts.iter().zip(keys).map(|(count, weight)| count * weight).sum();
    let effective = if low_rate { 35.0 } else { characters_per_minute };
    let criterion = 400.0 * 60000.0 / (effective * units);
    if low_rate {
        let symbol_units: f64 = counts[..3].iter().zip(&keys[..3]).map(|(count, weight)| count * weight).sum();
        let stretch = (400.0 * 60000.0 / characters_per_minute / criterion - symbol_units) / (units - symbol_units);
        resolved.word *= stretch;
        resolved.suite *= stretch;
        resolved.leaf *= stretch;
    }
    Ok(build(criterion, resolved))
}

fn build(criterion: f64, ratio: Ratio) -> Timing {
    Timing {
        criterion,
        ratio,
        dot: criterion * ratio.dot,
        dash: criterion * ratio.dash,
        gap: criterion * ratio.gap,
        word: criterion * ratio.word,
        group: criterion * ratio.suite,
        page: criterion * ratio.leaf,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    /// 组/分与字符/分是同一码率的两种单位
    fn groups_and_characters_are_same_rate() {
        let by_groups = timing(30.0, "groups", "short", Ratio::default(), false).unwrap();
        let by_characters = timing(120.0, "characters", "short", Ratio::default(), false).unwrap();
        assert!((by_groups.criterion - by_characters.criterion).abs() < 1e-9);
    }

    #[test]
    /// 整页时长等于按码率应耗的总时长，且不随点划比变化
    fn page_duration_matches_rate() {
        // 与前端 morseTiming.test.mjs 同一条不变量：400 字符 / 码率 = 整页毫秒
        for dash in [3.0, 4.0] {
            let ratio = Ratio { dash, ..Ratio::default() };
            let plan = timing(100.0, "characters", "short", ratio, false).unwrap();
            let counts = calibration_counts("short").unwrap();
            let keys = [plan.ratio.dot, plan.ratio.dash, plan.ratio.gap, plan.ratio.word, plan.ratio.suite, plan.ratio.leaf];
            let total: f64 = counts.iter().zip(keys).map(|(count, weight)| count * weight * plan.criterion).sum();
            assert!((total - 400.0 / 100.0 * 60000.0).abs() < 1e-6, "点划比 1:{dash} 时整页 {total}ms");
        }
    }

    #[test]
    /// 低速模式只拉长间隔，不动点划本身
    fn low_rate_only_stretches_gaps() {
        let low = timing(20.0, "characters", "short", Ratio::default(), true).unwrap();
        let capped = timing(35.0, "characters", "short", Ratio::default(), false).unwrap();
        assert!((low.criterion - capped.criterion).abs() < 1e-9, "低速下点划仍按 35 字符/分");
        assert_eq!(low.ratio.dot, 1.0);
        assert_eq!(low.ratio.dash, 3.0);
        assert!(low.ratio.word > 3.0, "间隔必须被拉长");
        assert!(timing(36.0, "characters", "short", Ratio::default(), true).is_err());
    }

    #[test]
    /// 字母表正反查与随机字符集自洽
    fn alphabet_lookups_are_consistent() {
        assert_eq!(elements_of("letter", 'A').unwrap(), "01");
        assert_eq!(char_of("letter", "1100"), Some('Z'));
        assert_eq!(characters("short").unwrap().len(), 10);
        assert_eq!(characters("mix").unwrap().len(), 36);
        // 数码的点划串在电子键装配表里会还原成字母，因此数码必须走 F1 直出
        assert_ne!(code_on_key(elements_of("short", '0').unwrap()), Some('0'));
        assert_eq!(code_on_key("1100"), Some('Z'));
    }
}
