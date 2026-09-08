package com.nip.dto.vo.simulation.router;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2023-03-01 18:38
 * @Description:
 */
@Data
//@ApiModel(value = "仿真训练 线路通报")
@RegisterForReflection
public class SimulationRouterRoomVO {

  //@ApiModelProperty(value = "id")
  private Integer id;


  /**
   * 房间名称
   */
  //@ApiModelProperty(value = "房间名称")
  private String name;

  private Integer isCable = 0;

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
   *报底类型 1=平均保底 2=乱码报底
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

  //@ApiModelProperty(value = "报文")
  private String content;

  //@ApiModelProperty(value = "当前人所在频道号")
  private Integer currentUserChannel;

  //@ApiModelProperty(value = "当前人发送状态  0 未完成 1 已完成")
  private Integer currentUserStatus;

  //@ApiModelProperty(value = "当前人Id")
  private String currentUserId;

  //@ApiModelProperty(value = "开始训练时间")
  private String startTime;

  //@ApiModelProperty(value = "训练耗时")
  private Integer totalTime;

  //@ApiModelProperty(value = "已填报页数")
  private long existPageNumber;
}
