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
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(title = "更新训练对象")
public class PostTickerTapeTrainUpdateParam implements Serializable {
  @Schema(title = "id")
  private String id;

  /**
   * 有效时长
   */
  @Schema(title = "有效时长")
  private String validTime;


}
