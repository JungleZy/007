package com.nip.dto.vo.param.simulation.report;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-03-01 17:39
 * @Description:
 */
@Data
@Schema(title = "仿真训练 通播教学 添加房间")
public class SimulationRoomReportAddParam {

  @Schema(title = "房间名称")
  private String roomName;

  @Schema(title = "是否是固定报 0 随机报 1 固定报")
  private Integer isCable = 0;

  @Schema(title = "如果是固定报，该值为固定报编号，否则为空")
  private String cableId;

  @Schema(title = "如果是固定报，可以选择起始页，默认第一页")
  private Integer startPage;

  @Schema(title = "房间发报人员")
  private List<String> sendUserList;

  @Schema(title = "房间收报人员")
  private List<String> receiveUserList;

  @Schema(title = "房间报底")
  private String content;

  @Schema(title = "报底类型 1=平均保底 2=乱码报底")
  private Integer bdType;

  @Schema(title = "报文类型 1=数字短码 2=数字长码 3=字码 4=混合报")
  private Integer bwType;

  @Schema(title = "报文组数")
  private Integer bwCount;

  @Schema(title = "播放速率")
  private String mainSignal;

  @Schema(title = "是否随机 0 不 1 是")
  private Integer isRandom;

  @Schema(title = "页开始标识，0 否 1 是")
  private Integer isStartSign = 1;
}
