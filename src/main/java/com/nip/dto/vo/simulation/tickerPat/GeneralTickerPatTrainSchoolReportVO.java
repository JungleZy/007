package com.nip.dto.vo.simulation.tickerPat;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2023-04-11 10:18
 * @Description:
 */
@Data
//@ApiModel(value = "排名")
@RegisterForReflection
public class GeneralTickerPatTrainSchoolReportVO {

  //@ApiModelProperty(value = "优秀 90分以上")
  private GeneralTickerPatTrainScoreInfoVO good;


  //@ApiModelProperty(value = "良好 70-90分")
  private GeneralTickerPatTrainScoreInfoVO nice;


  //@ApiModelProperty(value = "差 70分一下")
  private GeneralTickerPatTrainScoreInfoVO belowStandard;

}
