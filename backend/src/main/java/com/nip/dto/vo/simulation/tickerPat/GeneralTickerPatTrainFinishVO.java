package com.nip.dto.vo.simulation.tickerPat;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 09:59
 * @Description:
 */
//@ApiModel("岗位训练-手键拍发完成参数")
@Data
@RegisterForReflection
public class GeneralTickerPatTrainFinishVO {

  //@ApiModelProperty(value = "id",position = 1)
  private Integer id;

  //@ApiModelProperty(value = "userId",position = 2)
  private String userId;

  /*@ApiModelProperty(value = "完成内容",position = 3)
  private List<GeneralTickerPatTrainFinishInfoVO> finishInfo;*/


}
