package com.nip.dto;


import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * @Author: wushilin
 * @Data: 2023-08-22 11:36
 * @Description:
 */
@Data
@Schema(title = "提交答案")
@RegisterForReflection
public class GroupNetTrainSubmitAnswerDto {
  @Schema(title = "id")
  private Integer id;

  @Schema(title = "答案")
  private String answer;

  @Schema(title = "得分")
  private BigDecimal score;
}
