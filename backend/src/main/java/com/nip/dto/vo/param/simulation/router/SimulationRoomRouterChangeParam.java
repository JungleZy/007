package com.nip.dto.vo.param.simulation.router;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-03-03 10:44
 * @Description:
 */
@Data
@Schema(title = "更改信道参数")
public class SimulationRoomRouterChangeParam {

  @Schema(title = "房间id")
  private Integer roomId;

  @Schema(title = "用户id")
  private String userId;

  @Schema(title = "更改频道号")
  private  Integer channel;
}
