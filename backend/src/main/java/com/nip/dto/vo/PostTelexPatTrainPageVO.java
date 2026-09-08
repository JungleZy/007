package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-03-16 09:28
 * @Description:
 */
@Data
@Schema(title = "岗位数据报、电传拍发 分页VO")
public class PostTelexPatTrainPageVO {

  @Schema(title = "id")
  private String id;

  /**
   * 训练id
   */
  @Schema(title = "训练id")
  private String trainId;

  /**
   * 生成的key
   */
  @Schema(title = "生成的key")
  private String key;

  /**
   * 用户拍发得内容
   */
  @Schema(title = "用户拍发的内容")
  private String value;


  /**
   * 页码
   */
  @Schema(title = "页码")
  private Integer pageNumber;


  /**
   * 排序字段
   */
  @Schema(title = "排序字段")
  private Integer sort;

}
