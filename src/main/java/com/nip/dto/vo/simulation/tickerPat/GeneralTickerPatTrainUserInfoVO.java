package com.nip.dto.vo.simulation.tickerPat;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-04-08 18:41
 * @Description:
 */
@Data
@RegisterForReflection
public class GeneralTickerPatTrainUserInfoVO {
  private String userId;
  private String userName;
  private String userImg;

  /**
   * 0 离线 1在线 2准备
   */
  private Integer userStatus;

  /**
   * 角色 0 参训人 1 组训人
   */
  private Integer role;

  /**
   * 得分
   */
  //@ApiModelProperty("得分")
  private BigDecimal score;

  /**
   * 扣分详情
   */
  private String deductInfo;

  /**
   * 统计信息 点、划、间隔
   */
  private String statisticInfo;

  /**
   * 错误个数
   */
  private Integer errorNumber;

  /**
   * 正确率
   */
  private String accuracy;

  /**
   * 速率
   */
  private String speed;

  /**
   * 拍发记录
   */
  private String speedLog;

  /**
   * 漏拍
   */
  private Integer lack;

  /**
   * 已填报的页数
   */
  private Integer existPageNumber;

  /**
   * 已上传的页码
   */
  private List<Integer> existNumber;

  /**
   * 0未完成 1完成
   */
  private Integer isFinish;

  /**
   * 有效时长
   */
  //@ApiModelProperty(value = "有效时长",position = 9)
  private Long validTime;

  /**
   * 结束时间
   */
  private LocalDateTime finishTime;


  List<GeneralTickerPatTrainUserValueVO> patValue;
}
