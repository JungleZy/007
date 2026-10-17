use crate::domain::radiotelephone::post_military::*;
use serde_json::Map;
use serde_json::Value;
use std::collections::HashSet;

type Case = (
    &'static str,
    &'static [&'static str],
    Option<(bool, &'static [&'static str])>,
);

/// 期望值由 Java `PostMilitaryTermTrainService.checkKeyword` 实跑得到（确定性规则；含边界与异常）。
#[test]
fn check_keyword_matches_java_outputs() {
    let cases: &[Case] = &[
        (
            "由喷火手和单兵火箭手4～6人混合编成",
            &["由喷火手和单兵火箭手4～6人混合编成"],
            Some((
                true,
                &[
                    "由喷火手和单兵火箭手4～6人混合编成",
                    "由喷火手和单兵火箭手5～7人混合编成",
                ],
            )),
        ),
        (
            "装备2～3具",
            &["装备2～3具"],
            Some((true, &["装备2～3具", "装备3～4具"])),
        ),
        (
            "共10～20人",
            &["共10～20人"],
            Some((true, &["共10～20人", "共20～30人"])),
        ),
        (
            "数量9～99",
            &["数量9～99"],
            Some((true, &["数量9～99", "数量10～109"])),
        ),
        (
            "12345～6",
            &["12345～6"],
            Some((true, &["12345～6", "22345～7"])),
        ),
        (
            "a1～2～3",
            &["a1～2～3"],
            Some((true, &["a1～2～3", "a2～3～3"])),
        ),
        ("～1～2", &["～1～2"], None),
        (
            "无线电台",
            &["无线电台"],
            Some((true, &["无线电台", "有线电台"])),
        ),
        (
            "无线无线",
            &["无线无线"],
            Some((true, &["无线无线", "有线无线"])),
        ),
        (
            "出口通道",
            &["出口通道"],
            Some((true, &["出口通道", "入口通道"])),
        ),
        (
            "入口通道",
            &["入口通道"],
            Some((true, &["入口通道", "出口通道"])),
        ),
        (
            "出口入口",
            &["出口入口"],
            Some((true, &["出口入口", "入口入口"])),
        ),
        (
            "干线传输",
            &["干线传输"],
            Some((true, &["干线传输", "主线传输"])),
        ),
        (
            "每小时三小时",
            &["每小时三小时"],
            Some((true, &["每小时三小时", "每分钟三分钟"])),
        ),
        (
            "线状天线",
            &["线状天线"],
            Some((true, &["线状天线", "面状天线"])),
        ),
        (
            "面状天线",
            &["面状天线"],
            Some((true, &["面状天线", "线状天线"])),
        ),
        (
            "线状面状",
            &["线状面状"],
            Some((true, &["线状面状", "面状面状"])),
        ),
        ("接收机", &["接收机"], Some((true, &["接收机", "发射机"]))),
        ("发射机", &["发射机"], Some((true, &["发射机", "接收机"]))),
        (
            "接收发射",
            &["接收发射"],
            Some((true, &["接收发射", "发射发射"])),
        ),
        ("战术级", &["战术级"], Some((true, &["战术级", "战役级"]))),
        ("战役级", &["战役级"], Some((true, &["战役级", "战术级"]))),
        (
            "战术战役",
            &["战术战役"],
            Some((true, &["战术战役", "战役战役"])),
        ),
        (
            "甲、乙、丙、丁",
            &["甲、乙、丙、丁"],
            Some((true, &["甲、乙、丙、丁", "甲、丙、丁"])),
        ),
        (
            "甲、乙、丙",
            &["甲、乙、丙"],
            Some((false, &["甲、乙、丙"])),
        ),
        (
            "甲、乙、丙、丁、戊、",
            &["甲、乙、丙、丁、戊、"],
            Some((true, &["甲、乙、丙、丁、戊、", "甲、丙、丁、戊"])),
        ),
        ("、、、、", &["、、、、"], Some((false, &["、、、、"]))),
        (
            "甲、乙、丙、丁、",
            &["甲、乙、丙、丁、"],
            Some((true, &["甲、乙、丙、丁、", "甲、丙、丁"])),
        ),
        ("一二三", &["一二三"], Some((false, &["一二三"]))),
        (
            "无线\n电台",
            &["无线\n电台"],
            Some((true, &["无线\n电台", "有线\n电台"])),
        ),
        ("3～4\n", &["3～4\n"], Some((false, &["3～4\n"]))),
        (
            "6～7无线",
            &["6～7无线"],
            Some((true, &["6～7无线", "7～8无线"])),
        ),
        ("99999999999～1", &["99999999999～1"], None),
        ("1～99999999999", &["1～99999999999"], None),
        (
            "x2147483647～1",
            &["x2147483647～1"],
            Some((true, &["x2147483647～1", "x2247483647～1"])),
        ),
        (
            "2147483647～5",
            &["2147483647～5"],
            Some((true, &["2147483647～5", "-1147483649～6"])),
        ),
        (
            "a、b、c、d1无线",
            &["a、b、c、d1无线"],
            Some((true, &["a、b、c、d1无线", "a、b、c、d1有线"])),
        ),
        ("行李", &["行李"], Some((false, &["行李"]))),
        ("", &[""], Some((false, &[""]))),
        (
            "小时无线干线",
            &["小时无线干线"],
            Some((true, &["小时无线干线", "小时有线干线"])),
        ),
        (
            "出口2～3",
            &["出口2～3"],
            Some((true, &["出口2～3", "出口3～4"])),
        ),
        (
            "😀😀😀1～2",
            &["😀😀😀1～2"],
            Some((true, &["😀😀😀1～2", "😀😀😀2～3"])),
        ),
        (
            "，，，，，1～2",
            &["，，，，，1～2"],
            Some((true, &["，，，，，1～2", "，，，，，2～3"])),
        ),
        (
            "无线电台",
            &["无线电台", "有线电台"],
            Some((false, &["无线电台", "有线电台"])),
        ),
        (
            "无线电台出口",
            &["无线电台出口", "有线电台出口"],
            Some((true, &["无线电台出口", "有线电台出口", "无线电台入口"])),
        ),
        (
            "线状天线",
            &["线状天线", "面状天线"],
            Some((false, &["线状天线", "面状天线"])),
        ),
    ];
    let mut rng = rand::thread_rng();
    for (answer, start, expected) in cases {
        let mut options = start
            .iter()
            .map(|value| (*value).to_owned())
            .collect::<Vec<_>>();
        let actual = check_keyword(answer, &mut options, &mut rng);
        match expected {
            None => assert_eq!(actual, Err(PaperError::Fault), "answer {answer:?}"),
            Some((flag, expected_options)) => {
                assert_eq!(actual, Ok(*flag), "answer {answer:?}");
                assert_eq!(&options, expected_options, "answer {answer:?}");
            }
        }
    }
}

/// 最后一条规则（改一个数字）是随机的：只校验换掉了第一个数字、其余字符不变。
#[test]
fn check_keyword_digit_rule_changes_first_number_only() {
    let mut rng = rand::thread_rng();
    for _ in 0..50 {
        let mut options = vec!["约 5 ～ 8个".to_owned()];
        assert_eq!(
            check_keyword("约 5 ～ 8个", &mut options, &mut rng),
            Ok(true)
        );
        assert_eq!(options.len(), 2);
        let variant = &options[1];
        assert!(
            variant.starts_with("约 ") && variant.ends_with(" ～ 8个"),
            "{variant}"
        );
        assert_ne!(variant, "约 5 ～ 8个");
    }
}

#[test]
fn make_paper_always_yields_four_distinct_options_with_correct_letter() {
    let category = ["线路", "无线电台", "出口通道", "主线", "2～3具"]
        .iter()
        .enumerate()
        .map(|(index, value)| CategoryTerm {
            key: format!("题{index}"),
            value: (*value).to_owned(),
        })
        .collect::<Vec<_>>();
    let mut rng = rand::thread_rng();
    for _ in 0..200 {
        let (title, options, correct) = make_paper("t", &category, &mut rng).unwrap();
        let options: Map<String, Value> = serde_json::from_str(&options).unwrap();
        assert_eq!(options.len(), 4, "{options:?}");
        let values = options.values().collect::<HashSet<_>>();
        assert_eq!(values.len(), 4);
        let index = category.iter().position(|term| term.key == title).unwrap();
        assert_eq!(
            options[&correct],
            Value::String(category[index].value.clone())
        );
    }
}

#[test]
fn make_paper_rejects_fewer_than_four_distinct_values_with_type_prefix() {
    let category = ["甲", "甲", "乙", "", "丙"]
        .iter()
        .map(|value| CategoryTerm {
            key: "k".to_owned(),
            value: (*value).to_owned(),
        })
        .collect::<Vec<_>>();
    let mut rng = rand::thread_rng();
    assert_eq!(
        make_paper("T9", &category, &mut rng),
        Err(PaperError::Business(
            "类型 T9 有效题目不足4条，无法生成干扰项".to_owned()
        ))
    );
}
