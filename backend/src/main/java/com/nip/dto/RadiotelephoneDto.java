package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 09:31
 * @Description:
 */
@Schema(name = "报话训练DTO")
@Data
@RegisterForReflection
public class RadiotelephoneDto {
  /**
   * 0 通报用语 1 军语密语
   */
  @Schema(name = "0 通报用语 1 军语密语")
  private Integer type;

  /** Server-issued study identity returned by begin and echoed by lifecycle calls. */
  @Schema(name = "server-issued study session id")
  private String sessionId;
}

