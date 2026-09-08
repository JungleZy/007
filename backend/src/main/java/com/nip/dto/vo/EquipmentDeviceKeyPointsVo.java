package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-10-31 16:26
 * @Description:
 */
@Data
@Schema(name= "装备操作要点讲解")
public class EquipmentDeviceKeyPointsVo {

  @Schema(name = "装备名称")
  private String deviceName;

  @Schema(name = "要点内容")
  private String connect;
}
