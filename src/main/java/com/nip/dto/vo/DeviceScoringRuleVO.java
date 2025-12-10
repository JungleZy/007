package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-08-22 14:14
 * @Description:
 */
@Data
@Schema(title = "设备评分规则VO")
public class DeviceScoringRuleVO {
  /**
   * id
   */
  @Schema(title = "id")
  private Integer id;

  @Schema(title = "名称")
  private String name;

  @Schema(title = "设备id")
  private Integer deviceId;

  /**
   * 评分规则
   */
  @Schema(title = "评分规则")
  private String ruleContent;
}
