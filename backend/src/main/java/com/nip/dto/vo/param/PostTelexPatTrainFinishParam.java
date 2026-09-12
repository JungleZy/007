package com.nip.dto.vo.param;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nip.common.utils.StrictIntegerDeserializer;
import lombok.Data;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 14:59
 * @Description:
 */
@Data
@Accessors(chain = true)
@Schema(title = "岗位训练-电传拍发完成参数")
public class PostTelexPatTrainFinishParam implements Serializable {

  @Schema(title = "id")
  private String id;

  @Schema(title = "训练轮次")
  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  private Integer attempt;
}
