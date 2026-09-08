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
 * 那两条路径仍内嵌在依赖 DAO 的私有 {@code countScore} 里、无法直接调用，
 * 但收口后三者共用同一实现，<b>速率项的符号与方向由结构保证一致</b>——
 * 本类因此直接锁 {@code ScoreMath.wpmScore} 的契约：高于基准按 R 加分、低于基准按 L 扣分、等于基准为 0。
 */
class ScoringConsistencyTest {

  /** Key/Telex 侧 {@code Wpm.r}/{@code Wpm.l} 是 BigDecimal，可带小数系数。 */
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

  /**
   * 三个调用方的速率项符号一致。Ticker 可直接调静态方法；
   * Key/Telex 的速率分支已委托同一个 {@code ScoreMath.wpmScore}（各自 countScore 内一次调用），
   * 故此处断言同输入下该实现的结果，符号一致由「三者共用此实现」的结构保证。
   */
  @Test
  void allThreeCallersAgreeOnRateSign() {
    SpeedDeduct tickerRule = wpmRule();
    BigDecimal r = new BigDecimal("2");
    BigDecimal l = new BigDecimal("3");

    for (int speed : new int[] {BASE + 10, BASE, BASE - 10}) {
      int ticker = GeneralTickerPatService.calculateWpmScore(tickerRule, speed);
      // Key 与 Telex 的速率分支就是这一行（rule.getWpm() 的 base/r/l 同口径）
      int shared = ScoreMath.wpmScore(BASE, r, l, speed).signum();

      assertEquals(Integer.signum(ticker), shared,
          "speed=" + speed + " 时 Ticker 与 Key/Telex 共用实现的速率项符号必须一致");
    }
  }
}
