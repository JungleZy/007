package com.nip.dto.general.statistic;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2024-05-20 10:31
 * @Description:
 */
@Data
@Schema(title = "电子键组训错情统计")
@RegisterForReflection
public class GeneralTelexPatTrainErrorCollect {

  @Schema(title = "错码")
  private int errorCodeNumber = 0;
  @Schema(title = "改错")
  private int correctMistakesNumber = 0;

  @Schema(title = "不归")
  private int nonStandartNumber = 0;

  @Schema(title = "多少组")
  private int muchLessGroupsNumber = 0;

  @Schema(title = "少回行")
  private int lessReturnLineNumber = 0;

  @Schema(title = "多少行")
  private int muchLessLineNumber = 0;

  @Schema(title = "多少码")
  private int muchLessCodeNumber = 0;

  @Schema(title = "少页标")
  private int lessPageNumber = 0;

  @Schema(title = "标页错")
  private int errorPageNumber = 0;

}
