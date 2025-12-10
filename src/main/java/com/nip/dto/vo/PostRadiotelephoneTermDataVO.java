package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 14:42
 * @Description:
 */
@Data
@Schema(name = "岗位报话训练数据VO")
public class PostRadiotelephoneTermDataVO {

  @Schema(name = "id")
  private Integer id;

  /**
   * 0 single 1 frase
   */
  @Schema(name = "0 single 1 frase")
  private Integer type;

  @Schema(name = "key")
  private String key;

  @Schema(name = "value")
  private String value;
  /**
   * 排序字段
   */
  @Schema(name = "排序字段 (已弃用)")
  private Integer sort;

}
