//! 手键正文脚本：手工 ? / 改错、自动重发与逻辑页位置共用一套组游标。
use std::collections::HashMap;

use crate::{keying, timeline::Rng};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Correction {
    Current,
    Previous,
}

#[derive(Clone, Debug)]
pub(crate) enum Step {
    Text { text: String, group: usize, end_group: bool },
    Correct { kind: Correction, group: usize },
    Page,
}

pub(crate) struct Program {
    pub steps: Vec<Step>,
    pub groups: Vec<String>,
}

fn flush(pending: &mut String, steps: &mut Vec<Step>, group: usize, end_group: bool) {
    if !pending.is_empty() {
        steps.push(Step::Text { text: std::mem::take(pending), group, end_group });
    }
}

pub(crate) fn parse(text: &str, alphabet: &str) -> Result<Program, String> {
    let text = text.trim();
    keying::validate(&text.replace(['?', '/', '。', '.'], ""), alphabet)?;
    let compact = !text.contains(char::is_whitespace);
    let mut steps = Vec::new();
    let mut groups: Vec<String> = Vec::new();
    let mut pending = String::new();
    let mut cursor: usize = 0;
    let mut page_start: usize = 0;
    let mut correction_resume = None;
    let mut preserve_group = None;
    for character in text.chars() {
        if character == '。' || character == '.' {
            if !pending.is_empty() {
                flush(&mut pending, &mut steps, cursor, true);
                cursor = correction_resume.take().unwrap_or(cursor + 1);
            }
            if cursor == page_start || matches!(steps.last(), Some(Step::Correct { .. })) {
                return Err("翻页符前需有完整报文，改错符后需先重拍目标组".into());
            }
            steps.push(Step::Page);
            page_start = cursor;
            preserve_group = None;
        } else if character.is_whitespace() {
            if !pending.is_empty() {
                flush(&mut pending, &mut steps, cursor, true);
                if let Some(resume) = correction_resume.take() {
                    cursor = resume;
                    preserve_group = Some(resume);
                } else { cursor += 1; }
            }
        } else if character == '?' || character == '/' {
            // 紧贴四码完整组的控制符仍是组后操作，如 3128/4253；不能把它当半组回退。
            if pending.chars().count() == 4 {
                flush(&mut pending, &mut steps, cursor, true);
                if let Some(resume) = correction_resume.take() {
                    cursor = resume;
                    preserve_group = Some(resume);
                } else { cursor += 1; }
            }
            let occupied = !pending.is_empty();
            if !occupied && cursor == page_start {
                return Err("当前页尚未拍发报文，不能发送改错符".into());
            }
            flush(&mut pending, &mut steps, cursor, false);
            let kind = if character == '?' { Correction::Current } else { Correction::Previous };
            if kind == Correction::Previous && correction_resume.is_none() {
                correction_resume = Some(cursor);
            }
            // 同个人岗位手键：组内有字时当前/前一分别退0/1组；已成组时分别退1/2组。
            let back = match (kind, occupied) {
                (Correction::Current, true) => 0,
                (Correction::Current, false) | (Correction::Previous, true) => 1,
                (Correction::Previous, false) => 2,
            };
            cursor = cursor.saturating_sub(back).max(page_start);
            steps.push(Step::Correct { kind, group: cursor });
        } else {
            if pending.is_empty() {
                if cursor == groups.len() { groups.push(String::new()); }
                else if preserve_group != Some(cursor) { groups[cursor].clear(); }
                preserve_group = None;
            }
            pending.push(character);
            groups[cursor].push(character);
            if compact && pending.chars().count() == 4 {
                flush(&mut pending, &mut steps, cursor, true);
                if let Some(resume) = correction_resume.take() {
                    cursor = resume;
                    preserve_group = Some(resume);
                } else { cursor += 1; }
            }
        }
    }
    flush(&mut pending, &mut steps, cursor, true);
    if matches!(steps.last(), Some(Step::Correct { .. })) {
        return Err("改错符后请填写需要重发的报文".into());
    }
    Ok(Program { steps, groups })
}

fn pick(candidates: &[usize], seed: u32) -> Vec<usize> {
    if candidates.is_empty() { return Vec::new(); }
    let mut rng = Rng::new(seed);
    let mut selected: Vec<usize> = candidates.iter().copied().filter(|_| rng.next_f64() < 0.05).collect();
    if selected.is_empty() { selected.push(rng.pick(candidates)); }
    selected
}

pub(crate) fn add_automatic(program: Program, current: bool, previous: bool, seed: u32) -> Program {
    if !current && !previous { return program; }
    let mut current_candidates = Vec::new();
    let mut previous_candidates = Vec::new();
    let mut history = HashMap::new();
    for (index, step) in program.steps.iter().enumerate() {
        if matches!(step, Step::Page) { history.clear(); }
        if let Step::Text { text, group, end_group: true } = step {
            current_candidates.push(index);
            if *group > 0 && history.contains_key(&(group - 1)) {
                previous_candidates.push(index);
            }
            history.insert(*group, text.clone());
        }
    }
    let current_picks = if current { pick(&current_candidates, seed.wrapping_add(0x43555252)) } else { Vec::new() };
    let previous_picks = if previous { pick(&previous_candidates, seed.wrapping_add(0x50524556)) } else { Vec::new() };
    history.clear();
    let mut steps = Vec::new();
    for (index, step) in program.steps.into_iter().enumerate() {
        steps.push(step.clone());
        if let Step::Text { text, group, end_group: true } = step {
            history.insert(group, text.clone());
            if current_picks.contains(&index) {
                steps.push(Step::Correct { kind: Correction::Current, group });
                steps.push(Step::Text { text: text.clone(), group, end_group: true });
            }
            if previous_picks.contains(&index) {
                steps.push(Step::Correct { kind: Correction::Previous, group: group - 1 });
                steps.push(Step::Text { text: history[&(group - 1)].clone(), group: group - 1, end_group: true });
            }
        }
    }
    Program { steps, groups: program.groups }
}
