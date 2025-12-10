package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-06-24 15:17
 * @Description:
 */
@Data
@Entity(name = "t_post_military_term_train")
@Cacheable(value = false)
public class PostMilitaryTermTrainEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 创建人
   */
  private String userId;

  /**
   * 军语密语类型[ "",""]
   */
  private String types;

  /**
   * 训练名称
   */
  private String name;

  /**
   * 状态
   */
  private Integer status;

  /**
   * 军语数量
   */
  private Integer totalNumber;

  /**
   * 正确数量
   */
  private Integer correctNumber;

  /**
   * 错误数量
   */
  private Integer errorNumber;

  /**
   * 正确率
   */
  private BigDecimal accuracy;

  /**
   * 开始时间
   */
  private LocalDateTime startTime;

  /**
   * 结束时间
   */
  private LocalDateTime endTime;

  /**
   * 训练时长(秒)
   */
  private Integer duration;

  /**
   * 得分
   */
  private BigDecimal score;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();

}
