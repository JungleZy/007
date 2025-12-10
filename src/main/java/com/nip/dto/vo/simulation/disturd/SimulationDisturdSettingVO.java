package com.nip.dto.vo.simulation.disturd;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2023-03-10 19:15
 * @Description:
 */
@Data
//@ApiModel(value = "更新、保存设置")
@RegisterForReflection
public class SimulationDisturdSettingVO {
  //@ApiModelProperty(value = "房间id")
  private Integer roomId;

  //@ApiModelProperty(value = "设置")
  private String setting;
}
