package com.nip.dto.vo.param;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nip.common.utils.StrictIntegerDeserializer;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 14:36
 * @Description:
 */
@Data
@Schema(title = "岗位训练-电传拍发详情参数")
public class PostTelexPatTrainParam {
  private String id;

  @Schema(title = "训练轮次")
  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  private Integer attempt;

  @Schema(title = "倒计时秒数，null表示不限时", minimum = "1", maximum = "86400")
  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  @JsonSetter(nulls = Nulls.SKIP)
  private Integer countdownSeconds;
}
