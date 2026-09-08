package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@RegisterForReflection
public class TelexPatStatisticalDto {
  @Schema(title = "拍发码数")
  private int pat = 0;
  @Schema(title = "拍发组数")
  private int patGroup = 0;
  @Schema(title = "错码")
  private int errorCodeNumber = 0;
  @Schema(title = "改错")
  private int correctMistakesNumber = 0;
  @Schema(title = "不规")
  private int nonStandartNumber = 0;
  @Schema(title = "多少行")
  private int muchLessLineNumber = 0;
  @Schema(title = "多少组")
  private int muchLessGroupsNumber = 0;
  @Schema(title = "多少码")
  private int muchLessCodeNumber = 0;
  @Schema(title = "少回行")
  private int lessReturnLineNumber = 0;
  @Schema(title = "少页标")
  private int lessPageNumber = 0;
  @Schema(title = "标页错")
  private int errorPageNumber = 0;
}
