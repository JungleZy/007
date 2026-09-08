package com.nip.dto.vo.simulation;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2023-03-23 12:01
 * @Description:
 */
@Data
//@ApiModel(value = "报底vo")
@RegisterForReflection
public class SimulationRouterRoomPageVo {


  //@ApiModelProperty(value = "id")
  private Integer id;

  /**
   *训练id
   */
  //@ApiModelProperty(value = "房间id")
  private Integer roomId;

  /**
   * 页码
   */
  //@ApiModelProperty(value = "页码")
  private Integer pageNumber;

  /**
   * 排序字段
   */
  //@ApiModelProperty(value = "排序字段")
  private Integer sort;


  /**
   * 报底
   */
  //@ApiModelProperty(value = "报底")
  private String key;
}
