package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

/**
 * @Author: wushilin
 * @Data: 2022-04-11 09:27
 * @Description:
 */
@Schema(title = "添加要点")
@Data
public class ReceiveKeyPointsAddParam implements Serializable {

  @Schema(title = "id")
  private String id;

  @Schema(title = "类型")
  private Integer type;

  @Schema(title = "要点内容")
  private String content;

}
