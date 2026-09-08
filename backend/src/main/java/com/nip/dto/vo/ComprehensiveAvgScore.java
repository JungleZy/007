package com.nip.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @Author: wushilin
 * @Data: 2022-04-02 09:10
 * @Description:
 */
@Data
//@ApiModel("平均分")
@AllArgsConstructor
@NoArgsConstructor
public class ComprehensiveAvgScore {

  //@ApiModelProperty(value = "月份")
  private String month;

  //@ApiModelProperty(value = "平均分")
  private BigDecimal avgScore;

}
