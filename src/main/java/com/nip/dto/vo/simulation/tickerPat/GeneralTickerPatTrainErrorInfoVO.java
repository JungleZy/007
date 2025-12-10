package com.nip.dto.vo.simulation.tickerPat;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author: wushilin
 * @Data: 2023-04-11 10:24
 * @Description:
 */
@Data
@RegisterForReflection
public class GeneralTickerPatTrainErrorInfoVO {

  //@ApiModelProperty(value = "点虚")
  private BigDecimal dotMin;

  //@ApiModelProperty(value = "点粗")
  private BigDecimal dotMax;

  //@ApiModelProperty(value = "划虚")
  private BigDecimal lineMin;

  //@ApiModelProperty(value = "划粗")
  private BigDecimal lineMax;

  //@ApiModelProperty(value = "码间隔虚")
  private BigDecimal codeGapMin;

  //@ApiModelProperty(value = "码间隔粗")
  private BigDecimal codeGapMax;

  //@ApiModelProperty(value = "字间隔虚")
  private BigDecimal wordGapMin;

  //@ApiModelProperty(value = "字间隔粗")
  private BigDecimal wordGapMax;

  //@ApiModelProperty(value = "组间隔虚")
  private BigDecimal groupGapMin;

  //@ApiModelProperty(value = "组间隔粗")
  private BigDecimal groupGapMax;

}
