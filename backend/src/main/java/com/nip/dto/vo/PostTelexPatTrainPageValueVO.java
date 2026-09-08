package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-03-17 09:32
 * @Description:
 */
@Data
@Schema(title  = "每页拍发结果")
public class PostTelexPatTrainPageValueVO {
  /**
   * 训练id
   */
  @Schema(title = "id")
  private String trainId;


  /**
   * 页码
   */
  @Schema(title = "页码")
  private Integer pageNumber;

  /**
   * 拍发内容
   */
  @Schema(title = "拍发内容")
  private String patValue;

  @Schema(title = "有效时长")
  private Integer validTime;

  @Schema(title = "速率")
  private String speed;
}
