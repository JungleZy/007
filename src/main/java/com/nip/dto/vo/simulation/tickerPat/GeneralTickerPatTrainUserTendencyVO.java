package com.nip.dto.vo.simulation.tickerPat;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author: wushilin
 * @Data: 2023-04-11 10:34
 * @Description:
 */
@Data
//@ApiModel(value = "训练用户态势")
public class GeneralTickerPatTrainUserTendencyVO {

  //@ApiModelProperty(value = "用户id")
  private String userId;

  //@ApiModelProperty(value = "用户名称")
  private String userName;

  //@ApiModelProperty(value = "用户头像")
  private String userImg;

  //@ApiModelProperty(value = "上次分数")
  private BigDecimal lastScore;

  //@ApiModelProperty(value = "上次分数")
  private BigDecimal lastLastScore;

  //@ApiModelProperty(value = "本次分数")
  private BigDecimal thisScore;

}
