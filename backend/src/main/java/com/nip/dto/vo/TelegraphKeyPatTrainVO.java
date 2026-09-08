package com.nip.dto.vo;


import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-04-21 09:56
 * @Description:
 */
@Data
@Schema(title = "电子键拍发-单字拍发VO")
public class TelegraphKeyPatTrainVO {

  @Schema(title = "id")
  private String id;

  /**
   * 0 数字练习 1 特殊键练习
   */
  @Schema(title = "0 基础练习 1 单字练习")
  private Integer type;

  /**
   * 训练时长
   */
  @Schema(title = "训练时长")
  private Integer totalTime;

  /**
   * 训练次数
   */
  @Schema(title = "训练次数")
  private Integer totalNum;

  /**
   * 错误次数
   */
  @Schema(title = "错误次数")
  private Integer totalError;

}
