package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-04-21 09:56
 * @Description:
 */
@Data
@Entity(name = "t_entering_telex_pat")
@Cacheable(value = false)
public class EnteringTelexPatEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 创建人ID
   */
  private String createUserId;

  /**
   * 0 口诀练习 1字根练习 2 拆字练习
   */
  private Integer type;

  /**
   * 训练时长
   */
  private Integer totalTime;

  /**
   * 训练次数
   */
  private Integer totalNum;

  /**
   * 错误次数
   */
  private Integer totalError;

  /**
   * 报文类型名称
   */
  private String messageName;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();
}
