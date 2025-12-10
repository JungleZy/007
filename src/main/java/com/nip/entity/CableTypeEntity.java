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
@Entity(name = "t_cable_type")
@Cacheable(value = false)
public class CableTypeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Schema(title = "编号", type = SchemaType.STRING)
  private String id;
  @Schema(title = "报文类型标题", type = SchemaType.STRING)
  private String title;
}
