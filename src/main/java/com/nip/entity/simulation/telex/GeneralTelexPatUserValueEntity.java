package com.nip.entity.simulation.telex;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

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
   * 生成的key
   */
  @Column(name = "`key`")
  private String key;
  private String value;
}
