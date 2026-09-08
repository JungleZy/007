package com.nip.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2023-08-22 10:25
 * @Description:
 */
@Data
@Entity(name = "group_net_train")
public class GroupNetTrainEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 设备类型
   */
  private Integer deviceType;

  /**
   * 设备id
   */
  private Integer deviceId;

  /**
   * 题目
   */
  private String topic;

  /**
   * 答案
   */
  private String answer;

  /**
   * 评分规则
   */
  private String scoringRuleContent;


  /**
   * 得分
   */
  private BigDecimal score;


  /**
   * 冗余字段
   */
  private String content;

  /**
   * 创建人
   */
  private String createUser;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();
}
