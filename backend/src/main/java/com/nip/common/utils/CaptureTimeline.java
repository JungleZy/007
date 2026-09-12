package com.nip.common.utils;

import com.nip.dto.CaptureInterval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CaptureTimeline {
  private CaptureTimeline() {}

  public static long durationMillis(List<CaptureInterval> intervals, long maximumEndMs) {
    if (intervals == null || maximumEndMs < 0) {
      throw new IllegalArgumentException("采集时间轴缺失或训练尚未开始");
    }
    long previousEnd = 0;
    long duration = 0;
    for (CaptureInterval interval : intervals) {
      if (interval == null || interval.startedMs() < previousEnd || interval.endedMs() <= interval.startedMs()
          || interval.endedMs() > maximumEndMs) {
        throw new IllegalArgumentException("采集区间必须有序、非负且位于训练有效时间内");
      }
      duration += interval.endedMs() - interval.startedMs();
      previousEnd = interval.endedMs();
    }
    return duration;
  }

  public static void requireExtension(List<CaptureInterval> previous, List<CaptureInterval> replacement) {
    if (previous == null || replacement == null || replacement.size() <= previous.size()) {
      throw new IllegalStateException("页内容已变化，必须读取已确认记录后继续采集");
    }
    for (int index = 0; index < previous.size(); index++) {
      if (!previous.get(index).equals(replacement.get(index))) {
        throw new IllegalStateException("不能覆盖已确认的原始采集区间");
      }
    }
  }

  public static void requireNoOverlap(List<? extends List<CaptureInterval>> pages) {
    List<CaptureInterval> intervals = new ArrayList<>();
    for (List<CaptureInterval> page : pages) {
      if (page == null) {
        throw new IllegalArgumentException("已保存页缺少采集时间轴");
      }
      intervals.addAll(page);
    }
    intervals.sort(Comparator.comparingLong(CaptureInterval::startedMs));
    durationMillis(intervals, Long.MAX_VALUE);
  }
}
