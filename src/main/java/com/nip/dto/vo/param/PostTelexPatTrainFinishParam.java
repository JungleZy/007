package com.nip.dto.vo.param;

import lombok.Data;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 14:59
 * @Description:
 */
@Data
@Accessors(chain = true)
@Schema(title = "岗位训练-电传拍发完成参数")
public class PostTelexPatTrainFinishParam implements Serializable {

  @Schema(title = "id")
  private String id;

  @Schema(title = "错误个数")
  private Integer errorNumber;

  /**
   * 正确率
   */
  @Schema(title = "正确率")
  private String accuracy;

  /**
   * 速率
   */
  @Schema(title = "速率")
  private String speed;

  /**
   * 训练时长
   */
  @Schema(title = "训练时长")
  private Integer validTime;

  @Schema(title = "内容")
  private String content;
  @Schema(title = "平均速率")
  /**
   * 平均速率（20230606BBB新增）
   */
  private String totalSpeed;
}
