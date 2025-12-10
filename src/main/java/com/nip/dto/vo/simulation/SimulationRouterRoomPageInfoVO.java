package com.nip.dto.vo.simulation;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-03-23 12:00
 * @Description:
 */
@Data
//@ApiModel(value = "训练页详情对象")
@RegisterForReflection
public class SimulationRouterRoomPageInfoVO {

  //@ApiModelProperty(value = "保底内容")
  private List<SimulationRouterRoomPageVo> pageVos;


  //@ApiModelProperty(value = "填报内容")
  private List<String> value;


  //@ApiModelProperty(value = "pagecode")
  private List<String> pageCode = new ArrayList<>();
}
