package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-04-21 09:17
 * @Description:
 */
@Schema(title = "添加要点")
@Data
public class EnteringKeyPointsAddParam {
  @Schema(title = "id")
  private String id;

  @Schema(title = "类型 0:收报方法 1情况处置 2常见毛病和修正方法")
  private Integer type;

  @Schema(title = "要点内容")
  private String content;

}
