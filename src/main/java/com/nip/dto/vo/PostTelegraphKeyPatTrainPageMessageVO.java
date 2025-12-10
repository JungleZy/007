package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-03-15 14:33
 * @Description:
 */
@Data
@Schema(title = "电子键拍发内容")
public class PostTelegraphKeyPatTrainPageMessageVO {

  @Schema(title = "id")
  private Integer id;

  /**
   * 训练id
   */
  @Schema(title = "训练id")
  private String trainId;


  /**
   * 页码
   */
  @Schema(title = "页码")
  private Integer pageNumber;

  /**
   * 生成得key
   */
  @Schema(title = "生成得key")
  private String key;

  /**
   * 用户拍发得内容
   */
  @Schema(title = "用户拍发得内容")
  private String value;

  /**
   * 拍发的时间
   */
  @Schema(title = "拍发的时间")
  private String time;

  /**
   * 排序字段
   */
  @Schema(title = "排序字段")
  private Integer sort;
}
