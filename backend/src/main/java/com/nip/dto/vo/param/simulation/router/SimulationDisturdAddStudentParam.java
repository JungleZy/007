package com.nip.dto.vo.param.simulation.router;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-03-03 19:28
 * @Description:
 */

@Data
@Schema(title = "仿真训练 快报/干扰报")
public class SimulationDisturdAddStudentParam {

  /**
   * 房间id
   */
  @Schema(title = "房间id")
  private Integer roomId;

  /**
   * 人员id
   */
  @Schema(title = "人员id")
  private String userId;


  /**
   * 类型 0 发报 1 收报
   */
  @Schema(title = "人员类型")
  private Integer userType;


}
