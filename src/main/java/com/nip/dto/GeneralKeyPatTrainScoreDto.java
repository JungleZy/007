package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(title = "统计某个时段下的电子键考核成绩Dto")
@RegisterForReflection
public class GeneralKeyPatTrainScoreDto {
  private BigDecimal score;
  private String ruleContent;
}
