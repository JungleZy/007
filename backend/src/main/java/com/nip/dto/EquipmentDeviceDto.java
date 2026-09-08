package com.nip.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-10-31 11:53
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(title = "装备操作设备表")
public class EquipmentDeviceDto {

  @Schema(title = "设备名称")
  private String name;

  /**
   * 图片地址
   */
  @Schema(title = "图片地址")
  private String image;

  /**
   * 表格字段
   */
  @Schema(title = "表格字段")
  private String option;

  /**
   * 是否启用
   */
  @Schema(title = "是否启用")
  private int isEnable;

}
