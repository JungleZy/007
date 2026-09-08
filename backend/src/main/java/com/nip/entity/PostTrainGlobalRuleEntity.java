package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2023-03-14 09:16
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_post_train_global_rule") //对应的数据库表
@Cacheable(value = false)
public class PostTrainGlobalRuleEntity {

  /**
   * id
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 类型 0 勤务用语 1军语密语 2拼音训练 3英语训练 4五笔训练
   */
  private Integer type;

  /**
   * 级别
   */
  private String level;

  /**
   * 正确率
   */
  private String accuracy;


  /**
   * 描述
   */
  private String description;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();
}
