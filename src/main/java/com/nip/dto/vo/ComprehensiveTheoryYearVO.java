package com.nip.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-04-01 11:06
 * @Description:
 */
@Data
//@ApiModel("理论年统计信息")
public class ComprehensiveTheoryYearVO {

  //@ApiModelProperty(value = "基础理论",position = 0)
  private BigDecimal baseTheoryCredit;

  //@ApiModelProperty(value = "执掌装备",position = 1)
  private BigDecimal equipCredit;

  //@ApiModelProperty(value = "执行业务",position = 2)
  private BigDecimal workCredit;

  //@ApiModelProperty(value = "年统计信息",position = 3)
  private List<TheoryYearInfoVO> yearInfoVO;


  @Data
  //@ApiModel("年信息")
  @NoArgsConstructor
  @AllArgsConstructor
  public static
  class TheoryYearInfoVO{
    //    @ApiModelProperty(value = "月份",position = 0)
    private String moth;

    //@ApiModelProperty(value = "课件数量",position = 1)
    private Integer swfNum;

    //@ApiModelProperty(value = "学习时长",position = 2)
    private BigDecimal studyTime;

  }


}
