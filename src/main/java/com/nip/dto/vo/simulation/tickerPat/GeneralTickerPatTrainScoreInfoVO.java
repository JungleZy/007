package com.nip.dto.vo.simulation.tickerPat;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author: wushilin
 * @Data: 2023-04-11 10:10
 * @Description:
 */
@Data
//@ApiModel(value = "成绩分布")
@RegisterForReflection
public class GeneralTickerPatTrainScoreInfoVO {

  //@ApiModelProperty(value = "人数")
  private Integer peopleNumber;

  //@ApiModelProperty(value = "占比")
  private BigDecimal rate;
}
