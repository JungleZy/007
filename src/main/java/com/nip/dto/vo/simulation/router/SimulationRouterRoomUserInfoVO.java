package com.nip.dto.vo.simulation.router;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2023-03-03 11:10
 * @Description:
 */
@Data
//@ApiModel(value = "房间人员详情")
@RegisterForReflection
public class SimulationRouterRoomUserInfoVO {

  //@ApiModelProperty(value = "id")
  private String id;
  //@ApiModelProperty(value = "名称")
  private String userName;

  //@ApiModelProperty(value = "头像")
  private String userImg;
  /**
   * 发送管道号
   */
  //@ApiModelProperty(value = "发送频道号")
  private Integer channel;

  //@ApiModelProperty(value = "状态 0未完成 1已完成")
  private Integer status = 0;

  //@ApiModelProperty(value = "socket 状态 0 离线 1 在线 2准备")
  private Integer socketStatus;

  //@ApiModelProperty(value = "填报数量")
  private long existPageNumber;
}
