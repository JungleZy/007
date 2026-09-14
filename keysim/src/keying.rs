//! 手键与电子键的拍发模型。
//!
//! 手键（直键）：设备只送按下/抬起两种边沿，点划由客户端按按压时长判定
//! （useTraffic.js:63-68），阈值现场从开始符 '10001' 校准（handKeyTrain.js:335-366），
//! 所以时间轴必须先发一遍合格的开始符。
//!
//! 电子键（双手电键盘）：设备送的是**已成形的点划组**单字节码。
//! 右手键（31-35/42-45）起头入缓冲，左手键（21-25）收尾并立即成字
//! （electronKeyZuXun 学生端 handKeyTrain.js:315-347）。只有"单笔成字"才依赖
//! 800ms 静默超时提交，而后续任意数据键或 41/14 都会立刻提交挂起的缓冲，
//! 所以整页只有最后一个字可能付这 800ms。

use crate::morse::{self, Ratio, Timing};
use crate::timeline::{check_hand_plan, Builder, Slot, Style, Timeline};

/// 页尾单笔字的提交超时，取值同 handKeyTrain.js:39 的 pauseDuration
pub const PAUSE_DURATION: f64 = 800.0;

#[derive(Debug, Clone)]
pub struct HandOptions {
    pub text: String,
    pub alphabet: String,
    pub rate: f64,
    pub unit: String,
    pub skew: f64,
    pub jitter: f64,
    pub seed: u32,
    pub preamble: bool,
    /// "turn" 翻页符 | "end" 结束符 | "none"
    pub tail: String,
    /// 单页训练不经历"翻页后 codeGap 夹到 60ms"，可用更高码率
    pub single_page: bool,
    pub low_rate: bool,
    /// "machine" 机械等长 | "human" 真人手感
    pub style: String,
}

impl Default for HandOptions {
    fn default() -> Self {
        HandOptions {
            text: String::new(),
            alphabet: "letter".into(),
            rate: 70.0,
            unit: "characters".into(),
            skew: 51.0,
            jitter: 0.0,
            seed: 1,
            preamble: true,
            tail: "turn".into(),
            single_page: true,
            low_rate: false,
            style: "machine".into(),
        }
    }
}

#[derive(Debug, Clone)]
pub struct ElectronOptions {
    pub text: String,
    pub alphabet: String,
    /// 单位是"组/分"，与客户端显示口径及服务端重算口径一致
    pub rate: f64,
    pub stroke_gap: f64,
    pub group_size: usize,
    pub jitter: f64,
    pub seed: u32,
    pub preamble: bool,
    /// "page" 句号提交本页 | "end" F3+回车 | "none"
    pub tail: String,
    /// "machine" 机械等长 | "human" 真人手感
    pub style: String,
}

impl Default for ElectronOptions {
    fn default() -> Self {
        ElectronOptions {
            text: String::new(),
            alphabet: "letter".into(),
            rate: 20.0,
            stroke_gap: 30.0,
            group_size: 4,
            jitter: 0.0,
            seed: 1,
            preamble: true,
            tail: "page".into(),
            style: "machine".into(),
        }
    }
}

pub fn split_groups(text: &str, group_size: usize) -> Vec<String> {
    let trimmed = text.trim();
    if trimmed.is_empty() {
        return Vec::new();
    }
    if trimmed.split_whitespace().count() > 1 || trimmed.contains(char::is_whitespace) {
        return trimmed.split_whitespace().map(str::to_string).collect();
    }
    trimmed
        .chars()
        .collect::<Vec<_>>()
        .chunks(group_size.max(1))
        .map(|chunk| chunk.iter().collect())
        .collect()
}

/// 报文校验：必须是空格分隔的组，且每个字符都在该字母表里
pub fn validate(text: &str, alphabet: &str) -> Result<Vec<String>, String> {
    let groups = split_groups(text, 4);
    if groups.is_empty() {
        return Err("报文为空".into());
    }
    let table = morse::alphabet(alphabet).ok_or_else(|| format!("未知字母表 {alphabet}"))?;
    let mut unknown: Vec<char> = groups
        .iter()
        .flat_map(|group| group.chars())
        .filter(|character| !table.contains_key(character))
        .collect();
    unknown.sort_unstable();
    unknown.dedup();
    if !unknown.is_empty() {
        let list: String = unknown.iter().map(|c| c.to_string()).collect::<Vec<_>>().join(" ");
        return Err(format!("字母表 {alphabet} 里没有这些字符：{list}"));
    }
    Ok(groups)
}

/// 风格与有效抖动信封只在这里算一次：真人手感在没指定抖动时用 ±12%，
/// 之后所有可行性校验都按这个信封做，不另开一套判定。
fn resolve_style(name: &str, jitter: f64) -> Result<(Style, f64), String> {
    let style = Style::parse(name).ok_or_else(|| format!("未知拍发风格 {name}（可选 machine / human）"))?;
    let envelope = match (style, jitter > 0.0) {
        (Style::Human, false) => Style::HUMAN_DEFAULT_JITTER,
        _ => jitter,
    };
    Ok((style, envelope))
}

pub fn hand_plan(options: &HandOptions) -> Result<Timing, String> {
    let (_, envelope) = resolve_style(&options.style, options.jitter)?;
    let plan = morse::timing(options.rate, &options.unit, &options.alphabet, Ratio::default(), options.low_rate)?;
    check_hand_plan(&plan, options.skew, envelope, !options.single_page)?;
    Ok(plan)
}

/// 手键时间轴：开始符校准 + 正文 + 收尾符 + 末字静默
pub fn hand_timeline(options: &HandOptions) -> Result<Timeline, String> {
    let groups = validate(&options.text, &options.alphabet)?;
    let plan = hand_plan(options)?;
    let (style, envelope) = resolve_style(&options.style, options.jitter)?;
    let mut builder = Builder::styled("hand", plan, envelope, options.seed, style);
    let mut leading_gap: Option<f64> = None;

    if options.preamble {
        emit_hand_char(&mut builder, "开始", morse::CONTROL_START, None, true, &mut leading_gap);
        // 开始符校验在第 5 次抬起即触发，但正文首字仍要一个组间隔把它与开始符分开
        leading_gap = Some(builder.wait_slot(Slot::Group));
    }

    let group_count = groups.len();
    for (group_index, group) in groups.iter().enumerate() {
        let characters: Vec<char> = group.chars().collect();
        for (char_index, character) in characters.iter().enumerate() {
            let elements = morse::elements_of(&options.alphabet, *character)?;
            emit_hand_char(
                &mut builder,
                &character.to_string(),
                elements,
                Some(group_index),
                false,
                &mut leading_gap,
            );
            if char_index + 1 < characters.len() {
                leading_gap = Some(builder.wait_slot(Slot::Word));
            }
        }
        if group_index + 1 < group_count {
            leading_gap = Some(builder.wait_slot(Slot::Group));
        }
    }

    match options.tail.as_str() {
        "turn" => {
            leading_gap = Some(builder.wait(plan.group));
            for (index, elements) in morse::CONTROL_TURN.iter().enumerate() {
                emit_hand_char(&mut builder, "翻页", elements, None, true, &mut leading_gap);
                if index + 1 < morse::CONTROL_TURN.len() {
                    leading_gap = Some(builder.wait_slot(Slot::Word));
                }
            }
        }
        "end" => {
            leading_gap = Some(builder.wait_slot(Slot::Group));
            emit_hand_char(&mut builder, "结束", morse::CONTROL_END, None, true, &mut leading_gap);
        }
        _ => {}
    }

    // 末字要靠客户端的静默定时器（handKeyTrain.js:309-324）编译，实时回放必须留足这段静默
    builder.wait_exact(plan.group);
    Ok(builder.build(&options.alphabet, groups))
}

fn emit_hand_char(
    builder: &mut Builder,
    value: &str,
    elements: &str,
    group: Option<usize>,
    control: bool,
    leading_gap: &mut Option<f64>,
) {
    let started_at = builder.cursor();
    let mut codes = Vec::new();
    let mut durations = Vec::new();
    let mut gaps = Vec::new();
    let symbols: Vec<char> = elements.chars().collect();
    for (index, element) in symbols.iter().enumerate() {
        let dash = *element == '1';
        durations.push(builder.press_element(dash));
        codes.push(if dash { 1 } else { 0 });
        if index + 1 < symbols.len() {
            gaps.push(builder.wait_slot(Slot::Gap));
        }
    }
    let mut item = builder.char_at(value, started_at);
    item.codes = codes;
    item.durations = durations;
    item.gaps = gaps;
    item.group = group;
    item.control = control;
    item.leading_gap = leading_gap.take();
    builder.push_char(item);
}

/// 点划串 -> 键码序列。优先两笔（右手起头 + 左手收尾，立即成字），
/// 只能单笔时返回一个键码（提交要靠后续键或 800ms 超时）。
pub fn chord_of(elements: &str) -> Result<Vec<u8>, String> {
    let right: Vec<(u8, &str)> = morse::ELECTRON_KEYS
        .iter()
        .filter(|(code, _, _, group)| *code >= 30 && !group.is_empty())
        .map(|(code, _, _, group)| (*code, *group))
        .collect();
    let left: Vec<(u8, &str)> = morse::ELECTRON_KEYS
        .iter()
        .filter(|(code, _, _, group)| *code > 20 && *code < 30 && !group.is_empty())
        .map(|(code, _, _, group)| (*code, *group))
        .collect();

    for split in 1..elements.len() {
        let head = &elements[..split];
        let tail = &elements[split..];
        if let (Some((first, _)), Some((second, _))) = (
            right.iter().find(|(_, group)| *group == head),
            left.iter().find(|(_, group)| *group == tail),
        ) {
            return Ok(vec![*first, *second]);
        }
    }
    if let Some((single, _)) = right.iter().find(|(_, group)| *group == elements) {
        return Ok(vec![*single]);
    }
    Err(format!("点划串 {elements} 无法用电子键键位拍出"))
}

/// 该字符能否走 F2 装配：codeOnKey 只收字母，数码必须 F1 直出
fn chordable(alphabet: &str, character: char) -> bool {
    match morse::elements_of(alphabet, character) {
        Ok(elements) => morse::code_on_key(elements) == Some(character) && chord_of(elements).is_ok(),
        Err(_) => false,
    }
}

fn direct_key(character: char) -> Option<u8> {
    morse::ELECTRON_KEYS
        .iter()
        .find(|(code, _, text, _)| *code > 20 && *code <= 35 && *text == character.to_string())
        .map(|(code, _, _, _)| *code)
}

/// 电子键时间轴：F1 开始键 → 正文（字母走 F2 组合、数码走 F1 直出）→ 收尾
pub fn electron_timeline(options: &ElectronOptions) -> Result<Timeline, String> {
    let groups = validate(&options.text, &options.alphabet)?;
    if !(options.rate.is_finite() && options.rate > 0.0) {
        return Err("码率必须大于零".into());
    }
    let per_group = 60000.0 / options.rate;
    let per_char = per_group / options.group_size as f64;
    if per_char <= options.stroke_gap * 2.0 {
        return Err(format!(
            "码率 {} 组/分 只给每字 {:.1}ms，装不下两笔（每笔间隔 {}ms）",
            options.rate, per_char, options.stroke_gap
        ));
    }
    // 电子键不按点划时长判定，这里只借 Timing 携带节拍参数
    let plan = Timing {
        criterion: per_char,
        ratio: Ratio::default(),
        dot: 0.0,
        dash: 0.0,
        gap: options.stroke_gap,
        word: per_char,
        group: per_group,
        page: per_group,
    };
    let (style, envelope) = resolve_style(&options.style, options.jitter)?;
    let mut builder = Builder::styled("electron", plan, envelope, options.seed, style);
    let mut mode: Option<u8> = None;

    let press = |builder: &mut Builder, code: u8| {
        builder.code(code);
        builder.wait_slot(Slot::Gap);
    };

    if options.preamble {
        press(&mut builder, morse::FN_F1);
        mode = Some(morse::FN_F1);
        let started_at = builder.cursor();
        press(&mut builder, morse::FN_START);
        let mut item = builder.char_at("开始", started_at);
        item.control = true;
        item.keys = vec![morse::FN_START];
        builder.push_char(item);
    }

    let mut target = builder.cursor();
    let group_count = groups.len();
    for (group_index, group) in groups.iter().enumerate() {
        for character in group.chars() {
            target += per_char;
            let started_at = builder.cursor();
            let mut keys = Vec::new();
            if chordable(&options.alphabet, character) {
                if mode != Some(morse::FN_F2) {
                    press(&mut builder, morse::FN_F2);
                    mode = Some(morse::FN_F2);
                }
                keys.extend(chord_of(morse::elements_of(&options.alphabet, character)?)?);
            } else {
                let code = direct_key(character)
                    .ok_or_else(|| format!("字符 {character} 既不能 F2 装配也没有 F1 直出键位"))?;
                if mode != Some(morse::FN_F1) {
                    press(&mut builder, morse::FN_F1);
                    mode = Some(morse::FN_F1);
                }
                keys.push(code);
            }
            for code in &keys {
                press(&mut builder, *code);
            }
            let mut item = builder.char_at(character.to_string(), started_at);
            item.group = Some(group_index);
            item.keys = keys;
            builder.push_char(item);
            if target > builder.cursor() {
                let remaining = target - builder.cursor();
                builder.wait_exact(remaining);
            }
        }
        // 回车推进到下一组报底槽位（handKeyTrain.js:277-296）
        press(&mut builder, morse::FN_ENTER);
        if group_index + 1 < group_count && target > builder.cursor() {
            let remaining = target - builder.cursor();
            builder.wait_exact(remaining);
        }
    }

    match options.tail.as_str() {
        "page" => {
            if mode != Some(morse::FN_F1) {
                press(&mut builder, morse::FN_F1);
            }
            press(&mut builder, morse::FN_PERIOD);
        }
        "end" => {
            press(&mut builder, morse::FN_F3);
            press(&mut builder, morse::FN_ENTER);
        }
        _ => {
            // 页尾单笔字只能等 800ms 超时提交
            builder.wait_exact(PAUSE_DURATION + options.stroke_gap);
        }
    }

    Ok(builder.build(&options.alphabet, groups))
}

/// 随机报文：按字母表取字符、按组切分
pub fn random_message(alphabet: &str, groups: usize, group_size: usize, seed: Option<u32>) -> Result<String, String> {
    let characters = morse::characters(alphabet)?;
    let mut rng = crate::timeline::Rng::new(seed.unwrap_or_else(|| {
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|value| value.subsec_nanos() ^ value.as_secs() as u32)
            .unwrap_or(1)
    }));
    let mut out = Vec::with_capacity(groups);
    for _ in 0..groups.max(1) {
        let mut group = String::with_capacity(group_size);
        for _ in 0..group_size.max(1) {
            group.push(rng.pick(&characters));
        }
        out.push(group);
    }
    Ok(out.join(" "))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::timeline::Kind;

    #[test]
    /// 26 个字母都能拍出，键位拼出的点划串经装配表还原回原字母
    fn every_letter_is_chordable() {
        for (character, code) in crate::morse::ELECTRON_KEYS.iter().map(|(c, _, _, g)| (c, g)) {
            let _ = (character, code);
        }
        for character in 'A'..='Z' {
            let elements = morse::elements_of("letter", character).unwrap();
            let codes = chord_of(elements).unwrap();
            let joined: String = codes
                .iter()
                .map(|code| {
                    morse::ELECTRON_KEYS
                        .iter()
                        .find(|(candidate, _, _, _)| candidate == code)
                        .map(|(_, _, _, group)| *group)
                        .unwrap_or("")
                })
                .collect();
            assert_eq!(joined, elements, "{character} 的键位拼不出点划串");
            assert_eq!(morse::code_on_key(&joined), Some(character), "{character} 还原失败");
            assert!(codes[0] >= 30, "{character} 第一笔必须是右手键");
            if codes.len() > 1 {
                assert!(codes[1] < 30, "{character} 第二笔必须是左手键");
            }
        }
    }

    #[test]
    /// 只有单元素字母才是单笔——单笔才需要等 800ms
    fn only_single_element_letters_need_timeout() {
        let single: Vec<char> = ('A'..='Z')
            .filter(|character| chord_of(morse::elements_of("letter", *character).unwrap()).unwrap().len() == 1)
            .collect();
        assert_eq!(single, vec!['E', 'T']);
    }

    #[test]
    /// 数码走 F1 直出，且模式键不重复切换
    fn digits_use_direct_keys() {
        let options = ElectronOptions {
            text: "1234 5678".into(),
            alphabet: "short".into(),
            ..Default::default()
        };
        let timeline = electron_timeline(&options).unwrap();
        let mode_keys: Vec<u8> = timeline
            .events
            .iter()
            .filter_map(|event| match event.kind {
                Kind::Code(code) if code == morse::FN_F1 || code == morse::FN_F2 => Some(code),
                _ => None,
            })
            .collect();
        assert_eq!(mode_keys, vec![morse::FN_F1], "数码页不应切到 F2");
        for item in timeline.body_chars() {
            assert_eq!(item.keys.len(), 1, "{} 应是 F1 单键直出", item.value);
        }
    }

    #[test]
    /// 开始符必须能过试机：点划间隔齐整，且划 > 点的两倍
    fn preamble_passes_calibration() {
        // 抖动上界：3(1-j) > 2(1+j) → j < 0.2，所以 0.2 本身不可用（见下一个测试）
        for jitter in [0.0, 0.15] {
            let options = HandOptions { text: "A".into(), jitter, seed: 7, ..Default::default() };
            let timeline = hand_timeline(&options).unwrap();
            let preamble = timeline.chars.iter().find(|item| item.value == "开始").unwrap();
            let dots: Vec<f64> = preamble
                .codes
                .iter()
                .zip(&preamble.durations)
                .filter(|(code, _)| **code == 0)
                .map(|(_, duration)| *duration)
                .collect();
            let dashes: Vec<f64> = preamble
                .codes
                .iter()
                .zip(&preamble.durations)
                .filter(|(code, _)| **code == 1)
                .map(|(_, duration)| *duration)
                .collect();
            let mean = |values: &[f64]| values.iter().sum::<f64>() / values.len() as f64;
            let skew = 51.0;
            for (name, values) in [("点", &dots), ("划", &dashes), ("间隔", &preamble.gaps)] {
                let center = mean(values);
                let min = values.iter().copied().fold(f64::MAX, f64::min);
                let max = values.iter().copied().fold(f64::MIN, f64::max);
                assert!(min >= center * (1.0 - skew / 100.0), "{name} 偏低越界（抖动 {jitter}）");
                assert!(max <= center * (1.0 + skew / 100.0), "{name} 偏高越界（抖动 {jitter}）");
            }
            assert!(mean(&dashes) > mean(&dots) * 2.0, "划中位数必须超过点的两倍");
        }
    }

    #[test]
    /// 抖动超过规则容差时直接拒绝，而不是产出注定失败的时间轴
    fn excessive_jitter_is_rejected() {
        let options = HandOptions { text: "A".into(), jitter: 0.6, skew: 50.0, ..Default::default() };
        assert!(hand_timeline(&options).unwrap_err().contains("抖动"));
        // 边界：抖动 ≥0.2 时"划的最短"不再超过"点的最长"的两倍，客户端试机必失败
        let boundary = HandOptions { text: "A".into(), jitter: 0.2, skew: 99.0, ..Default::default() };
        let error = hand_timeline(&boundary).expect_err("抖动 0.2 必须被拒");
        assert!(error.contains("两倍"), "{error}");
        assert!(hand_timeline(&HandOptions { jitter: 0.19, ..boundary }).is_ok(), "0.19 应当可用");
    }

    #[test]
    /// 报文按字母表校验：字码表里没有数字
    fn message_is_validated_against_alphabet() {
        assert!(validate("ABCD EFGH", "letter").is_ok());
        let error = validate("ABCD 1234", "letter").unwrap_err();
        assert!(error.contains("没有这些字符"), "{error}");
        assert!(validate("   ", "letter").is_err());
    }

    #[test]
    /// 随机报文形状正确且同种子可复现
    fn random_message_is_reproducible() {
        let first = random_message("letter", 5, 4, Some(3)).unwrap();
        let second = random_message("letter", 5, 4, Some(3)).unwrap();
        assert_eq!(first, second);
        assert_eq!(first.split(' ').count(), 5);
        assert!(first.split(' ').all(|group| group.len() == 4 && group.chars().all(|c| c.is_ascii_uppercase())));
        let digits = random_message("short", 3, 4, Some(3)).unwrap();
        assert!(digits.chars().all(|c| c.is_ascii_digit() || c == ' '));
    }
}
