package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@Schema(title = "更改训练状态")
@RegisterForReflection
public class GeneralTelexPathUpdateStatusParam {
  @Schema(title = "0未开始，1，进行中，2已完成")
  private Integer status;
  @Schema(title = "训练id")
  private String trainId;
}
