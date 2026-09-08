package com.nip.dto.vo.param.simulation.router;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author lht
 * @Date: 2023/3/3 11:06
 * @Description
 */
@Data
@Schema(title = "仿真训练 快报/干扰 修改状态")
public class SimulationDisturdEditStatusParam {
  @Schema(title = "房间id")
  private Integer roomId;
}
