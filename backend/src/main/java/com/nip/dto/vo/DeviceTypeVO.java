package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-07-07 09:10
 * @Description:
 */
@Data
public class DeviceTypeVO {

  private Integer id;

  /**
   * 类型名称
   */
  @Schema(name = "类型名称")
  private String typeName;

  @Schema(name = "存在设备")
  private Integer existDevice;


}
