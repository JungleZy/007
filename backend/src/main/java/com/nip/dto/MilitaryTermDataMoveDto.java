package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-06-24 10:55
 * @Description:
 */
@Data
@Schema(name = "军语密语移动DTO")
@RegisterForReflection
public class MilitaryTermDataMoveDto {

  @Schema(name = "移动对象")
  private String sourceId;

  @Schema(name = "目标位置对象")
  private String targetId;

  @Schema(name = "移动方式 0:从下往上 1:从上往下")
  private Integer type;
}
