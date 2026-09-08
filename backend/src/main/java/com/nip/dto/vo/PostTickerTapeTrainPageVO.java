package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-03-21 13:50
 * @Description:
 */
@Data
@Schema(title = "收报训练vo")
public class PostTickerTapeTrainPageVO {
  private Integer id;

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
   * 生成得key
   */
  @Schema(title = "生成得key")
  private String key;



  /**
   * 排序字段
   */
  @Schema(title = "排序字段")
  private Integer sort;


  @Schema(title = "填报内容")
  private String value;

}
