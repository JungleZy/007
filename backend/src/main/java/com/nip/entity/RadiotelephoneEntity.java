package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 09:09
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_radiotelephone_train")
// (user_id, type) 是业务唯一键：listPage/finish 在读路径懒建统计行，没有这条唯一约束时
// 两个并发首调会各插一行，统计页重复显示该 type，且后续结算只累加 firstResult() 命中的那行。
// 配套迁移：docs/database/migrations/2026-09-08-01-unique-lazy-create.sql
@Table(name = "t_radiotelephone_train", uniqueConstraints =
    @UniqueConstraint(name = "uk_radiotelephone_train_user_type", columnNames = {"user_id", "type"}))
@Cacheable(value = false)
public class RadiotelephoneEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;


  /**
   * 用户id
   */
  private String userId;


  /**
   * 0 通报用语 1 军语密语
   */
  private Integer type;

  /**
   * 总时长
   */
  private String totalTime;

  /**
   * 训练次数
   */
  private Integer totalCount;

}
