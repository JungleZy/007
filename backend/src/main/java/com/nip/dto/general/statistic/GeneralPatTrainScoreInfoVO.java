package com.nip.dto.general.statistic;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * @Author: wushilin
 * @Data: 2023-04-11 10:10
 * @Description:
 */
@Data
@Schema(title = "成绩分布")
@RegisterForReflection
public class GeneralPatTrainScoreInfoVO {

  @Schema(title = "人数")
  private Integer peopleNumber;

  @Schema(title = "占比")
  private BigDecimal rate;
}
