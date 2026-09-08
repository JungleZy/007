package com.nip.dto;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-10-31 16:18
 * @Description:
 */
@Data
@Schema(title = "装备操作要点讲解DTO")
public class EquipmentDeviceKeyPointsDto {

  @Schema(title = "设备id")
  private String deviceId;

  @Schema(title = "要点内容")
  private String content;

}
