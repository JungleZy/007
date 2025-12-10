package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 09:28
 * @Description:
 */
@Data
@Entity(name = "t_post_entering_exercise")
@Cacheable(value = false)
public class PostEnteringExerciseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 类型 0 拼音文章 1 拼音军语 2五笔文章 3五笔军语 4 英语文章
   */
  private Integer type;

  /**
   * 创建人id
   */
  private String createUserId;

  /**
   * 开始时间
   */
  private LocalDateTime startTime;

  /**
   * 结束时间
   */
  private LocalDateTime endTime;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();


  /**
   * 正确率
   */
  private Double accuracy;

  /**
   * 速度
   */
  private Integer speed;

  /**
   * 文本内容
   */
  private String content;

  /**
   * 训练时长
   */
  private Integer duration;

  /**
   * 状态 0未开始 1进行中 2结束
   */
  private Integer status;

  /**
   * 训练名称
   */
  private String name;

  /**
   * 错误个数
   */
  private Integer errorNum;

  /**
   * 正确个数
   */
  private Integer correctNum;
}
