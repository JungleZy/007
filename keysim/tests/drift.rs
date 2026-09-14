//! 对表测试：keysim 的电码表、键位表、帧协议、节拍公式必须与仓内**生产代码**一致。
//!
//! 这些值不是 keysim 自己定的口径，而是从前端生产代码派生的。前端一旦改动而
//! keysim 没跟上，这里立刻失败——模拟器就不会悄悄按过期口径造数据。
//!
//! 源文件（相对仓库根）：
//! - bw-frontend/frontend/src/common/mixin/useMorse.js                     点划表
//! - bw-frontend/frontend/src/common/utils/WebSerial.js                    串口帧协议与电子键码集
//! - bw-frontend/frontend/src/common/mixin/useTraffic.js                   手键点划判定与重复按下窗口
//! - bw-frontend/frontend/src/common/utils/voice/MorseVoiceHighPerformance.js  节拍配比与校准页
//! - .../electronKeyZuXun/train/student/js/keyCode.js                      电子键键位与装配表
//! - .../handkeyZuXun/train/student/js/handKeyTrain.js                     控制符与提交超时

use std::collections::HashMap;
use std::path::PathBuf;

use keysim::{faults, keying, morse, sinks};

fn repo(path: &str) -> String {
    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("..");
    let full = root.join(path);
    std::fs::read_to_string(&full).unwrap_or_else(|error| panic!("读不到仓内源文件 {}：{error}", full.display()))
}

fn frontend(path: &str) -> String {
    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("..");
    let full = root.join("bw-frontend/frontend/src").join(path);
    std::fs::read_to_string(&full).unwrap_or_else(|error| panic!("读不到前端源文件 {}：{error}", full.display()))
}

/// 从 useMorse.js 的 morseCode 里取某字母表的 字符 -> 点划串。
/// 前端同一文件里还有一张"点划 -> 字符"反查表，所以只认第一段（带 value: 的那段），
/// 并按花括号配平取块，不依赖缩进。
fn js_morse_table(alphabet: &str) -> HashMap<char, String> {
    let source = frontend("common/mixin/useMorse.js");
    let anchor = [format!("'{alphabet}': {{"), format!("'{alphabet}':{{")]
        .iter()
        .filter_map(|needle| source.find(needle))
        .min()
        .unwrap_or_else(|| panic!("useMorse.js 里没有 {alphabet} 表"));
    let open = anchor + source[anchor..].find('{').expect("表必须有花括号");
    let mut depth = 0usize;
    let mut close = open;
    for (offset, character) in source[open..].char_indices() {
        match character {
            '{' => depth += 1,
            '}' => {
                depth -= 1;
                if depth == 0 {
                    close = open + offset;
                    break;
                }
            }
            _ => {}
        }
    }
    let mut table = HashMap::new();
    for line in source[open..close].lines() {
        let Some((key, rest)) = line.split_once(':') else { continue };
        let key = key.trim().trim_matches(|c| c == '\'' || c == '"');
        let Some(value_start) = rest.find("value: '") else { continue };
        let value = &rest[value_start + 8..];
        let Some(value_end) = value.find('\'') else { continue };
        let mut characters = key.chars();
        if let (Some(character), None) = (characters.next(), characters.next()) {
            table.insert(character, value[..value_end].to_string());
        }
    }
    assert!(!table.is_empty(), "{alphabet} 表解析为空，useMorse.js 结构可能变了");
    table
}

#[test]
/// 四张点划表逐字符对齐前端 morseCode
fn morse_tables_match_frontend() {
    for name in morse::ALPHABETS {
        let expected = js_morse_table(name);
        let actual = morse::alphabet(name).unwrap_or_else(|| panic!("keysim 缺 {name} 表"));
        assert_eq!(actual.len(), expected.len(), "{name} 表字符数不一致");
        for (character, elements) in &expected {
            let mine = actual
                .get(character)
                .unwrap_or_else(|| panic!("{name} 表缺字符 {character}"));
            assert_eq!(mine, elements, "{name} 表 {character} 的点划串漂移");
        }
    }
}

#[test]
/// 电子键单字节码集必须与 WebSerial.js 的 CODES 完全一致：
/// 多一个会被客户端当电子键码错解，少一个会导致该键拍不出去
fn electron_codes_match_web_serial() {
    let source = frontend("common/utils/WebSerial.js");
    let start = source.find("const CODES = [").expect("WebSerial.js 里没有 CODES");
    let body = &source[start..];
    let end = body.find(']').expect("CODES 未闭合");
    let mut expected: Vec<u8> = body[..end]
        .split(|c: char| !c.is_ascii_digit())
        .filter(|token| !token.is_empty())
        .filter_map(|token| token.parse().ok())
        .collect();
    expected.retain(|code| *code >= 11);
    expected.sort_unstable();

    let mut mine: Vec<u8> = morse::ELECTRON_KEYS.iter().map(|(code, _, _, _)| *code).collect();
    mine.sort_unstable();
    assert_eq!(mine, expected, "电子键码集与 WebSerial.js 的 CODES 漂移");
}

#[test]
/// 帧协议：按下 2 字节、抬起 3 字节、电子键码 1 字节（WebSerial.handleData）
fn frame_lengths_match_web_serial() {
    let source = frontend("common/utils/WebSerial.js");
    assert!(source.contains("code === 1"), "WebSerial.js 的按下分支变了");
    assert_eq!(sinks::DOWN.len(), 2, "按下帧必须 2 字节");
    assert_eq!(sinks::UP.len(), 3, "抬起帧必须 3 字节");
    assert_eq!(sinks::DOWN[0], 1, "按下帧首字节必须是 1");
    assert_eq!(sinks::UP[0], 2, "抬起帧首字节必须是 2");

    // 首字节落在 CODES 内的是电子键单字节帧
    let timeline = keying::electron_timeline(&keying::ElectronOptions {
        text: "AB".into(),
        preamble: false,
        tail: "none".into(),
        ..Default::default()
    })
    .expect("电子键时间轴应能生成");
    for chunk in sinks::to_bytes(&timeline) {
        assert_eq!(chunk.bytes.len(), 1, "电子键帧必须是 1 字节：{:?}", chunk.bytes);
        assert!((11..=45).contains(&chunk.bytes[0]), "电子键码 {} 超出 CODES 范围", chunk.bytes[0]);
    }
}

#[test]
/// 手键重复按下容忍窗口取自 useTraffic.js 的 max(2000, lineLimit*8)
fn duplicate_down_window_matches_use_traffic() {
    let source = frontend("common/mixin/useTraffic.js");
    assert!(
        source.contains("Math.max(2000"),
        "useTraffic.js 的重复按下窗口变了，faults.rs 的 DUP_DOWN_WINDOW 需同步"
    );
    assert_eq!(faults::DUP_DOWN_WINDOW, 2000.0);
}

#[test]
/// ≤10ms 的按压会被 useTraffic.js 丢弃：故障注入必须造出这种帧
fn micro_press_threshold_matches_use_traffic() {
    let source = frontend("common/mixin/useTraffic.js");
    assert!(source.contains("duration <= 10"), "useTraffic.js 的 10ms 丢弃阈值变了");

    let mut timeline = keying::hand_timeline(&keying::HandOptions {
        text: "ABCD".into(),
        ..Default::default()
    })
    .expect("手键时间轴应能生成");
    faults::apply(&mut timeline, &[faults::Fault::MicroPress], 7, faults::DEFAULT_EVERY);
    let mut shortest = f64::MAX;
    let mut down: Option<f64> = None;
    for event in &timeline.events {
        match event.kind {
            keysim::timeline::Kind::Down => down = Some(event.at),
            keysim::timeline::Kind::Up => {
                if let Some(start) = down.take() {
                    shortest = shortest.min(event.at - start);
                }
            }
            keysim::timeline::Kind::Code(_) => {}
        }
    }
    assert!(shortest <= 10.0, "microPress 没造出 ≤10ms 的按压，最短 {shortest}ms");
}

#[test]
/// 节拍配比与校准页规模取自 MorseVoiceHighPerformance.js，码率换算才对得上
fn timing_ratio_matches_voice_module() {
    let source = frontend("common/utils/voice/MorseVoiceHighPerformance.js");
    assert!(
        source.contains("{dot: 1, dash: 3, gap: 1, word: 3, suite: 5, leaf: 7}"),
        "DEFAULT_RATIO 变了，morse.rs 的 Ratio::default 需同步"
    );
    assert!(source.contains("i < 400"), "校准页不再是 400 字符，criterion 公式需同步");
    let ratio = morse::Ratio::default();
    assert_eq!(
        [ratio.dot, ratio.dash, ratio.gap, ratio.word, ratio.suite, ratio.leaf],
        [1.0, 3.0, 1.0, 3.0, 5.0, 7.0]
    );

    // wpm 口径：criterion = 1200/rate（MorseVoiceHighPerformance.js:171）
    let plan = morse::timing(20.0, "wpm", "letter", ratio, false).expect("wpm 应可换算");
    assert!((plan.criterion - 60.0).abs() < 1e-9, "wpm criterion 漂移：{}", plan.criterion);
}

#[test]
/// 电子键键位与装配表取自 keyCode.js：右手起头 + 左手收尾，装配后必须还原成字母
fn electron_key_map_matches_key_code_js() {
    let source = frontend("views/manage/organization/electronKeyZuXun/train/student/js/keyCode.js");
    for (code, key, _, elements) in morse::ELECTRON_KEYS {
        assert!(
            source.contains(&format!("code: '{code}'")),
            "keyCode.js 里没有键码 {code}（keysim 按键 {key} 用它）"
        );
        if !elements.is_empty() {
            let rendered = format!("[{}]", elements.chars().map(|c| c.to_string()).collect::<Vec<_>>().join(","));
            assert!(
                source.contains(&rendered) || source.contains(&format!("_code: {rendered}")),
                "keyCode.js 里键码 {code} 的 _code 与 keysim 的 {rendered} 不一致"
            );
        }
    }
    // 装配：点划串必须能还原成字符（codeOnKey）
    assert_eq!(morse::code_on_key("01"), Some('A'));
    assert_eq!(morse::code_on_key("1100"), Some('Z'));
}

#[test]
/// 控制符与末字提交超时取自 handKeyTrain.js（开始符、结束符、翻页符、800ms）
fn control_symbols_match_hand_key_train() {
    let hand = frontend("views/manage/organization/handkeyZuXun/train/student/js/handKeyTrain.js");
    assert!(hand.contains("'10001'"), "手键开始符不再是 10001");
    let electron = frontend("views/manage/organization/electronKeyZuXun/train/student/js/handKeyTrain.js");
    assert!(
        electron.contains("pauseDuration = ref(800)"),
        "末字提交超时不再是 800ms，keying::PAUSE_DURATION 需同步"
    );
    assert!(hand.contains("end: '01010'"), "手键结束符不再是 01010");
    assert!(hand.contains("alter: '001100'") || hand.contains("'001100'"), "更正符不再是 001100");
    assert_eq!(morse::CONTROL_START, "10001");
    assert_eq!(morse::CONTROL_END, "01010");
    assert_eq!(keying::PAUSE_DURATION, 800.0);
}

#[test]
/// 码率口径必须与仓内三处实现一致：客户端显示、服务端结算、服务端单位换算。
/// 任何一处改了公式（比如换成 1200/rate 或不再四舍五入），这里立刻失败。
fn rate_formula_matches_client_and_server() {
    // 客户端：字符数 × 60000 / 采集区间（handKeyTrain.js:142）
    let client = frontend("views/manage/organization/handkeyZuXun/train/student/js/handKeyTrain.js");
    assert!(
        client.contains("patNumber.value * 60000 / elapsed"),
        "客户端码率公式变了，keysim 的 measured_rate 需同步"
    );

    // 服务端：ScoreMath.rate(count, totalTimeMillis)，分子 60000，四舍五入到整数
    let math = repo("backend/src/main/java/com/nip/common/utils/ScoreMath.java");
    assert!(math.contains("MILLIS_PER_MINUTE"), "ScoreMath 不再用分钟毫秒常量");
    assert!(math.contains("RoundingMode.HALF_UP"), "服务端码率不再四舍五入");
    assert!(
        math.contains("divide(new BigDecimal(totalTimeMillis), 0, RoundingMode.HALF_UP)"),
        "服务端码率的除法口径变了"
    );

    // 单位换算：字/分 ×1，组/分 ×4
    let unit = repo("backend/src/main/java/com/nip/dto/score/TrainingRateUnit.java");
    assert!(unit.contains("CHARACTERS_PER_MINUTE(1)"), "字/分的换算系数变了");
    assert!(unit.contains("FOUR_CHARACTER_GROUPS_PER_MINUTE(4)"), "组/分的换算系数变了");

    // keysim 自己：同样的公式，同样的分母系数
    let plan = keying::HandOptions { text: "ABCD EFGH".into(), rate: 70.0, ..Default::default() };
    let timeline = keying::hand_timeline(&plan).expect("时间轴应能生成");
    let window = keying::capture_window(&timeline);
    let characters = timeline.body_chars().count() as f64;
    let expected = characters * 60000.0 / window;
    assert!((keying::measured_rate(&timeline, 1.0) - expected).abs() < 1e-9);
    assert!((keying::measured_rate(&timeline, 4.0) - expected / 4.0).abs() < 1e-9);
}

#[test]
/// 客户端的每页基准重算：字间隔 = codeGap×3、组间隔 = codeGap×5（patStandard.js 写死）。
/// keysim 的比例表必须给出同样的关系，否则翻页后客户端再也编译不出字码。
fn page_standard_ratios_match_pat_standard_js() {
    let source = frontend("views/manage/organization/handkeyZuXun/train/student/js/patStandard.js");
    assert!(source.contains("codeGap * 3"), "字间隔不再是 codeGap×3");
    assert!(source.contains("codeGap * 5"), "组间隔不再是 codeGap×5");
    assert!(source.contains("codeGap = 60"), "codeGap 的 60ms 下限没了");

    let ratio = morse::Ratio::default();
    assert_eq!(ratio.word / ratio.gap, 3.0, "字间隔/符内间隔应为 3");
    assert_eq!(ratio.suite / ratio.gap, 5.0, "组间隔/符内间隔应为 5");
}
