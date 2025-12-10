package com.nip.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 15:44
 * @Description:
 */
@Data
@Schema(name = "速率配置")
@NoArgsConstructor
@AllArgsConstructor
public class TickerTapeTrainSettingVO implements Serializable {

  @Schema(name = "id")
  private String id;

  @Schema(name = "类型")
  private String type;

  @Schema(name = "速率(码/分)")
  private Integer rate;

  @Schema(name = "文案")
  private String text;

  @Schema(name = "添加时间")
  @JsonFormat(pattern = "yyyy-MM-dd hh:mm:ss",timezone = "GMT+8")
  private LocalDateTime createTime;

}
