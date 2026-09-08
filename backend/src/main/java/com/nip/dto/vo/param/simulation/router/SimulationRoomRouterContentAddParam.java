package com.nip.dto.vo.param.simulation.router;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: lht
 * @Data: 2023-03-02 17:39
 * @Description:
 */
@Data
@Schema(title = "仿真训练 快报/干扰报")
public class SimulationRoomRouterContentAddParam {
  @Schema(title = "房间名称")
  private String roomName;

  @Schema(title = "主信号")
  private String mainSignal;

  @Schema(title = "干扰信号")
  private String interferenceSignal;

  @Schema(title = "房间报底")
  private String content;

  @Schema(title = "报底类型 1=平均保底 2=乱码报底")
  private Integer bdType;

  @Schema(title = "报文类型 1=数字短码 2=数字长码 3=字码 4=混合报")
  private Integer bwType;

  @Schema(title = "报文组数")
  private Integer bwCount;

  @Schema(title = "是否随机0 不 1是")
  private Integer isRandom;

  @Schema(title = "是否固定报0 不 1是")
  private Integer isCable;

  @Schema(title = "固定报编号")
  private String cableId;

  @Schema(title = "如果是固定报，可以选择起始页，默认第一页")
  private Integer startPage;
}
