package com.nip.service;

import com.nip.common.utils.ScoreMath;
import com.nip.dto.score.SpeedDeduct;
import com.nip.service.general.GeneralTickerPatService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 3.2 / 3.4：评分口径的纯算法回归（无 DB，普通 JUnit5）。
 *
 * <p>速率加减分现已收口为唯一实现 {@link ScoreMath#wpmScore(int, BigDecimal, BigDecimal, int)}：
 * 手键 {@code GeneralTickerPatService.calculateWpmScore} 委托它，
 * 电键 {@code GeneralKeyPatService.countScore} 与数据报 {@code GeneralTelexPatService.countScore}
 * 的速率分支也各自只剩一次 {@code ScoreMath.wpmScore(...)} 调用。
 *
 * <p>本类<b>只锁 {@code ScoreMath.wpmScore} 这一纯算法的契约</b>：高于基准按 R 加分、
 * 低于基准按 L 扣分、等于基准为 0、小数系数不被截断、系数缺省不抛 NPE；
 * 手键那条经 {@code calculateWpmScore} 的整数出口一并覆盖。
 * 电键与数据报的速率分支内嵌在依赖 DAO 的私有 {@code countScore} 里，本类无法执行，
 * 因此<b>各域落库成绩里码率项是否正确，由各域自己的结算用例负责</b>，不要指望本类替它们守门。
 */
class ScoringConsistencyTest {

  /** 全类共用的速率基准（字/分）：高于它走 R 加分、低于它走 L 扣分、等于它得 0。 */
  private static final int BASE = 70;

  private static SpeedDeduct wpmRule() {
    SpeedDeduct rule = new SpeedDeduct();
    rule.setBase(BASE);
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

  @Test
  void wpmScoreIsPositiveAboveBaseAndNegativeBelow() {
    BigDecimal r = new BigDecimal("2");
    BigDecimal l = new BigDecimal("3");

    assertEquals(0, new BigDecimal("20").compareTo(ScoreMath.wpmScore(BASE, r, l, 80)),
        "高于基准 10 且 R=2 应为 +20");
    assertEquals(0, new BigDecimal("-30").compareTo(ScoreMath.wpmScore(BASE, r, l, 60)),
        "低于基准 10 且 L=3 应为 -30");
    assertEquals(1, ScoreMath.wpmScore(BASE, r, l, 71).signum(), "高于基准必须是加分");
    assertEquals(-1, ScoreMath.wpmScore(BASE, r, l, 69).signum(), "低于基准必须是扣分");
  }

  @Test
  void wpmScoreIsZeroExactlyAtBase() {
    BigDecimal r = new BigDecimal("2");
    BigDecimal l = new BigDecimal("3");

    assertEquals(0, ScoreMath.wpmScore(BASE, r, l, BASE).signum());
    assertEquals(0, ScoreMath.wpmScore(0, r, l, 0).signum());
  }

  /**
   * 「不改分」的守门测试：Key/Telex 的 {@code Wpm.r}/{@code Wpm.l} 是 BigDecimal，
   * 系数带小数时不得被截成 int，否则 1.5 会变 1、2.5 会变 2，直接改分。
   */
  @Test
  void fractionalCoefficientsAreNotTruncated() {
    BigDecimal r = new BigDecimal("1.5");
    BigDecimal l = new BigDecimal("2.5");

    assertEquals(0, new BigDecimal("15.0").compareTo(ScoreMath.wpmScore(BASE, r, l, 80)),
        "1.5 × 10 = 15，被截成 int 则只有 10");
    assertEquals(0, new BigDecimal("-25.0").compareTo(ScoreMath.wpmScore(BASE, r, l, 60)),
        "-(2.5 × 10) = -25，被截成 int 则只有 -20");
    assertEquals(0, new BigDecimal("1.5").compareTo(ScoreMath.wpmScore(BASE, r, l, 71)),
        "diff=1 时小数系数原样体现");
  }

  /** 系数缺省（规则 JSON 未配 r/l）按零系数处理，不能抛 NPE 把整次评分打回 500。 */
  @Test
  void nullCoefficientsScoreZeroInsteadOfThrowing() {
    assertEquals(0, ScoreMath.wpmScore(BASE, null, null, 80).signum());
    assertEquals(0, ScoreMath.wpmScore(BASE, null, null, 60).signum());
  }
}
