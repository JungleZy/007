package com.nip.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-02-27 14:38
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "考试分析成绩列表")
public class TheoryKnowLedgeExamUserVO {

  @Schema(name = "姓名")
  private String userName;

  @Schema(name = "本次考试分数")
  private Integer currentScore;

  @Schema(name = "上次考试分数")
  private Integer previousScore;


}
