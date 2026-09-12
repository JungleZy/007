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
@Entity(name = "general_telex_pat_user_value") //对应的数据库表
@Cacheable(value = false)
public class GeneralTelexPatUserValueEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Schema(title = "编号", required = true, type = SchemaType.STRING)
  private String id;
  /**
   * 训练ID
   */
  private String trainId;
  /**
   * 用户ID
   */
  private String userId;
  /**
   * 页码
   */
  private Integer pageNumber;
  /**
   * 排序字段
   */
  private Integer sort;
  /**
   * 本行所属训练轮次；只有原始提交行（{@code sort = -1}）会写，结算产出的分析行为空。
   */
  private Integer attempt;
  /**
   * 原始采集区间（相对成员 {@code captureStartedAt} 的毫秒区间数组 JSON）。
   * 结算只删分析行、不动原始行，这条时间轴一经写入不再被覆盖。
   */
  @Column(columnDefinition = "longtext")
  private String captureIntervals;
  /**
   * 服务端收到该页的时刻，用于校验采集区间不越过采集边界。
   */
  private LocalDateTime receivedAt;
  /**
   * 生成的key
   */
  @Column(name = "`key`")
  private String key;
  /**
   * 原始提交行存的是一整页文本，varchar(255) 装不下；口径与 general 电子键的
   * {@code GeneralKeyPatUserValueEntity#value} 一致。
   */
  @Column(columnDefinition = "longtext")
  private String value;
}
