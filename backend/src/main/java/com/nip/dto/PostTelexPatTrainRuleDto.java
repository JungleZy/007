package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 评分规则
 * @Author: wushilin
 * @Data: 2022-05-16 17:06
 * @Description:
 */

@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class PostTelexPatTrainRuleDto {

  /**
   * 速度
   */
  private Wpm wpm;

  /**
   * 扣分
   */
  private Other other;

  @Data
  @RegisterForReflection
  public static class Wpm{
    /**
     * 速度
     */
    Integer base;

    /**
     *高于扣分
     */
    BigDecimal r;
    /**
     *低于扣分
     */

    BigDecimal l;
  }

  @Data
  @RegisterForReflection
  public static class Other{
    /**
     * 错码
     */
    BigDecimal errorCode;

    /**
     * 多少组
     */
    BigDecimal muchLessGroups;

    /**
     * 改错
     */
    BigDecimal correctMistakes;

    /**
     * 少页标
     */
    BigDecimal lessPage;

    /**
     * 少回行
     */
    BigDecimal lessReturnLine;

    /**
     * 多少行
     */
    BigDecimal muchLessLine;

    /**
     * 多少码
     */
    BigDecimal muchLessCode;

    /**
     * 页标错
     */
    BigDecimal errorPage;

    /**
     * 不规则
     */
    BigDecimal nonStandart;

    /**
     * 改错
     */
    BigDecimal alterError;

    /**
     * 串组
     */
    BigDecimal bunchGroup;
    /**
     * 少间隔
     */
    BigDecimal lessGap = new BigDecimal(0);
  }
}
