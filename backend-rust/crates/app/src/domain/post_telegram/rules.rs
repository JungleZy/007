use super::dto::CaptureInterval;

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
