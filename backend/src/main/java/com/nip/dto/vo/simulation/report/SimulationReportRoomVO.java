package com.nip.dto.vo.simulation.report;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Data
//@ApiModel(value = "仿真训练 通播教学")
@RegisterForReflection
public class SimulationReportRoomVO {

  //@ApiModelProperty(value = "id")
  private Integer id;
  private Integer isCable;
  //@ApiModelProperty(value = "创建人名称")
  private String userName;

  //@ApiModelProperty(value = "创建人头像")
  private String userImg;

  /**
   * 房间名称
   */
  //@ApiModelProperty(value = "房间名称")
  private String name;

  /**
   * 创建人id
   */
  //@ApiModelProperty(value = "创建人id")
  private String createUserId;

  /**
   * 房间状态 0 开启 1关闭
   */
  //@ApiModelProperty(value = "房间状态 0 开启 1关闭")
  private Integer stats;

  private LocalDateTime createTime;

  /**
   * 报底类型 1=平均保底 2=乱码报底
   */
  //@ApiModelProperty(value = "报底类型 1=平均保底 2=乱码报底")
  private Integer bdType;

  /**
   * 报文类型 1=数字短码 2=数字长码 3=字码 4=混合报
   */
  //@ApiModelProperty(value = " 报文类型 1=数字短码 2=数字长码 3=字码 4=混合报")
  private Integer bwType;

  /**
   * 报文组数
   */
  //@ApiModelProperty(value = "报文组数")
  private Integer bwCount;
  private Integer pageCount;

  private String content;

  private Integer channel;

  private List<SimulationReportRoomUserVO> receiveUser;

  //@ApiModelProperty(value = "训练耗时")
  private Integer totalTime;

  //@ApiModelProperty(value = "播报速率")
  private String mainSignal;

  //@ApiModelProperty(value = "播放状态 0 暂停 1 播放")
  private Integer playStatus;

  //@ApiModelProperty(value = "已填报页数")
  private long existPageNumber;
  @Schema(title = "页开始标识，0 否 1 是")
  private Integer isStartSign = 1;


}
