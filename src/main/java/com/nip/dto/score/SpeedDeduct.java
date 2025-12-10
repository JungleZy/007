package com.nip.dto.score;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2022-05-25 10:34
 * @Description: 速率扣分
 */
@Data
@RegisterForReflection
public  class SpeedDeduct {
  /**
   * 临界值
   */
  Integer base;

  /**
   * 低于扣分
   */
  Integer r;

  /**
   * 高于加分
   */
  Integer l;

  /**
   * 最大扣分
   */
  Integer max;

  /**
   * 速率计算规则 true->wpm false->码每分
   */
  private Boolean type;

}
