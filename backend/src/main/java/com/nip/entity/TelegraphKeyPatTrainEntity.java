package com.nip.entity;


import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-04-21 09:56
 * @Description:
 */
@Data
@Entity(name = "t_telegraph_key_pat_train")
@Accessors(chain = true)
@Cacheable(value = false)
public class TelegraphKeyPatTrainEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 创建人ID
   */
  private String createUserId;

  /**
   * 0 基础练习 1 单字练习
   */
  private Integer type;

  /**
   * 训练时长
   */
  private Integer totalTime;

  /**
   * 拍发次数
   */
  private Integer totalNum;

  /**
   * 错误次数
   */
  private Integer totalError;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();
}
