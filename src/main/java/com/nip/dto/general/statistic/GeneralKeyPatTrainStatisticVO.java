package com.nip.dto.general.statistic;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-04-11 10:14
 * @Description:
 */
@Data
@Schema(title = "统计信息")
@RegisterForReflection
public class GeneralKeyPatTrainStatisticVO {

  @Schema(title = "成绩分布")
  private GeneralPatTrainSchoolReportVO schoolReport;

  @Schema(title = "参训人拍发态势")
  private List<GeneralPatTrainUserTendencyVO> userTendencyVO;

  @Schema(title = "错误统计")
  private GeneralKeyPatTrainErrorCollect errorCollect;

}
