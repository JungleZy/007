use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::{collections::HashMap, io::Write, ops::Range, sync::LazyLock};

#[derive(Deserialize)]
struct RawBook {
    keyboard: Keyboard,
    pinyin: Pinyin,
    wubi: Wubi,
}
#[derive(Deserialize)]
struct Keyboard {
    #[serde(rename = "inputKeys")]
    input_keys: HashMap<char, String>,
}
#[derive(Deserialize)]
struct Pinyin {
    syllables: Vec<String>,
    characters: Vec<String>,
}
#[derive(Deserialize)]
struct Wubi {
    font: String,
    code: String,
}
struct Book {
    keyboard: HashMap<char, String>,
    pinyin_index: HashMap<char, usize>,
    pinyin_codes: Vec<String>,
    wubi_index: HashMap<char, Range<usize>>,
    wubi_codes: String,
}

static BOOK: LazyLock<Book> = LazyLock::new(|| {
    let raw: RawBook = serde_json::from_str(include_str!(
        "../../../../bw-frontend/frontend/src/common/utils/entering-codebook.json"
    ))
    .expect("checked-in entering codebook must be valid");
    assert_eq!(raw.pinyin.characters.len(), raw.pinyin.syllables.len());
    let mut pinyin_index = HashMap::new();
    for (index, fonts) in raw.pinyin.characters.iter().enumerate() {
        for font in fonts.chars() {
            pinyin_index.entry(font).or_insert(index);
        }
    }
    let mut wubi_index = HashMap::new();
    let mut offset = 0;
    let mut codes = raw.wubi.code.split(';');
    for font in raw.wubi.font.split(';') {
        let code = codes.next().expect("wubi codebook columns must align");
        let mut characters = font.chars();
        if let Some(character) = characters.next() {
            if characters.next().is_none() && character.len_utf16() == 1 {
                wubi_index.insert(character, offset..offset + code.len());
            }
        }
        offset += code.len() + 1;
    }
    assert!(codes.next().is_none());
    Book {
        keyboard: raw.keyboard.input_keys,
        pinyin_index,
        pinyin_codes: raw.pinyin.syllables,
        wubi_index,
        wubi_codes: raw.wubi.code,
    }
});

#[derive(Deserialize, Serialize)]
pub(super) struct SourceRow {
    font: String,
    code: String,
}

pub(super) struct Capture {
    pub content: String,
    pub correct: i32,
    pub errors: i32,
}

fn array(content: Option<&str>) -> Result<Vec<Value>, String> {
    content
        .and_then(|value| serde_json::from_str(value).ok())
        .ok_or_else(|| "录入内容必须为数组".to_owned())
}

fn text(value: Value) -> Result<String, String> {
    match value {
        Value::String(value) => Ok(value),
        _ => Err("录入内容必须为字符串".to_owned()),
    }
}

fn append_code(out: &mut String, font: char, wubi: bool) -> Result<(), String> {
    if let Some(value) = BOOK.keyboard.get(&font) {
        out.push_str(value);
    } else if u32::from(font) <= 255 {
        out.push(font);
    } else if wubi {
        let range = BOOK
            .wubi_index
            .get(&font)
            .ok_or_else(|| format!("五笔词库包含无码字符：{font}"))?;
        out.push_str(&BOOK.wubi_codes[range.clone()]);
    } else {
        let index = BOOK
            .pinyin_index
            .get(&font)
            .ok_or_else(|| format!("拼音词库包含无码字符：{font}"))?;
        out.push_str(&BOOK.pinyin_codes[*index]);
    }
    Ok(())
}

fn source_row(font: String, code: String) -> Result<SourceRow, String> {
    if font.is_empty() || code.is_empty() {
        return Err("词库条目缺少文字或码串".to_owned());
    }
    Ok(SourceRow { font, code })
}

pub(super) fn freeze(kind: i32, stock: Option<&str>) -> Result<Vec<SourceRow>, String> {
    if !matches!(kind, 0..=4 | 9) {
        return Err("个人录入训练类型无效".to_owned());
    }
    let source = array(stock)?;
    let mut rows = Vec::with_capacity(source.len());
    for item in source {
        if kind == 0 {
            let Value::Object(mut object) = item else {
                return Err("同字异音词库无效".to_owned());
            };
            rows.push(source_row(
                text(object.remove("font").unwrap_or(Value::Null))?,
                text(object.remove("pys").unwrap_or(Value::Null))?,
            )?);
        } else {
            let font = text(item)?;
            if kind == 4 {
                for character in font.chars() {
                    let mut code = String::new();
                    append_code(&mut code, character, false)?;
                    rows.push(source_row(character.to_string(), code)?);
                }
            } else {
                let mut code = String::new();
                for character in font.chars() {
                    append_code(&mut code, character, kind == 9)?;
                }
                rows.push(source_row(font, code)?);
            }
        }
    }
    if rows.is_empty() {
        return Err("未配置可用录入词库".to_owned());
    }
    Ok(rows)
}

pub(super) fn source(content: Option<&str>) -> Result<Vec<SourceRow>, String> {
    content
        .and_then(|text| serde_json::from_str(text).ok())
        .ok_or_else(|| "录入内容必须为数组".to_owned())
}

pub(super) fn empty_capture(source: &[SourceRow]) -> Capture {
    capture_values(source, std::iter::repeat(Ok(""))).expect("empty answers fit every source row")
}

pub(super) fn capture(source: &[SourceRow], answers: Option<&str>) -> Result<Capture, String> {
    let answers = array(answers)?;
    if answers.len() != source.len() {
        return Err("录入条目数量与题面不一致".to_owned());
    }
    let values = answers.iter().map(|answer| {
        let object = answer
            .as_object()
            .ok_or_else(|| "录入条目无效".to_owned())?;
        object
            .get("value")
            .and_then(Value::as_str)
            .ok_or_else(|| "录入内容必须为字符串".to_owned())
    });
    capture_values(source, values)
}

struct GsonFormatter;

impl serde_json::ser::Formatter for GsonFormatter {
    fn write_string_fragment<W: ?Sized + Write>(
        &mut self,
        writer: &mut W,
        fragment: &str,
    ) -> std::io::Result<()> {
        let mut start = 0;
        for (index, character) in fragment.char_indices() {
            let escape: &[u8] = match character {
                '\u{2028}' => b"\\u2028",
                '\u{2029}' => b"\\u2029",
                _ => continue,
            };
            writer.write_all(&fragment.as_bytes()[start..index])?;
            writer.write_all(escape)?;
            start = index + character.len_utf8();
        }
        writer.write_all(&fragment.as_bytes()[start..])
    }
}

pub(super) fn serialize_source(source: &[SourceRow]) -> Result<String, serde_json::Error> {
    let mut out = Vec::new();
    source.serialize(&mut serde_json::Serializer::with_formatter(
        &mut out,
        GsonFormatter,
    ))?;
    Ok(String::from_utf8(out).expect("JSON encoder only emits UTF-8"))
}

fn json_string(out: &mut Vec<u8>, value: &str) {
    value
        .serialize(&mut serde_json::Serializer::with_formatter(
            out,
            GsonFormatter,
        ))
        .expect("writing a string to Vec cannot fail");
}

// JsonObject.toString() retains field order and explicit nulls. Emit UTF-16 code
// units independently for letter hints, including escaped surrogate code units.
fn capture_values<'a>(
    source: &[SourceRow],
    values: impl Iterator<Item = Result<&'a str, String>>,
) -> Result<Capture, String> {
    let mut out = Vec::new();
    let mut correct = 0;
    let mut errors = 0;
    out.push(b'[');
    for (index, (row, value)) in source.iter().zip(values).enumerate() {
        let value = value?;
        let code_len = row.code.encode_utf16().count();
        let value_len = value.encode_utf16().count();
        if value_len > code_len {
            return Err("录入码串超出条目长度".to_owned());
        }
        let complete = value_len == code_len;
        let matched = value == row.code;
        if complete {
            if matched {
                correct += 1;
            } else {
                errors += 1;
            }
        }
        if index != 0 {
            out.push(b',');
        }
        out.extend_from_slice(b"{\"font\":");
        json_string(&mut out, &row.font);
        out.extend_from_slice(b",\"value\":");
        json_string(&mut out, value);
        out.extend_from_slice(if complete {
            b",\"isFocus\":true"
        } else {
            b",\"isFocus\":false"
        });
        out.extend_from_slice(b",\"trueOrfalse\":");
        out.extend_from_slice(if !complete {
            b"null"
        } else if matched {
            b"true"
        } else {
            b"false"
        });
        out.extend_from_slice(b",\"pys\":[");
        let mut entered = value.encode_utf16();
        for (letter_index, unit) in row.code.encode_utf16().enumerate() {
            if letter_index != 0 {
                out.push(b',');
            }
            out.extend_from_slice(b"{\"py\":");
            if let Some(character) = char::from_u32(u32::from(unit)) {
                let mut buffer = [0; 4];
                json_string(&mut out, character.encode_utf8(&mut buffer));
            } else {
                write!(&mut out, "\"\\u{unit:04x}\"").expect("writing to Vec cannot fail");
            }
            out.extend_from_slice(b",\"trueOrfalse\":");
            out.extend_from_slice(match entered.next() {
                None => b"null",
                Some(actual) if actual == unit => b"true",
                Some(_) => b"false",
            });
            out.push(b'}');
        }
        out.extend_from_slice(b"]}");
    }
    out.push(b']');
    Ok(Capture {
        content: String::from_utf8(out).expect("JSON encoder only emits UTF-8"),
        correct,
        errors,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn capture_rebuilds_source_and_truth_instead_of_trusting_client_preview() {
        let source = freeze(
            0,
            Some(r#"[{"font":"重","pys":"zhong"},{"font":"重","pys":"chong"}]"#),
        )
        .unwrap();
        let result = capture(&source, Some(r#"[{"font":"伪造","value":"zhong","trueOrfalse":false},{"value":"ch","isFocus":true}]"#)).unwrap();
        let rows: Value = serde_json::from_str(&result.content).unwrap();
        assert_eq!((result.correct, result.errors), (1, 0));
        assert_eq!(rows[0]["font"], "重");
        assert_eq!(rows[0]["trueOrfalse"], true);
        assert_eq!(rows[1]["isFocus"], false);
        assert_eq!(rows[1]["trueOrfalse"], Value::Null);
        assert_eq!(rows[1]["pys"][2], json!({"py":"o","trueOrfalse":null}));
    }

    #[test]
    fn invalid_stock_and_answer_lengths_do_not_create_empty_successes() {
        assert!(freeze(0, None).is_err());
        assert!(freeze(0, Some("[]")).is_err());
        let source = freeze(0, Some(r#"[{"font":"甲","pys":"a"}]"#)).unwrap();
        assert!(capture(&source, Some("[]")).is_err());
        assert!(capture(&source, Some(r#"[{"value":"ab"}]"#)).is_err());
        assert!(capture(&source, Some(r#"[{"value":3}]"#)).is_err());
        let result = capture(&source, Some(r#"[{"value":"x"}]"#)).unwrap();
        assert_eq!((result.correct, result.errors), (0, 1));
    }

    #[test]
    fn article_splits_characters_and_normalizes_keyboard_punctuation() {
        let source = freeze(4, Some(r#"["A，ü"]"#)).unwrap();
        let codes: Vec<_> = source
            .iter()
            .map(|row| (row.font.as_str(), row.code.as_str()))
            .collect();
        assert_eq!(codes, [("A", "A"), ("，", ","), ("ü", "v")]);
        assert!(freeze(5, Some(r#"["A"]"#)).is_err());
    }

    #[test]
    fn same_answers_reconstruct_java_capture_identity_after_cutover() {
        let source = freeze(
            0,
            Some(r#"[{"font":"重","pys":"zhong"},{"font":"重","pys":"chong"}]"#),
        )
        .unwrap();
        let captured = capture(&source, Some(r#"[{"value":"\u2028"},{"value":""}]"#)).unwrap();
        let saved_by_java = r#"[{"font":"重","value":"\u2028","isFocus":false,"trueOrfalse":null,"pys":[{"py":"z","trueOrfalse":false},{"py":"h","trueOrfalse":null},{"py":"o","trueOrfalse":null},{"py":"n","trueOrfalse":null},{"py":"g","trueOrfalse":null}]},{"font":"重","value":"","isFocus":false,"trueOrfalse":null,"pys":[{"py":"c","trueOrfalse":null},{"py":"h","trueOrfalse":null},{"py":"o","trueOrfalse":null},{"py":"n","trueOrfalse":null},{"py":"g","trueOrfalse":null}]}]"#;
        assert_eq!(captured.content, saved_by_java);
    }
}
