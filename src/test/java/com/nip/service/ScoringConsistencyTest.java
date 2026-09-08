package com.nip.service;

import com.nip.dto.score.SpeedDeduct;
import com.nip.service.general.GeneralTickerPatService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 3.2 / 3.4：评分口径的纯算法回归（无 DB，普通 JUnit5）。
 * 速率项：手键（Ticker）必须与电键 GeneralKeyPatService:814-824、
 * 数据报 GeneralTelexPatService:763-773 同口径——高于基准按 R 加分、低于基准按 L 扣分。
 * 那两条路径的速率计算内嵌在依赖 DAO 的私有 countScore 中，无法直接调用，
 * 故此处锁定 Ticker 侧可观测的符号与方向契约（R 加、L 减、等于基准为 0）。
 */
class ScoringConsistencyTest {

  private static SpeedDeduct wpmRule() {
    SpeedDeduct rule = new SpeedDeduct();
    rule.setBase(70);
    rule.setR(2);
    rule.setL(3);
    rule.setMax(100);
    return rule;
  }

  @Test
  void fasterThanBaseScoresHigherThanBase() {
    SpeedDeduct rule = wpmRule();

    assertTrue(GeneralTickerPatService.calculateWpmScore(rule, 80)
        > GeneralTickerPatService.calculateWpmScore(rule, 70));
  }

  @Test
  void slowerThanBaseScoresLowerThanBase() {
    SpeedDeduct rule = wpmRule();

    assertTrue(GeneralTickerPatService.calculateWpmScore(rule, 60)
        < GeneralTickerPatService.calculateWpmScore(rule, 70));
  }

  @Test
  void speedBonusUsesRAndPenaltyUsesL() {
    SpeedDeduct rule = wpmRule();

    assertEquals(20, GeneralTickerPatService.calculateWpmScore(rule, 80));
    assertEquals(0, GeneralTickerPatService.calculateWpmScore(rule, 70));
    assertEquals(-30, GeneralTickerPatService.calculateWpmScore(rule, 60));
  }
}
