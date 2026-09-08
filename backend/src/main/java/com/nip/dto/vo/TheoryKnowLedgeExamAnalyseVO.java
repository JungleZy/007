package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-02-27 14:42R
 * @Description:
 */

@Data
@Schema(name = "考试分析Vo")
public class TheoryKnowLedgeExamAnalyseVO {

  @Schema(name = "scoreList",title = "成绩列表")
  private List<TheoryKnowLedgeExamUserVO> scoreList;

  @Schema(name = "errorTop3",title = "错题TOP3")
  private List<TheoryKnowledgeQuestionErrorTopVO> errorTop3;

  @Schema(name = "failing",title = "不及格")
  private Integer failing;

  @Schema(name = "ordinary",title = "良 （60-80）")
  private Integer ordinary;

  @Schema(name = "good",title = "优秀")
  private Integer good;
}
