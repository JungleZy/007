package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 手键错误表
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "hand_key_err_log") //对应的数据库表
@Cacheable(value = false)
public class HandKeyErrLogEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "id", nullable = false)
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 训练id
   */
  @Column(name = "train_id")
  private String trainId;

  /**
   * 参数
   */
  @Column(name = "param")
  private String param;

  /**
   * 用户id
   */
  @Column(name = "user_id")
  private String userId;

  /**
   * 报错信息
   */
  @Column(name = "exeception_message")
  private String execeptionMessage;

  /**
   * 报错时间
   */
  @Column(name = "create_time")
  private LocalDateTime createTime = LocalDateTime.now();

}
