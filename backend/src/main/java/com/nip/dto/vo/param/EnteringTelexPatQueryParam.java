package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-04-21 11:15
 * @Description:
 */
@Data
@Schema(title = "查询对象")
public class EnteringTelexPatQueryParam {

  @Schema(title = "0 口诀练习 1 字根练习 2 拆字练习")
  private Integer type;
}
