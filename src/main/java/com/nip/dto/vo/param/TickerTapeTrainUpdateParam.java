package com.nip.dto.vo.param;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 17:39
 * @Description:
 */
@Schema(title = "更新训练对象")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TickerTapeTrainUpdateParam implements Serializable {
  @Schema(title = "id")
  private String id;

  /**
   * 有效时长
   */
  @Schema(title = "有效时长")
  private String validTime;

  @Schema(title = "听取位置")
  private String mark;

  @Schema(title = "进度")
  private Integer schedule;


}
