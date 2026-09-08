package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-06-06 16:10
 * @Description:
 */
@Schema(title = "电传拍发查询对象")
@Data
public class TelexPatTrainQueryParam {

  @Schema(title = " 0 数字训练 1字母训练 2组合训练")
  private Integer type;
}
