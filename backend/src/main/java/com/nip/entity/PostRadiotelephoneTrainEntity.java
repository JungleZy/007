package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 16:57
 * @Description:
 */

@Data
@Entity(name = "t_post_radiotelephone_train")
@Cacheable(value = false)
public class PostRadiotelephoneTrainEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 用户id
   */
  private String userId;

  /**
   * 训练名称
   */
  private String name;

  /**
   * 状态 0 未开始 1进行中 2 结束
   */
  private Integer status;

  /**
   * 0 单词 1 短语
   */
  private Integer type;

  /**
   * 训练类型 0 听报训练 1 默写训练
   */
  private Integer trainType;

  /**
   * 速率
   */
  private BigDecimal speed;

  /**
   * 训练内容数量
   */
  private Integer number;

  /**
   * 干扰
   */
  private String disturb;

  /**
   * 训练开始时间
   */
  private LocalDateTime startTime;

  /**
   * 训练结束时间
   */
  private LocalDateTime endTime;

  /**
   * 训练耗时
   */
  private Integer duration;

  /**
   * 训练内容
   */
  private String content;


  /**
   * 正确率
   */
  private BigDecimal accuracy;


  /**
   * 错误个数
   */
  private Integer errorNumber;


  /**
   * 正确个数
   */
  private Integer passNumber;

  /**
   * 得分
   */
  private BigDecimal score;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();
}
