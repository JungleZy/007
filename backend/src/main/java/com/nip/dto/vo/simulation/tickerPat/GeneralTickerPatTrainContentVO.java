package com.nip.dto.vo.simulation.tickerPat;

import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainContentAddParam;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-04-08 17:29
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
//@ApiModel(value = "手键拍发报文VO")
@RegisterForReflection
public class GeneralTickerPatTrainContentVO {

  //@ApiModelProperty(value = "报底内容",position = 1)
  private String messageBody;

  //@ApiModelProperty(value = "训练未开始",position = 2)
  private List<GeneralTickerPatTrainContentAddParam> messageKey;

  //@ApiModelProperty(value = "拍发记录",position = 3)
  private String finishInfo;

  //@ApiModelProperty(value = "点划标准值",position = 4)
  private String standard;

  /**
   * 矫正后的报文
   */
  //@ApiModelProperty(value = "",position = 5)
  private String resolver;
}
