package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 09:12
 * @Description:
 */
@Schema(name = "报话训练VO")
@Data
public class RadiotelephoneVO {

  /**
   * 用户id
   */
  @Schema(name = "用户id")
  private String userId;


  /**
   * 0 通报用语 1 军语密语
   */
  @Schema(name = "0 通报用语 1 军语密语")
  private Integer type;

  /**
   * 总时长
   */
  @Schema(name = "总时长")
  private String totalTime;

  /**
   * 训练次数
   */
  @Schema(name = "训练次数")
  private Integer totalCount;

}
