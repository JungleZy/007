package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/29 8:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "t_entering_statistical") //对应的数据库表
@Cacheable(value = false)
public class EnteringStatisticalEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 类型（0拼音 1 五笔
   */
  private Integer type;

  /**
   * 0->0异音同字 1 同音异字 2 连音词组 1->0 口诀练习 1 字根练习 2 拆字练习 3连音词组
   */
  private Integer childType;

  /**
   * 训练次数
   */
  private Integer totalCount;

  /**
   * 训练总时长
   */
  private String totalTime;

  /**
   * 平均速率
   */
  private BigDecimal avgSpeed;

  /**
   * 用户id
   */
  private String userId;

}
