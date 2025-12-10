package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 17:09
 * @Description:
 */
@Data
@Schema(name = "岗位训练-报话训练VO")
public class PostRadiotelephoneVO {
  @Schema(name = "训练名称")
  private String id;

  /**
   * 训练名称
   */
  @Schema(name = "训练名称")
  private String name;

  /**
   * 状态 0 未开始 1进行中 2 结束
   */
  @Schema(name = "状态 0 未开始 1进行中 2 结束")
  private Integer status;

  /**
   * 0 单词 1 短语
   */
  @Schema(name = "0 单词 1 短语")
  private Integer type;

  /**
   * 速率
   */
  @Schema(name = "速率")
  private BigDecimal speed;

  /**
   * 训练内容数量
   */
  @Schema(name = "训练内容数量")
  private Integer number;

  /**
   * 干扰
   */
  @Schema(name = "干扰")
  private List<Integer> disturb;



  /**
   * 训练耗时
   */
  @Schema(name = "训练时长(秒)")
  private Integer duration;

  /**
   * 训练内容
   */
  @Schema(name = "训练内容")
  private String content;


  @Schema(name = "正确率")
  private BigDecimal accuracy;


  @Schema(name = "错误个数")
  private Integer errorNumber;

  @Schema(name = "正确个数")
  private Integer passNumber;

  @Schema(name = "得分")
  private BigDecimal score;


  /**
   * 训练类型 0 听报训练 1 默写训练
   */
  @Schema(name = "训练类型 0 听报训练 1 默写训练")
  private Integer trainType;

  public PostRadiotelephoneVO() {
  }

  public PostRadiotelephoneVO(String id, String name, Integer status, Integer type, BigDecimal speed, Integer number,
                              List<Integer> disturb, Integer duration, String content, BigDecimal accuracy,
                              Integer errorNumber, Integer passNumber, BigDecimal score, Integer trainType) {
    this.id = id;
    this.name = name;
    this.status = status;
    this.type = type;
    this.speed = speed;
    this.number = number;
    this.disturb = disturb;
    this.duration = duration;
    this.content = content;
    this.accuracy = accuracy;
    this.errorNumber = errorNumber;
    this.passNumber = passNumber;
    this.score = score;
    this.trainType = trainType;
  }
}
