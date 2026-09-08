package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 11:54
 * @Description:
 */
@Data
@Schema(title = "完成/暂停训练")
public class PostEnteringExerciseFinishParam {

  @Schema(title = "id")
  private String id;

  @Schema(title = "正确率")
  private Double accuracy;

  @Schema(title = "速度")
  private Integer speed;

  @Schema(title = "训练时长")
  private Integer duration;

  @Schema(title = "训练内容")
  private String content;

  @Schema(title = "正确个数")
  private Integer correctNum;

  @Schema(title = "错误个数")
  private Integer errorNum;

}
