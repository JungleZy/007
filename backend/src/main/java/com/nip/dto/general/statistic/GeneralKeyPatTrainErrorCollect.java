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
public class GeneralKeyPatTrainErrorCollect {

  @Schema(title = "错码")
  private int errorNumber = 0;

  @Schema(title = "少码")
  private int lackNumber = 0;

  @Schema(title = "多码")
  private int moreNumber = 0;

  @Schema(title = "少组")
  private int lackLineNumber= 0;

  @Schema(title = "多组")
  private int moreLineNumber = 0;

  @Schema(title = "少行")
  private int lackGroupNumber = 0;

  @Schema(title = "多行")
  private int moreGroupNumber = 0;

}
