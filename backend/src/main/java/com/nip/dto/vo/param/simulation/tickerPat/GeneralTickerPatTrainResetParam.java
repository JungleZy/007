package com.nip.dto.vo.param.simulation.tickerPat;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nip.common.utils.StrictIntegerDeserializer;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2023-04-11 10:01
 * @Description:
 */
@Data
public class GeneralTickerPatTrainResetParam {
  private Integer id;

  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  private Integer attempt;
}
