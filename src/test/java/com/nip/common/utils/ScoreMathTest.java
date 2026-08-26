package com.nip.common.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreMathTest {

  @Test
  void ratePerMinute() {
    // 120 次 / 60 秒 = 120 次/分钟
    assertEquals(new BigDecimal(120), ScoreMath.rate(120, 60_000));
    // 7 次 / 120 秒 = 3.5 → HALF_UP → 4
    assertEquals(new BigDecimal(4), ScoreMath.rate(7, 120_000));
  }

  @Test
  void rateZeroDenominatorReturnsZero() {
    assertEquals(BigDecimal.ZERO, ScoreMath.rate(10, 0));
    assertEquals(BigDecimal.ZERO, ScoreMath.rate(10, -5));
  }

  @Test
  void rateZeroCountReturnsZero() {
    assertEquals(BigDecimal.ZERO, ScoreMath.rate(0, 60_000));
  }

  @Test
  void accuracyPercent() {
    assertEquals(new BigDecimal("33.00"), ScoreMath.accuracy(1, 3));
    assertEquals(new BigDecimal("100.00"), ScoreMath.accuracy(4, 4));
  }

  @Test
  void accuracyGuardsDenominatorNotNumerator() {
    // 分母为 0：返回 0，而不是 ArithmeticException（P2-15/51 守分子漂移）
    assertEquals(BigDecimal.ZERO, ScoreMath.accuracy(5, 0));
    // 分子为负：按 0 计，不出现负正确率
    assertEquals(new BigDecimal("0.00"), ScoreMath.accuracy(-3, 10));
  }
}
