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
@Entity(name = "t_masthead")
@Cacheable(value = false)
public class MastheadEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Schema(title = "编号", required = true, type = SchemaType.STRING)
  private String id;
  @Schema(title = "训练编号", type = SchemaType.STRING)
  private String trainId;
  @Schema(title = "报头内容", type = SchemaType.STRING)
  private String content;
}
