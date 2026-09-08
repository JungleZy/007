package com.nip.dto.vo;


import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-04-02 08:54
 * @Description:
 */
@Data
//@ApiModel("考试年统计信息")
@Accessors(chain = true)
public class ComprehensiveExamYearVO {

//  @ApiModelProperty(value = "每月学习时长")
  private List<ComprehensiveTheoryYearVO.TheoryYearInfoVO> theoryYearInfoVOS;

  //@ApiModelProperty(value = "每月平均分")
  private List<ComprehensiveAvgScore> avgScores;

}
