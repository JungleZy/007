package com.nip.common.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 评分公共数学口径（Phase3 Task 3.3，P2-68 / P2-15 / P2-51 统一收口）。
 * 全仓速率/正确率计算一律走这里，禁止再内联除法。
 */
public final class ScoreMath {

  private static final BigDecimal MILLIS_PER_MINUTE = BigDecimal.valueOf(60_000L);
  private static final BigDecimal HUNDRED = new BigDecimal(100);

  private ScoreMath() {
  }

  /**
   * 速率 = count / totalTimeMillis 折算为「次/分钟」。
   *
   * @param count           次数（组数/码数/拍发次数）
   * @param totalTimeMillis 总耗时，单位【毫秒】；秒要先乘 1000
   * @return 每分钟次数，0 位小数 HALF_UP；count<=0 或 totalTimeMillis<=0 时返回 0（零除返 0）
   */
  public static BigDecimal rate(long count, long totalTimeMillis) {
    if (count <= 0 || totalTimeMillis <= 0) {
      return BigDecimal.ZERO;
    }
    return new BigDecimal(count)
        .multiply(MILLIS_PER_MINUTE)
        .divide(new BigDecimal(totalTimeMillis), 0, RoundingMode.HALF_UP);
  }

  /**
   * 正确率百分比 = correct / total * 100。
   *
   * @param correct 正确个数（为负按 0 计，避免负正确率）
   * @param total   总个数，守【分母】：total<=0 返回 0
   * @return 百分比，中间除法 2 位小数 HALF_UP 再乘 100（与既有落库口径一致，如 33.00）
   */
  public static BigDecimal accuracy(long correct, long total) {
    if (total <= 0) {
      return BigDecimal.ZERO;
    }
    long safeCorrect = Math.max(correct, 0);
    return new BigDecimal(safeCorrect)
        .divide(new BigDecimal(total), 2, RoundingMode.HALF_UP)
        .multiply(HUNDRED);
  }

  /**
   * 速率加减分：<b>高于基准按 r 加分、低于基准按 l 扣分</b>，等于基准不加不减。
   * 全仓唯一实现——Ticker / Key / Telex 三条速率路径一律委托此处，禁止再内联该分支。
   *
   * <p>调用方负责训练域的单位及舍入口径；本方法只按实际速率与基准的差值计算带符号的调整分。
   *
   * @param base  速率基准值
   * @param r     高于基准时每高 1 的加分系数；{@code null} 按零系数处理，不抛
   * @param l     低于基准时每低 1 的扣分系数；{@code null} 按零系数处理，不抛
   * @param speed 本次训练的平均速率（取整口径由调用方决定，Ticker HALF_DOWN、Key/Telex HALF_UP）
   * @return 速率项得分，<b>带符号</b>：正数为加分、负数为扣分；{@code speed == base} 时为 0
   */
  public static BigDecimal wpmScore(int base, BigDecimal r, BigDecimal l, int speed) {
    if (speed > base) {
      return r == null ? BigDecimal.ZERO : r.multiply(new BigDecimal(speed - base));
    }
    if (speed < base) {
      return l == null ? BigDecimal.ZERO : l.multiply(new BigDecimal(base - speed)).negate();
    }
    return BigDecimal.ZERO;
  }

  /** 保留调用方的精确速率，不在速率评分中额外取整；缺省 r/l 仍按零系数处理。 */
  public static BigDecimal wpmScore(int base, BigDecimal r, BigDecimal l, BigDecimal speed) {
    BigDecimal difference = speed.subtract(BigDecimal.valueOf(base));
    if (difference.signum() > 0) return r == null ? BigDecimal.ZERO : r.multiply(difference);
    if (difference.signum() < 0) return l == null ? BigDecimal.ZERO : l.multiply(difference);
    return BigDecimal.ZERO;
  }
}
