package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * @Author: wushilin
 * @Data: 2023-08-22 11:22
 * @Description:
 */
@Data
@Schema(title = "组网运用详情VO")
public class GroupNetTrainDetailsVO {
  @Schema(title = "id")
  private Integer id;

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
   * 答案
   */
  @Schema(title = "答案")
  private String answer;

  /**
   * 评分规则
   */
  @Schema(title = "评分规则")
  private String scoringRuleContent;


  /**
   * 得分
   */
  @Schema(title = "得分")
  private BigDecimal score;


  /**
   * 冗余字段
   */
  @Schema(title = "冗余字段")
  private String content;
}
