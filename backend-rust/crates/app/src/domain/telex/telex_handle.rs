//! Port of Java `TelexPatUtils.handle` and its helpers (group telex settlement).
//!
//! Java walks the submitted page text, normalises correction markers (`/`, `QTA`, `ADD`,
//! `nP`, bare 1-10 selectors), then aligns the result against the page's source groups and
//! records one analysis row per source group (plus the "extra" rows). The statistics counters
//! and the rows are both observable (score, `deductInfo`, `getPage`/`getPatValue`/`patDetail`
//! content), so the port keeps Java's exact control flow, including paths that throw
//! (`IndexOutOfBounds`, `NumberFormatException`, `NoSuchElement`): those return
//! `JavaException`, which the caller turns into the same HTTP 500 Java answers with.

use crate::shared::telex_capture::decimal_digit;

#[derive(Default, Debug, Clone, PartialEq, Eq)]
pub(crate) struct Stats {
    pub(crate) pat_group: i64,
    pub(crate) error_code: i64,
    pub(crate) correct_mistakes: i64,
    pub(crate) non_standard: i64,
    pub(crate) much_less_line: i64,
    pub(crate) much_less_groups: i64,
    pub(crate) much_less_code: i64,
    pub(crate) less_return_line: i64,
    pub(crate) less_page: i64,
    pub(crate) error_page: i64,
}

/// One source group of the page (`TelexPatPageTransferDto`).
#[derive(Debug, Clone)]
pub(crate) struct PageKey {
    pub(crate) key: String,
    pub(crate) sort: i64,
}

/// One analysis row (`TelexPatValueTransferDto`).
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct ValueRow {
    pub(crate) page_number: i64,
    pub(crate) key: String,
    pub(crate) value: String,
    pub(crate) sort: i64,
}

/// A Java runtime exception on the settlement path (the request answers HTTP 500).
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct JavaException(pub &'static str);

pub(crate) type R<T> = Result<T, JavaException>;

fn throw<T>(what: &'static str) -> R<T> {
    Err(JavaException(what))
}

fn is_digit(character: char) -> bool {
    decimal_digit(character).is_some()
}

fn is_letter_or_digit(character: char) -> bool {
    character.is_alphabetic() || is_digit(character)
}

/// `Integer.parseInt`: optional sign, Unicode decimal digits, 32-bit range.
fn parse_int(text: &str) -> R<i64> {
    let mut characters = text.chars().peekable();
    let negative = match characters.peek() {
        Some('-') => {
            characters.next();
            true
        }
        Some('+') => {
            characters.next();
            false
        }
        _ => false,
    };
    let mut value: i64 = 0;
    let mut any = false;
    for character in characters {
        let digit = decimal_digit(character).ok_or(JavaException("NumberFormatException"))?;
        any = true;
        value = value * 10 + digit as i64;
        if value > i64::from(i32::MAX) + 1 {
            return throw("NumberFormatException");
        }
    }
    if !any {
        return throw("NumberFormatException");
    }
    let value = if negative { -value } else { value };
    if value < i64::from(i32::MIN) || value > i64::from(i32::MAX) {
        return throw("NumberFormatException");
    }
    Ok(value)
}

fn parse_int_opt(text: &str) -> Option<i64> {
    parse_int(text).ok()
}

fn is_between_one_and_ten(text: &str) -> bool {
    if text.is_empty() || (text.chars().count() > 1 && text.starts_with('0')) {
        return false;
    }
    parse_int_opt(text).is_some_and(|number| (1..=10).contains(&number))
}

fn is_between_one_and_hundred(text: &str) -> bool {
    parse_int_opt(text).is_some_and(|number| (1..=100).contains(&number))
}

fn split_by_four(text: &str) -> Vec<String> {
    text.chars()
        .collect::<Vec<_>>()
        .chunks(4)
        .map(|chunk| chunk.iter().collect())
        .collect()
}

fn last_char_is_page_marker(chars: &[char]) -> bool {
    matches!(chars.last(), Some('P' | 'p'))
}

/// `isPageModification`: leading digits (1-3, no leading zero) and a trailing P/p.
fn is_page_modification(text: &str) -> bool {
    let chars: Vec<char> = text.chars().collect();
    if chars.len() < 2 || !last_char_is_page_marker(&chars) {
        return false;
    }
    let digits = chars.iter().take_while(|c| is_digit(**c)).count();
    if digits == 0 || digits > 3 {
        return false;
    }
    digits == 1 || chars[0] != '0'
}

/// `extractPageNumber`: the leading digits of a page modification, or -1.
fn extract_page_number(text: &str) -> i64 {
    let chars: Vec<char> = text.chars().collect();
    if chars.len() < 2 || !last_char_is_page_marker(&chars) {
        return -1;
    }
    let digits = chars[..chars.len() - 1]
        .iter()
        .take_while(|c| is_digit(**c))
        .count();
    if digits == 0 || digits > 3 || (digits > 1 && chars[0] == '0') {
        return -1;
    }
    let number: String = chars[..digits].iter().collect();
    match parse_int_opt(&number) {
        Some(number) if (1..=999).contains(&number) => number,
        _ => -1,
    }
}

/// `containsPattern`: the regex `[^/]+/[^/]+` finds a match.
fn contains_pattern(text: &str) -> bool {
    let chars: Vec<char> = text.chars().collect();
    (1..chars.len().saturating_sub(1))
        .any(|index| chars[index] == '/' && chars[index - 1] != '/' && chars[index + 1] != '/')
}

/// `transformString`: the text after the last `/` (empty when the slash is last).
fn transform_string(text: &str) -> String {
    match text.rfind('/') {
        None => text.to_owned(),
        Some(index) => text[index + 1..].to_owned(),
    }
}

fn is_only_slashes(text: &str) -> bool {
    !text.is_empty() && text.chars().all(|c| c == '/')
}

/// `isValid`: four characters, letter/digit prefix, trailing slash.
fn is_valid(text: &str) -> bool {
    let chars: Vec<char> = text.chars().collect();
    if chars.len() != 4 || !is_letter_or_digit(chars[0]) || chars[3] != '/' {
        return false;
    }
    let last_non_slash = (0..4).rev().find(|index| chars[*index] != '/');
    match last_non_slash {
        Some(last) => chars[..=last].iter().all(|c| is_letter_or_digit(*c)),
        None => true,
    }
}

fn is_valid_format(text: &str) -> bool {
    let chars: Vec<char> = text.chars().collect();
    chars.len() >= 2 && is_letter_or_digit(chars[0]) && chars[chars.len() - 1] == '/'
}

fn has_middle_slash(text: &str) -> bool {
    let chars: Vec<char> = text.chars().collect();
    if chars.len() < 3 {
        return false;
    }
    let mut slash_index = 0;
    let mut slash_count = 0;
    for (index, character) in chars.iter().enumerate() {
        if !is_letter_or_digit(*character) && *character != '/' {
            return false;
        }
        if *character == '/' {
            slash_count += 1;
            slash_index = index;
        }
    }
    slash_count == 1 && slash_index > 0 && slash_index < chars.len() - 1
}

/// `parseNumber`: digits without leading zeros in 1..=999, else -1.
fn parse_number(digits: &[char]) -> i64 {
    if digits.is_empty() {
        return -1;
    }
    let text: String = digits.iter().collect();
    let text = {
        let stripped = text.trim_start_matches('0');
        if stripped.is_empty() {
            "0".to_owned()
        } else {
            stripped.to_owned()
        }
    };
    match parse_int_opt(&text) {
        Some(number) if (1..=999).contains(&number) => number,
        _ => -1,
    }
}

fn number_before(chars: &[char], start_index: isize) -> i64 {
    if start_index <= 0 {
        return -1;
    }
    let mut start = start_index - 1;
    while start >= 0 && is_digit(chars[start as usize]) {
        start -= 1;
    }
    start += 1;
    parse_number(&chars[start as usize..start_index as usize])
}

fn number_after(chars: &[char], end_index: isize) -> i64 {
    if end_index >= chars.len() as isize - 1 {
        return -1;
    }
    let mut end = end_index + 1;
    while (end as usize) < chars.len() && is_digit(chars[end as usize]) {
        end += 1;
    }
    parse_number(&chars[(end_index + 1) as usize..end as usize])
}

/// `checkHyphenPattern`: 1 = `n---m` ascending range, 3 = descending, 2 = other hyphen shapes,
/// 4 = no hyphen at all.
pub(super) fn check_hyphen_pattern(input: &str) -> i64 {
    if !input.contains('-') {
        return 4;
    }
    let chars: Vec<char> = input.chars().collect();
    let mut hyphen_count = 0;
    let mut start_index: isize = -1;
    let mut end_index: isize = -1;
    let mut found = false;
    let (mut first, mut second) = (-1_i64, -1_i64);
    for index in 0..chars.len() {
        if chars[index] == '-' {
            if hyphen_count == 0 {
                start_index = index as isize;
            }
            hyphen_count += 1;
            end_index = index as isize;
        } else if hyphen_count > 0 {
            if hyphen_count == 3 {
                first = number_before(&chars, start_index);
                second = number_after(&chars, end_index);
                if first > 0 && second > 0 {
                    found = true;
                    break;
                }
            }
            hyphen_count = 0;
        }
    }
    if !found && hyphen_count == 3 {
        first = number_before(&chars, start_index);
        second = number_after(&chars, end_index);
        if first > 0 && second > 0 {
            found = true;
        }
    }
    if found {
        return if first > second { 3 } else { 1 };
    }
    first = -1;
    second = -1;
    hyphen_count = 0;
    start_index = -1;
    for index in 0..chars.len() {
        if chars[index] == '-' {
            if hyphen_count == 0 {
                start_index = index as isize;
            }
            hyphen_count += 1;
            end_index = index as isize;
        } else if hyphen_count > 0 {
            first = number_before(&chars, start_index);
            second = number_after(&chars, end_index);
            break;
        }
    }
    if hyphen_count > 0 && first < 0 {
        first = number_before(&chars, start_index);
        second = number_after(&chars, end_index);
    }
    if first > 0 && second > 0 {
        return if first > second { 3 } else { 2 };
    }
    2
}

fn valid_number(digits: &[char]) -> Option<i64> {
    if digits.is_empty() {
        return None;
    }
    let text: String = digits.iter().collect();
    parse_int_opt(&text).filter(|number| (1..=999).contains(number))
}

/// `extractNumbersAroundHyphens`: the numbers around the first `---`, or empty.
pub(super) fn extract_numbers_around_hyphens(input: &str) -> Vec<i64> {
    let chars: Vec<char> = input.chars().collect();
    let Some(hyphen_start) = chars.windows(3).position(|w| w == ['-', '-', '-']) else {
        return Vec::new();
    };
    if hyphen_start == 0 {
        return Vec::new();
    }
    let number_end = hyphen_start as isize - 1;
    let mut number_start = number_end;
    while number_start >= 0 && is_digit(chars[number_start as usize]) {
        number_start -= 1;
    }
    number_start += 1;
    let Some(before) = valid_number(&chars[number_start as usize..(number_end + 1) as usize])
    else {
        return Vec::new();
    };
    let after_start = hyphen_start + 3;
    if after_start >= chars.len() {
        return Vec::new();
    }
    let mut after_end = after_start;
    while after_end < chars.len() && is_digit(chars[after_end]) {
        after_end += 1;
    }
    let Some(after) = valid_number(&chars[after_start..after_end]) else {
        return Vec::new();
    };
    vec![before, after]
}

/// `insertElement(List, index, element)`: pads with "" up to `index`, inserts there.
fn insert_element(list: &mut Vec<String>, index: i64, element: String) {
    if index < 0 {
        return;
    }
    let index = index as usize;
    while list.len() < index {
        list.push(String::new());
    }
    if index < list.len() {
        list.insert(index, element);
    } else {
        list.push(element);
    }
}

/// `insertElement(List<List>, outer, inner, value)`.
fn insert_nested(nested: &mut Vec<Vec<String>>, outer: i64, inner: i64, value: String) {
    let inner = inner.max(0) as usize;
    let outer = outer.max(0) as usize;
    while outer >= nested.len() {
        nested.push(Vec::new());
    }
    let list = &mut nested[outer];
    while inner > list.len() {
        list.push(String::new());
    }
    list.insert(inner, value);
}

fn delete_element(nested: &mut [Vec<String>], outer: i64, inner: i64) -> bool {
    if outer < 0 || outer as usize >= nested.len() {
        return false;
    }
    let list = &mut nested[outer as usize];
    if inner < 0 || inner as usize >= list.len() {
        return false;
    }
    list.remove(inner as usize);
    true
}

/// `insertList`: pads with empty lines up to `index`, then inserts.
fn insert_list(target: &mut Vec<Vec<String>>, index: i64, new_list: Vec<String>) -> R<()> {
    if index < 0 {
        return throw("IllegalArgumentException");
    }
    let index = index as usize;
    while target.len() < index {
        target.push(Vec::new());
    }
    target.insert(index, new_list);
    Ok(())
}

fn replace_element(list: &[String], index: usize, value: &str) -> Vec<String> {
    if index >= list.len() {
        return list.to_vec();
    }
    let mut copy = list.to_vec();
    copy[index] = value.to_owned();
    copy
}

/// Appends one word to the current line (`splitByFour` for long unpunctuated words).
fn push_word(word: &str, current: &mut Vec<String>, group_number: &mut i64, ks: &mut Stats) {
    if word.chars().count() >= 8 && !word.contains('/') && !word.contains('-') {
        let strings = split_by_four(word);
        for string in &strings {
            current.push(string.clone());
            *group_number += 1;
            ks.pat_group += 1;
        }
        ks.non_standard += strings.len() as i64 - 1;
    } else {
        current.push(word.to_owned());
        *group_number += 1;
        ks.pat_group += 1;
    }
}

/// Splits the page text into lines of words; returns the number of groups counted on the page.
fn tokenize(submitted: &str, ks: &mut Stats) -> (Vec<Vec<String>>, i64) {
    let chars: Vec<char> = submitted.chars().collect();
    let length = chars.len();
    let mut result: Vec<Vec<String>> = Vec::new();
    let mut group_number = 0_i64;
    let mut start = 0_usize;
    let mut pos = 0_usize;
    let mut current: Vec<String> = Vec::new();
    while pos < length {
        let character = chars[pos];
        if character == ' ' || character == '\t' {
            if start < pos {
                let word: String = chars[start..pos].iter().collect();
                push_word(&word, &mut current, &mut group_number, ks);
            }
            start = pos + 1;
        } else if character == '\n' || character == '\r' {
            if start < pos {
                let word: String = chars[start..pos].iter().collect();
                push_word(&word, &mut current, &mut group_number, ks);
            }
            if !current.is_empty() {
                result.push(std::mem::take(&mut current));
                group_number += 1;
                ks.pat_group += 1;
            }
            if character == '\r' && pos + 1 < length && chars[pos + 1] == '\n' {
                pos += 1;
            }
            start = pos + 1;
        }
        pos += 1;
    }
    if start < pos {
        let word: String = chars[start..pos].iter().collect();
        push_word(&word, &mut current, &mut group_number, ks);
    }
    if !current.is_empty() {
        result.push(current);
    }
    (result, group_number)
}

/// The text after the last `-` with its trailing non-digits cut, `None` when there is none.
fn page_marker(submitted: &str) -> Option<String> {
    let index = submitted.rfind('-')?;
    let after = &submitted[index + 1..];
    if after.is_empty() {
        return None;
    }
    Some(after.chars().take_while(|c| is_digit(*c)).collect())
}

fn get<'a, T>(items: &'a [T], index: usize) -> R<&'a T> {
    items
        .get(index)
        .ok_or(JavaException("IndexOutOfBoundsException"))
}

/// Normalises corrections: Java's "neaten" pass. Returns the corrected lines.
fn neaten(
    result: &[Vec<String>],
    page_rows_so_far: &mut [ValueRow],
    ks: &mut Stats,
) -> R<Vec<Vec<String>>> {
    let mut neaten: Vec<Vec<String>> = Vec::new();
    for (i, line) in result.iter().enumerate() {
        let mut row: Vec<String> = Vec::new();
        if line.len() == 2 && is_between_one_and_hundred(&line[0]) {
            let i1 = parse_int(&line[0])?;
            let first = ((i1 - 1) / 10) as usize;
            let second = ((i1 - 1) % 10) as usize;
            let strings = replace_element(get(&neaten, first)?, second, &line[1]);
            let set_index = (i1 / 10) as usize;
            if set_index >= neaten.len() {
                return throw("IndexOutOfBoundsException");
            }
            neaten[set_index] = strings;
            continue;
        }
        let mut j = 0_usize;
        while j < line.len() {
            let group = line[j].clone();
            if group.contains('/') {
                if contains_pattern(&group) {
                    row.push(transform_string(&group));
                    ks.correct_mistakes += 1;
                } else if is_only_slashes(&group) && j > 0 {
                    if group.chars().count() != 4 {
                        ks.non_standard += 1;
                    }
                    if row.pop().is_none() {
                        return throw("NoSuchElementException");
                    }
                    row.push(get(line, j + 1)?.clone());
                    j += 1;
                    ks.correct_mistakes += 1;
                } else if is_valid_format(&group) {
                    if !is_valid(&group) {
                        ks.non_standard += 1;
                    }
                    row.push(get(line, j + 1)?.clone());
                    j += 1;
                    ks.correct_mistakes += 1;
                } else if has_middle_slash(&group) {
                    row.push(transform_string(&group));
                    ks.correct_mistakes += 1;
                } else {
                    row.push(group.clone());
                    ks.non_standard += 1;
                }
            } else if is_page_modification(&group) {
                if j + 2 < line.len() {
                    if i == 0 || i - 1 >= neaten.len() {
                        return throw("IndexOutOfBoundsException");
                    }
                    if !neaten[i - 1].iter().any(|item| item == "-") {
                        ks.non_standard += 1;
                    }
                    let alter_page_number = extract_page_number(&group);
                    let alter_page_group = parse_int(&line[j + 1])?;
                    let alter_page_value = line[j + 2].clone();
                    let mut ok = false;
                    for earlier in page_rows_so_far.iter_mut() {
                        if earlier.page_number == alter_page_number
                            && earlier.sort == alter_page_group - 1
                        {
                            earlier.value = alter_page_value.clone();
                            ks.correct_mistakes += 1;
                            ok = true;
                        }
                    }
                    if !ok {
                        ks.non_standard += 1;
                    }
                    j += 2;
                }
            } else if group == "QTA" {
                if j + 1 < line.len() {
                    let next = line[j + 1].clone();
                    let kind = check_hyphen_pattern(&next);
                    if kind == 1 {
                        let integers = extract_numbers_around_hyphens(&next);
                        if integers.is_empty() {
                            return throw("NoSuchElementException");
                        }
                        for k in integers[0]..integers[1] + 1 {
                            if delete_element(&mut neaten, (k - 1) / 10, integers[0] % 10 - 1) {
                                ks.correct_mistakes += 1;
                            } else {
                                ks.non_standard += 1;
                            }
                        }
                        j += integers.len();
                    } else if kind == 4 {
                        let i1 = parse_int(&next)?;
                        if i < 10 && is_between_one_and_ten(&next) {
                            if i1 < 1 || (i1 - 1) as usize >= row.len() {
                                return throw("IndexOutOfBoundsException");
                            }
                            row.remove((i1 - 1) as usize);
                            ks.correct_mistakes += 1;
                            j += 1;
                        } else if i >= 10 && is_between_one_and_hundred(&next) {
                            delete_element(&mut neaten, (i1 - 1) / 10, i1 % 10 - 1);
                            ks.correct_mistakes += 1;
                            j += 1;
                        } else {
                            j += 1;
                            ks.non_standard += 1;
                        }
                    } else {
                        j += 1;
                        ks.non_standard += 1;
                    }
                } else {
                    row.push(group.clone());
                }
            } else if group == "ADD" {
                if j + 2 < line.len() {
                    let next = line[j + 1].clone();
                    let kind = check_hyphen_pattern(&next);
                    if kind == 1 {
                        let integers = extract_numbers_around_hyphens(&next);
                        if integers.is_empty() {
                            return throw("NoSuchElementException");
                        }
                        let mut flag = 2_usize;
                        let mut add_row: Vec<String> = Vec::new();
                        for k in integers[0]..integers[1] + 1 {
                            insert_element(
                                &mut add_row,
                                (k - 1) % 10,
                                get(line, j + flag)?.clone(),
                            );
                            ks.correct_mistakes += 1;
                            flag += 1;
                        }
                        insert_list(&mut neaten, (integers[0] - 1) / 10, add_row)?;
                        j += flag;
                    } else if kind == 4 {
                        let i1 = parse_int(&next)?;
                        if i < 10 && is_between_one_and_ten(&next) {
                            insert_element(&mut row, i1 - 1, line[j + 2].clone());
                            ks.correct_mistakes += 1;
                            j += 2;
                        } else if i >= 10 && is_between_one_and_hundred(&next) {
                            insert_nested(&mut neaten, i1 / 10, i1 % 10 - 1, line[j + 2].clone());
                            ks.correct_mistakes += 1;
                            j += 2;
                        } else {
                            ks.non_standard += 1;
                        }
                    } else {
                        ks.non_standard += 1;
                    }
                } else {
                    row.push(group.clone());
                }
            } else if is_between_one_and_ten(&group) {
                if j + 1 < line.len() {
                    if row.len() < 10 {
                        ks.non_standard += 1;
                    }
                    let index = (parse_int(&group)? - 1) as usize;
                    row = replace_element(&row, index, &line[j + 1]);
                    ks.correct_mistakes += 1;
                    j += 1;
                }
            } else {
                row.push(group.clone());
            }
            j += 1;
        }
        if !row.is_empty() {
            neaten.push(row);
        }
    }
    Ok(neaten)
}

/// Java `String.split("-")[0]`: empty-array results (all hyphens) throw.
fn first_hyphen_segment(value: &str) -> R<String> {
    if value.chars().all(|c| c == '-') {
        return throw("ArrayIndexOutOfBoundsException");
    }
    Ok(value.split('-').next().unwrap_or("").to_owned())
}

/// `TelexPatUtils.handle` for one page: updates `ks` and appends this page's analysis rows to
/// `out` (which holds the earlier pages' rows too: page modifications reach back into them).
pub(crate) fn handle(
    page_number: i64,
    pages: &[PageKey],
    submitted: &str,
    ks: &mut Stats,
    out: &mut Vec<ValueRow>,
) -> R<()> {
    let (result, group_number) = tokenize(submitted, ks);
    if group_number >= 100 {
        match page_marker(submitted) {
            None => ks.less_page += 1,
            Some(marker) if marker.is_empty() => ks.less_page += 1,
            Some(marker) => {
                if marker != page_number.to_string() {
                    ks.error_page += 1;
                }
            }
        }
    }
    let mut neaten = neaten(&result, out, ks)?;

    // 少回行: a corrected line longer than ten groups that contains the next row's first source
    // group is split there.
    let mut split: Vec<Vec<String>> = Vec::new();
    for (i, line) in neaten.iter().enumerate() {
        if line.len() > 10 {
            let mut flag = 0_usize;
            for (j, group) in line.iter().enumerate().skip(10) {
                if get(pages, i + 10)?.key == *group {
                    flag = j;
                    break;
                }
            }
            if flag > 0 {
                split.push(line[..flag].to_vec());
                split.push(line[flag..].to_vec());
                ks.less_return_line += 1;
            } else {
                split.push(line.clone());
            }
        } else {
            split.push(line.clone());
        }
    }
    neaten = split;
    neaten.retain(|line| !line.is_empty());

    let emit = |out: &mut Vec<ValueRow>, key: &str, value: String, sort: i64| {
        out.push(ValueRow {
            page_number,
            key: key.to_owned(),
            value,
            sort,
        });
    };
    let mut row_num = 0_usize;
    let mut col_num = 0_usize;
    let mut j = 0_usize;
    while j < pages.len() {
        let group_key = pages[j].clone();
        if row_num < neaten.len() {
            let row = &neaten[row_num];
            if col_num < row.len() {
                let mut group_value = row[col_num].clone();
                if group_value == group_key.key {
                    emit(out, &group_key.key, group_value.clone(), group_key.sort);
                } else {
                    let mut keep_going = true;
                    let mut more_group = true;
                    let mut more_group_temp = vec![group_value.clone()];
                    for k in 1..=4 {
                        let temp_index = col_num + k;
                        if temp_index < row.len() {
                            let next = &row[temp_index];
                            if *next == group_key.key {
                                for (gs, mg) in more_group_temp.iter().enumerate() {
                                    emit(out, &group_key.key, format!("{gs},{mg}"), group_key.sort);
                                }
                                emit(
                                    out,
                                    &group_key.key,
                                    format!("{},{}", more_group_temp.len(), next),
                                    group_key.sort,
                                );
                                col_num = temp_index;
                                ks.much_less_groups += 1;
                                more_group = false;
                                keep_going = false;
                                break;
                            }
                            more_group_temp.push(next.clone());
                        }
                    }
                    if more_group {
                        let mut k = j + 1;
                        while k < j / 10 * 10 + 10 {
                            let next_page = get(pages, k)?;
                            if next_page.key == group_value {
                                let lack = k - j;
                                ks.much_less_groups += lack as i64;
                                for offset in 0..lack {
                                    let missing = get(pages, j + offset)?;
                                    emit(
                                        out,
                                        &missing.key,
                                        String::new(),
                                        group_key.sort + offset as i64,
                                    );
                                }
                                emit(out, &next_page.key, group_value.clone(), next_page.sort);
                                j += lack;
                                keep_going = false;
                                break;
                            }
                            k += 1;
                        }
                    }
                    if keep_going {
                        if group_value.contains('-') {
                            group_value = first_hyphen_segment(&group_value)?;
                            if group_value.contains(group_key.key.as_str()) {
                                emit(out, &group_key.key, group_value.clone(), group_key.sort);
                                keep_going = false;
                            }
                        }
                        if group_value.chars().count() > 4
                            && group_value.contains(group_key.key.as_str())
                        {
                            emit(out, &group_key.key, group_value.clone(), group_key.sort);
                            ks.much_less_code += 1;
                            keep_going = false;
                        }
                        if group_value.chars().count() < 4
                            && group_key.key.contains(group_value.as_str())
                        {
                            emit(out, &group_key.key, group_value.clone(), group_key.sort);
                            ks.much_less_code += 1;
                            keep_going = false;
                        }
                    }
                    if keep_going {
                        emit(out, &group_key.key, group_value.clone(), group_key.sort);
                        ks.error_code += 1;
                    }
                }
            } else {
                emit(out, &group_key.key, String::new(), group_key.sort);
                ks.much_less_groups += 1;
            }
        } else {
            emit(out, &group_key.key, String::new(), group_key.sort);
            if col_num == 0 {
                ks.much_less_line += 1;
            }
        }
        // 多行: rows left over after the last source group.
        if j == pages.len() - 1 && (row_num as isize) < neaten.len() as isize - 1 {
            ks.much_less_line += (neaten.len() - row_num) as i64;
            let mut flag = 1_i64;
            for line in &neaten[row_num..] {
                for value in line {
                    emit(out, "", value.clone(), j as i64 + flag);
                    flag += 1;
                }
            }
        }
        // 行尾多组: groups past the end of a ten-group row.
        if group_key.sort % 10 == 9 {
            let mut flag = 1;
            if row_num < neaten.len() {
                for _ in col_num + 1..neaten[row_num].len() {
                    ks.much_less_groups += 1;
                    flag += 1;
                }
            }
            col_num += flag;
        } else {
            col_num += 1;
        }
        if row_num != (j + 1) / 10 {
            col_num = 0;
            row_num += 1;
        }
        j += 1;
    }
    Ok(())
}
