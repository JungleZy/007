package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/29 8:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_telex_pat_train_statistical") //对应的数据库表
@Cacheable(value = false)
public class TelexPatTrainStatisticalEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 类型（0 单字训练 1 数字连贯 2字母连贯 3混合连贯
   */
  private Integer type;

  /**
   * 训练次数
   */
  private Integer totalCount;

  /**
   * 训练总时长
   */
  private String totalTime;

  /**
   * 训练总时长
   */
  private BigDecimal avgSpeed;

  /**
   * 用户id
   */
  private String userId;

}
