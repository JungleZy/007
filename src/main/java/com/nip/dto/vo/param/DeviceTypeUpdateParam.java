package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-07-07 09:13
 * @Description:
 */
@Data
@Schema(name = "添加类型VO")
public class DeviceTypeUpdateParam {

  @Schema(name = "id")
  private Integer id;

  @Schema(name = "类型名称")
  private String typeName;

}
