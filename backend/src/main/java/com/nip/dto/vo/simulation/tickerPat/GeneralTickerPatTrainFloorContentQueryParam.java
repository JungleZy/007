package com.nip.dto.vo.simulation.tickerPat;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2022-05-05 17:43
 * @Description:
 */
//@ApiModel("查询训练报文内容参数")
@Data
@RegisterForReflection
public class GeneralTickerPatTrainFloorContentQueryParam {
  //@ApiModelProperty(value = "训练",position = 1)
  private String id;

  //@ApiModelProperty(value = "报底编号",position = 2)
  private Integer floorNumber;
}
