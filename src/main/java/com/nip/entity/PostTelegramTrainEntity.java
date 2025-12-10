package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-05-05 11:19
 * @Description: 岗位训练-手键拍发实体类
 */
@Entity(name = "t_post_telegram_train")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Cacheable(value = false)
public class PostTelegramTrainEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 训练名称
   */
  private String name;

  /**
   * 是否是固定报 0 固定报 1 随机报
   */
  private Integer isCable;

  /**
   * 类型 0 数码报 1 字码报 2 混合报
   */
  private Integer type;

  /**
   * 短码长码 0 短码 1 长码
   */
  private Integer codeSort;

  /**
   * 是否随机 0否 1是
   */
  private Integer isRandom;

  /**
   * 报底
   */
  private Integer messageNumber;


  /**
   * 开始时间
   */
  private LocalDateTime startTime;

  /**
   * 结束时间
   */
  private LocalDateTime endTime;

  /**
   * 有效时长
   */
  private Long validTime;

  /**
   * 速率
   */
  private String speed;

  private String speedLog;

  /**
   *  0未开始，1，进行中，2未完成已暂停，3已完成
   */
  private Integer status;

  /**
   * 创建人ID
   */
  private String createUser;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();

  /**
   * 错误个数
   */
  private Integer errorNumber;

  /**
   * 正确率
   */
  private String accuracy;

  /**
   * 当前报底编号
   */
  private Integer floorNow;


  /**
   * 完成信息，用于统计使用
   */
  private String finishInfo;

  /**
   * 评分规则Id
   */
  private String ruleId;

  /**
   * 评分规则信息
   */
  private String ruleContent;

  private String score;

  /**
   * 统计信息 点、划、间隔
   */
  private String statisticInfo;


  /**
   * 漏拍
   */
  private Integer lack;

  /**
   * 扣分详情
   */
  private String deductInfo;

  /**
   * 是否平均0否 1是
   */
  private Integer isAverage;

}
