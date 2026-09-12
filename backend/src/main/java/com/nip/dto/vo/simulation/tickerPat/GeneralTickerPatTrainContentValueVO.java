package com.nip.dto.vo.simulation.tickerPat;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nip.common.utils.StrictIntegerDeserializer;
import com.nip.dto.CaptureInterval;
import com.nip.dto.vo.param.simulation.tickerPat.GeneralTickerPatTrainContentAddParam;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 17:21
 * @Description:
 */
//@ApiModel("记录手机键拍发内容")
@Data
@RegisterForReflection
public class GeneralTickerPatTrainContentValueVO {
  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  private Integer attempt;
  private List<CaptureInterval> captureIntervals;


  /**
   * 训练id
   */
  //@ApiModelProperty(value = "训练id",position = 1)
  private Integer trainId;

  /**
   * 报底页数
   */
  //@ApiModelProperty(value = "报底页数",position = 2)
  private Integer floorNumber;

  /**
   * 客户按下松开的时间
   */
  //@ApiModelProperty(value = "报文内容",position = 3)
  private List<GeneralTickerPatTrainContentAddParam> messageBody;


  //@ApiModelProperty(value = "基准值",position = 4)
  private List<GeneralTickerPatTrainFinishInfoVO> standard;

  //@ApiModelProperty(value = "完成信息",position = 5)
  private String finishInfo;


}
