package com.nip.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2023-08-22 11:56
 * @Description:
 */
@Entity(name = "device_scoring_rule")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceScoringRuleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 设备id
   */
  private Integer deviceId;

  /**
   * 评分规则
   */
  private String name;

  /**
   * 评分规则
   */
  private String ruleContent;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();


}
