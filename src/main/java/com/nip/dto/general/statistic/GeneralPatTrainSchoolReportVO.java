package com.nip.dto.general.statistic;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-04-11 10:18
 * @Description:
 */
@Data
@Schema(title = "排名")
@RegisterForReflection
public class GeneralPatTrainSchoolReportVO {

  @Schema(title = "优秀 90分以上")
  private GeneralPatTrainScoreInfoVO good;


  @Schema(title = "良好 70-90分")
  private GeneralPatTrainScoreInfoVO nice;


  @Schema(title = "差 70分一下")
  private GeneralPatTrainScoreInfoVO belowStandard;

}
