package com.nip.dto.vo.simulation.router;


import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: lht
 * @Data: 2023-03-02 18:38
 * @Description:
 */
@Data
//@ApiModel(value = "仿真训练 快报/干扰报")
@RegisterForReflection
public class SimulationRouterRoomContentVO {

  //@ApiModelProperty(value = "id")
  private Integer id;

  private Integer isCable;
  /**
   * 房间名称
   */
  //@ApiModelProperty(value = "房间名称")
  private String name;


  /**
   * 创建人
   */
  //@ApiModelProperty(value = "创建人")
  private String userName;

  /**
   * 创建人头像
   */
  //@ApiModelProperty(value = "创建头像")
  private String userImg;

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

  //@ApiModelProperty(value = "用户id")
  private String createUserId;


}
