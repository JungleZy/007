package com.nip.entity.simulation.telex;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "general_telex_pat") //对应的数据库表
@Cacheable(value = false)
public class GeneralTelexPatEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Schema(title = "编号", required = true, type = SchemaType.STRING)
  private String id;

  /**
   * 训练名称
   */
  private String title;

  @Schema(title = "是否固定报0 不 1是")
  private Integer isCable;

  /**
   * 类型 0 数字连贯 1 字母连贯 2 组合连贯
   */
  private Integer type;

  /**
   * 训练类型 0 数据报 1 电传训练
   */
  private Integer trainType;

  /**
   * 报底类型，0 挨指报 1 对手报 2 随机报
   */
  private Integer patType;

  /**
   * 报底数量
   */
  private Integer totalNumber;

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
   * 0未开始，1，进行中，2已完成
   */
  private Integer status;

  /**
   * 评分规则Id
   */
  private String ruleId;

  /**
   * 评分规则信息
   */
  private String ruleContent;

  /**
   * 创建人ID
   */
  private String createUser;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();
}
