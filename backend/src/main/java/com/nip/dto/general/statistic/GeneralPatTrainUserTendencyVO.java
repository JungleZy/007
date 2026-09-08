package com.nip.dto.general.statistic;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * @Author: wushilin
 * @Data: 2023-04-11 10:34
 * @Description:
 */
@Data
@Schema(title = "训练用户态势")
@RegisterForReflection
public class GeneralPatTrainUserTendencyVO {

  @Schema(title = "用户id")
  private String userId;

  @Schema(title = "用户名称")
  private String userName;

  @Schema(title = "用户头像")
  private String userImg;

  @Schema(title = "上次分数")
  private BigDecimal lastScore;

  @Schema(title = "上上次分数")
  private BigDecimal lastLastScore;

  @Schema(title = "本次分数")
  private BigDecimal thisScore;
  @Schema(title = "训练状态")
  private Integer status;

}
