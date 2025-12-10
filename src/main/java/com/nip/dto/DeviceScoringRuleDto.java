package com.nip.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-08-22 14:05
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(title = "设备评分规则")
public class DeviceScoringRuleDto {

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
