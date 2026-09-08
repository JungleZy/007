package com.nip.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/29 8:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "t_telegraph_key_pat_synthetical_train") //对应的数据库表
@Cacheable(value = false)
public class TelegraphKeyPatSyntheticalEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 标题
   */
  private String title;

  /**
   *  0 未开始 1 进行中 2 暂停 3 完成
   */
  private Integer status;

  /**
   * 时长
   */
  private String duration;

  /**
   * // 全报文数量
   */
  private Integer totalNumber;

  /**
   * // 错误数量
   */
  private Integer errorNumber;

  /**
   *正确率
   */
  private Double accuracy;

  /**
   *速率
   */
  private String speed;

  private String createUserId;

  private LocalDateTime createTime = LocalDateTime.now();

  /**
   * 报文内容
   */
  private String content;

  /**
   * 训练报文 0数码 1字码 2混合码
   */
  private Integer messageType;
}
