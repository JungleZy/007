package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 15:50
 * @Description:
 */
@Data
@Schema(name = "报话训练数据DTO")
@RegisterForReflection
public class PostRadiotelephoneTermDataDto {

  @Schema(name = "0 single 1 frase")
  private Integer type;

  @Schema(name = "数量")
  private Integer number;
}
