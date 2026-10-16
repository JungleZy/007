use super::dto::CaptureInterval;
use serde_json::Value;

pub(super) fn capture_duration(
    intervals: &[CaptureInterval],
    maximum_end: i64,
) -> Result<i64, &'static str> {
    if maximum_end < 0 {
        return Err("采集时间轴缺失或训练尚未开始");
    }
    let mut previous_end = 0_i64;
    let mut duration = 0_i64;
    for interval in intervals {
        if interval.started_ms < previous_end
            || interval.ended_ms <= interval.started_ms
            || interval.ended_ms > maximum_end
        {
            return Err("采集区间必须有序、非负且位于训练有效时间内");
        }
        duration = duration
            .checked_add(interval.ended_ms - interval.started_ms)
            .ok_or("采集时长无效")?;
        previous_end = interval.ended_ms;
    }
    Ok(duration)
}
pub(super) fn require_no_overlap(pages: &[Vec<CaptureInterval>]) -> Result<(), &'static str> {
    let mut intervals = pages
        .iter()
        .flat_map(|page| page.iter().cloned())
        .collect::<Vec<_>>();
    intervals.sort_by_key(|interval| interval.started_ms);
    let mut previous_end = 0_i64;
    for interval in intervals {
        if interval.started_ms < previous_end || interval.ended_ms <= interval.started_ms {
            return Err("采集区间必须有序、非负且位于训练有效时间内");
        }
        previous_end = interval.ended_ms;
    }
    Ok(())
}

pub(super) fn validate_standard(standard: &[Value], has_body: bool) -> Result<(), &'static str> {
    if has_body && standard.is_empty() {
        return Err("点划基准记录不能为空");
    }
    for item in standard {
        let Some(object) = item.as_object() else {
            return Err("点划自校准基准记录无效");
        };
        for name in ["dot", "line", "codeGap", "wordGap", "groupGap"] {
            if object
                .get(name)
                .and_then(Value::as_i64)
                .is_none_or(|value| value <= 0)
            {
                return Err("点划自校准基准记录无效");
            }
        }
    }
    Ok(())
}

#[derive(Clone, Copy)]
pub(crate) struct TimingRule {
    pub(crate) base: i64,
    pub(crate) low: i64,
    pub(crate) high: i64,
    pub(crate) max: i64,
}

pub(crate) struct HandkeyRule {
    pub(super) timing: [TimingRule; 5],
    pub(crate) other: [(i64, i64); 6],
    pub(crate) speed: TimingRule,
}

fn rule_integer(value: &Value, required: bool) -> Result<i64, &'static str> {
    if value.is_null() && !required {
        return Ok(0);
    }
    let number = value.as_f64().ok_or("评分规则必须包含有效非负整数")?;
    if number < 0.0 || number > i32::MAX as f64 || number.fract() != 0.0 {
        return Err("评分规则必须包含有效非负整数");
    }
    Ok(number as i64)
}

fn timing_rule(value: &Value, speed: bool) -> Result<TimingRule, &'static str> {
    if !value.is_object() || (!value["type"].is_null() && !value["type"].is_boolean()) {
        return Err("评分规则必须是完整对象");
    }
    let rule = TimingRule {
        base: rule_integer(&value["base"], true)?,
        low: rule_integer(&value["l"], !speed)?,
        high: rule_integer(&value["r"], !speed)?,
        max: rule_integer(&value["max"], !speed)?,
    };
    if speed && rule.base == 0 {
        return Err("评分规则速率基准必须大于0");
    }
    Ok(rule)
}

pub(crate) fn handkey_rule(content: Option<&str>) -> Result<HandkeyRule, &'static str> {
    let root: Value = serde_json::from_str(content.ok_or("训练评分规则快照缺失，请新建训练")?)
        .map_err(|_| "评分规则必须是完整JSON对象")?;
    if root["rateUnit"].as_str() != Some("CHARACTERS_PER_MINUTE") {
        return Err("评分规则rateUnit必须明确为CHARACTERS_PER_MINUTE");
    }
    rule_integer(&root["skew"], true)?;
    let mut other = [(0, 0); 6];
    for (index, name) in [
        "errorCode",
        "quantoCode",
        "quantoGroup",
        "alterError",
        "quantoRow",
        "bunchGroup",
    ]
    .iter()
    .enumerate()
    {
        other[index] = (
            rule_integer(&root["other"][*name]["l"], true)?,
            rule_integer(&root["other"][*name]["max"], true)?,
        );
    }
    Ok(HandkeyRule {
        timing: [
            timing_rule(&root["code"]["dot"], false)?,
            timing_rule(&root["code"]["dash"], false)?,
            timing_rule(&root["gap"]["little"], false)?,
            timing_rule(&root["gap"]["middle"], false)?,
            timing_rule(&root["gap"]["large"], false)?,
        ],
        other,
        speed: timing_rule(&root["wpm"], true)?,
    })
}

#[derive(Default)]
pub(crate) struct HandkeyScore {
    pub(crate) correct: i64,
    pub(crate) errors: i64,
    pub(crate) total: i64,
    pub(crate) more_groups: i64,
    pub(crate) lack_groups: i64,
    pub(super) words: i64,
    pub(crate) lines: i64,
    pub(super) bunches: i64,
    pub(super) alterations: i64,
    pub(super) timing: [i64; 5],
    pub(crate) durations: [i64; 5],
    pub(crate) statistics: [[i64; 3]; 5],
}

impl HandkeyScore {
    pub(crate) fn correct(&self) -> i64 {
        self.correct
    }
    pub(crate) fn total(&self) -> i64 {
        self.total
    }
    pub(crate) fn errors(&self) -> i64 {
        self.errors
    }
}
