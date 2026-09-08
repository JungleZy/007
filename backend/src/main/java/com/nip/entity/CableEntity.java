package com.nip.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_cable")
@Cacheable(value = false)
public class CableEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Schema(title = "编号", required = true, type = SchemaType.STRING)
  private String id;
  @Schema(title = "报文标题", type = SchemaType.STRING)
  private String title;
  @Schema(title = "报文分类编号", type = SchemaType.STRING)
  private String typeId;
  @Schema(title = "报文分类标题", type = SchemaType.STRING)
  private String typeTitle;
  @Schema(title = "报文类型，0:收报，1:发报，2:收发报", type = SchemaType.INTEGER)
  private Integer scope;
  @Schema(title = "0 短码 1 长码", type = SchemaType.INTEGER)
  private Integer floorCount;
  @Schema(title = "组数", type = SchemaType.INTEGER)
  private Integer groupCount;
  @Schema(title = "码数", type = SchemaType.INTEGER)
  private Integer codeCount;
  @Schema(title = "类型 0 数码报 1 字码报 2 混合报", type = SchemaType.INTEGER)
  private Integer codeType;
  @Schema(title = "0 短码 1 长码", type = SchemaType.INTEGER)
  private Integer codeSort;
  @Schema(title = "备注", type = SchemaType.STRING)
  private String remark;
  @Schema(title = "创建时间", type = SchemaType.OBJECT)
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime createTime = LocalDateTime.now();
}
