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
  /**
   * 采集协议版本与本轮次，两者在生产库都是 {@code int NOT NULL DEFAULT 0}
   * （迁移 backend/database/migrations/2026-09-11-04-personal-handkey-capture.sql:80-81）。
   *
   * <p>声明 {@code nullable = false} 的理由与 {@code PostTelegramTrainContentFloorValueEntity.attempt}
   * 完全相同（见该字段注释）：{@code %test} 走 drop-and-create、建表只看实体声明，不声明就与 {@code %prod}
   * 的 NOT NULL 分歧，漏设的写路径「测试过、生产炸」；而迁移里的 {@code DEFAULT 0} 因本实体无
   * {@code @DynamicInsert} 恒不生效，不会兜住漏设。
   *
   * <p>当前生产写路径都显式置值（`PostTelegramTrainService.add` 的 `setProtocolVersion(1)`/`setAttempt(0)`），
   * 所以这里是声明对齐，不改变运行行为。
   */
  @Column(nullable = false)
  private Integer protocolVersion;
  @Column(nullable = false)
  private Integer attempt;
  private Integer fullScore;
  private Long activeMillis;

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
   * 0未开始，1，进行中，2未完成已暂停，3已完成
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
  @Column(columnDefinition = "LONGTEXT")
  private String ruleContent;

  private String score;

  /**
   * 统计信息 点、划、间隔
   */
  @Column(columnDefinition = "LONGTEXT")
  private String statisticInfo;

  /**
   * 漏拍
   */
  private Integer lack;

  /**
   * 扣分详情
   */
  @Column(columnDefinition = "LONGTEXT")
  private String deductInfo;

  /**
   * 是否平均0否 1是
   */
  private Integer isAverage;

}
