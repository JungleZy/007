package com.nip.dto.vo.simulation.router;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-03-02 18:27
 * @Description:
 */
@Data
//@ApiModel(value = "房间人员详情")
@RegisterForReflection
public class SimulationRouterRoomUserVO {

  //@ApiModelProperty(value = "发送人")
  List<SimulationRouterRoomUserInfoVO> sendUserList = new ArrayList<>();

  //@ApiModelProperty(value = "接受人")
  List<SimulationRouterRoomUserInfoVO> receiveUserList = new ArrayList<>();
}
