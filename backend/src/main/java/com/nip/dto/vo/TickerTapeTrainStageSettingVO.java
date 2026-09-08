package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-02-21 17:10
 * @Description:
 */
@Data
@Schema(name = "收报训练阶段配置VO")
public class TickerTapeTrainStageSettingVO {
  @Schema(name = "id")
  private Integer id;
  @Schema(name = "配置内容")
  private String stageArray;
}
