package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2023-02-21 15:20
 * @Description:
 */
@Data
public class PostTickerTapeTrainSettingAddParamList implements Serializable {
  @Schema(title = "id")
  private String id;

  @Schema(title = "类型")
  private String type;

  @Schema(title = "速率(码/分)")
  private Integer rate;

  @Schema(title = "文案")
  private String text;

  @Schema(title = "添加时间")
  private LocalDateTime createTime;
}
