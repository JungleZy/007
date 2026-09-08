package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-05-05 17:43
 * @Description:
 */
@Schema(title = "查询训练报文内容参数")
@Data
public class PostTelegramTrainFloorContentQueryParam {
  @Schema(title = "训练")
  private String id;

  @Schema(title = "报底编号")
  private Integer floorNumber;
}
