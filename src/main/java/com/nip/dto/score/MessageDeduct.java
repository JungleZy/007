package com.nip.dto.score;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2022-05-25 10:52
 * @Description: 内容错误扣分
 */
@Data
@RegisterForReflection
public class MessageDeduct {
  /**
   * 单个扣分
   */
  Integer l;

  /**
   * 最大扣分
   */
  Integer max;
}
