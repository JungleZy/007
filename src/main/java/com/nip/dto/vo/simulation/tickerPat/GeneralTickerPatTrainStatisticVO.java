package com.nip.dto.vo.simulation.tickerPat;

import lombok.Data;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-04-11 10:14
 * @Description:
 */
@Data
//@ApiModel(value = "统计信息")
public class GeneralTickerPatTrainStatisticVO {

  //@ApiModelProperty(value = "成绩分布")
  private GeneralTickerPatTrainSchoolReportVO schoolReport;

  //@ApiModelProperty(value = "错情分布")
  private GeneralTickerPatTrainErrorInfoVO errorInfoVO;

  //@ApiModelProperty(value = "参训人拍发态势")
  private  List<GeneralTickerPatTrainUserTendencyVO> userTendencyVO;

}
