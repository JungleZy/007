package com.nip.common.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.nip.common.utils.PatTrainStatisticsUtil.calculateRate;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task 7.6：百分比口径收敛。原先 ToolUtil.calculateRate(min,max,total) 的首参是
 * 早已失效的哨兵（Phase 3 已证明它会把 groupGapMin=0 的正常占比误判成 0%），
 * 三参版删除后全项目只剩 PatTrainStatisticsUtil.calculateRate(count,total) 一个口径。
 * 本测试锁定这个唯一口径的可观测契约：分母为 0、分子为 0 返回 0，其余按四舍五入取整百分比。
 */
class CalculateRateTest {

  @Test
  void rateIsZeroWhenDenominatorIsZero() {
    assertEquals(0, calculateRate(5, 0).compareTo(BigDecimal.ZERO), "分母为 0 不得除零");
  }

  @Test
  void rateIsZeroWhenNumeratorIsZero() {
    assertEquals(0, calculateRate(0, 10).compareTo(BigDecimal.ZERO), "分子为 0 即 0%");
  }

  @Test
  void rateIsNumeratorOverTotalInPercent() {
    assertEquals(0, calculateRate(5, 10).compareTo(new BigDecimal(50)), "5/10 = 50%");
    assertEquals(0, calculateRate(10, 10).compareTo(new BigDecimal(100)), "全中 = 100%");
    assertEquals(0, calculateRate(1, 3).compareTo(new BigDecimal(33)), "1/3 四舍五入到 33%");
    assertEquals(0, calculateRate(2, 3).compareTo(new BigDecimal(67)), "2/3 四舍五入到 67%");
  }
}
