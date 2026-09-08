package com.nip.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/29 8:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_telex_pat_train") //对应的数据库表
@Cacheable(value = false)
public class TelexPatTrainEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 类型（0数字连贯，1字母连贯，2组合连贯
   */
  private Integer type;
  /**
   * 报文组数
   */
  private Integer numbs;
  private String title;
  /**
   *  0 未开始 1 进行中 2 暂停 3 完成
   */
  private Integer status = 0;
  /**
   * 时长
   */
  private String duration;
  /**
   * // 全报文数量
   */
  private Integer totalNumber = 0;
  /**
   * // 错误数量
   */
  private Integer errorNumber = 0;
  /**
   *正确率
   */
  private Integer accuracy = 0;
  /**
   *速率
   */
  private String speed;
  private String createUserId;
  private String createTime = String.valueOf(new Date().getTime());
  private String content;
}
