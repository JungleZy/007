package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * @Author: wushilin
 * @Data: 2024-06-05 10:17
 * @Description:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RegisterForReflection
public class AvgResult {
  @Schema(title = "本次平均成绩")
  private BigDecimal thisAvgResult;

  @Schema(title = "上次平均成绩")
  private BigDecimal lastAvgResult;
}
