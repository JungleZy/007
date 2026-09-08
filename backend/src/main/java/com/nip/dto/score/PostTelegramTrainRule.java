package com.nip.dto.score;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @Author: wushilin
 * @Data: 2022-05-25 11:36
 * @Description: 扣分规则
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@RegisterForReflection
public class PostTelegramTrainRule {
  /**
   * 偏移量
   */
  private Integer skew;
  /**
   * 速率
   */
  private SpeedDeduct wpm;
  /**
   * 点
   */
  private SpeedDeduct dot;
  /**
   * 划
   */
  private SpeedDeduct dash;
  /**
   * 小间隔
   */
  private SpeedDeduct little;
  /**
   * 中间隔
   */
  private SpeedDeduct middle;
  /**
   * 大间隔
   */
  private SpeedDeduct large;
  /**
   * 错码
   */
  private MessageDeduct errorCode;
  /**
   * 多少码
   */
  private MessageDeduct quantoCode;
  /**
   * 多少组
   */
  private MessageDeduct quantoGroup;
  /**
   * 改错
   */
  private MessageDeduct alterError;
  /**
   * 多少行
   */
  private MessageDeduct quantoRow;
  /**
   * 串组
   */
  private MessageDeduct bunchGroup;
}
