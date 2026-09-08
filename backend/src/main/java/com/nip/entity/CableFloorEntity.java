package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_cable_floor")
@Cacheable(value = false)
public class CableFloorEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Schema(title = "编号", required = true, type = SchemaType.STRING)
  private String id;
  @Schema(title = "报文编号", type = SchemaType.STRING)
  private String cableId;
  @Schema(title = "类型编号", type = SchemaType.STRING)
  private String typeId;
  @Schema(title = "页排序", type = SchemaType.INTEGER)
  private Integer floorNumber;
  @Schema(title = "组排序", type = SchemaType.INTEGER)
  private Integer sort;
  @Schema(title = "内容", type = SchemaType.STRING)
  private String moresKey;
}
