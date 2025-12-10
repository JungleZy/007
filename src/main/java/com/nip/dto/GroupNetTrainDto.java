package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-08-22 10:35
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(title = "组网运用训练Dto")
@RegisterForReflection
public class GroupNetTrainDto {

  /**
   * 设备类型
   */
  @Schema(title = "设备类型")
  private Integer deviceType;

  /**
   * 设备id
   */
  @Schema(title = "设备id")
  private Integer deviceId;

  /**
   * 题目
   */
  @Schema(title = "题目")
  private String topic;

  /**
   * 评分规则内容
   */
  @Schema(title = "评分规则内容")
  private String scoringRuleContent;

  /**
   * 答案
   */
  @Schema(title = "答案")
  private String answer;
}
