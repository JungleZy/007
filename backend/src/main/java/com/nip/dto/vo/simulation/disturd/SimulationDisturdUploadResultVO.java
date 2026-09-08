package com.nip.dto.vo.simulation.disturd;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-03-06 15:57
 * @Description:
 */
@Data
//@ApiModel(value = "提交训练结果")
@RegisterForReflection
public class SimulationDisturdUploadResultVO {

  //@ApiModelProperty(value = "用户id",position = 1)
  private String userId;

  //@ApiModelProperty(value = "房间id",position = 2)
  private Integer roomId;

  //@ApiModelProperty(value = "结果",position = 3)
  private List<String> contentValue;
}
