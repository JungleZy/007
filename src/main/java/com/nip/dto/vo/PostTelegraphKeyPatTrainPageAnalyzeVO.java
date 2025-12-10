package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-03-23 16:54
 * @Description: 每页速率个数统计
 */
@Data
@Schema(title = "每页速率个数信息统计")
public class PostTelegraphKeyPatTrainPageAnalyzeVO {

  @Schema(title = "拍发个数")
  private Integer patNumber;

  @Schema(title = "总时长")
  private Integer totalTime;

}
