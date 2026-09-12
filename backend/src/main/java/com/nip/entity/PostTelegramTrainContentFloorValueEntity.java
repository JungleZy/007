package com.nip.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 17:04
 * @Description:
 */
@Data
@Entity(name = "t_post_telegram_train_floor_content_value")
@Cacheable(value = false)
public class PostTelegramTrainContentFloorValueEntity {
  /**
   * 本轮次，写路径必须显式设置，不能依赖任何兜底。
   *
   * <p>1) 为什么必须声明 {@code nullable = false}：生产库该列是 {@code int NOT NULL DEFAULT 0}
   * （迁移 backend/database/migrations/2026-09-11-04-personal-handkey-capture.sql:84），而 {@code %test}
   * 走 drop-and-create（application.yml:88），建表结构完全由本实体声明推导、与迁移脚本无关。一旦这里不声明，
   * 测试库的 attempt 就是可空列：漏设 attempt 的写路径在测试里能落库、跑绿，到 {@code %prod}（validate，
   * application.yml:125）才因违反 NOT NULL 炸掉。声明它就是让测试库与生产库在这一列上不再分歧。
   *
   * <p>2) 迁移里的 {@code DEFAULT 0} 恒不生效，别指望它兜底：本实体没有 {@code @DynamicInsert}，
   * Hibernate 每条 INSERT 都带全部列，attempt 为 null 时发出的是 {@code attempt = NULL} 而不是省略该列，
   * 列默认值永远走不到。也就是说漏设 attempt 的唯一结果是生产写入失败，不会被静默改写成 0。
   */
  @Column(nullable = false)
  private Integer attempt;
  @Column(columnDefinition = "LONGTEXT")
  private String captureIntervals;
  private LocalDateTime receivedAt;
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 训练id
   */
  private String trainId;

  /**
   * 报底页数
   */
  private Integer floorNumber;

  /**
   * 客户按下松开的时间
   */
  @Column(columnDefinition = "LONGTEXT")
  private String messageBody;

  /**
   * 基准值
   */
  @Column(columnDefinition = "LONGTEXT")
  private String standard;

  /**
   * 完成信息
   */
  @Column(columnDefinition = "LONGTEXT")
  private String finishInfo;

  /**
   * 解析后的报文格式内容
   */
  @Column(columnDefinition = "LONGTEXT")
  private String resolver;

}
