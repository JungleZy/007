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
public class EnteringExerciseFinishParam {
  @Schema(title = "id")
  private String id;

  @Schema(title = "客户端预览正确率，不作为评分依据")
  private Double accuracy;

  @Schema(title = "客户端预览速度，不作为评分依据")
  private Integer speed;

  @Schema(title = "客户端预览秒数，不作为计时依据")
  private Integer duration;

  @Schema(title = "JSON数组字符串，每条必须包含实际输入value；题面与正误由服务端重建")
  private String content;

  @Schema(title = "客户端预览正确组数，不作为评分依据")
  private Integer correctNum;

  @Schema(title = "客户端预览错误组数，不作为评分依据")
  private Integer errorNum;

}
