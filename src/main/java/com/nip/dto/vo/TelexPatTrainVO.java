package com.nip.dto.vo;


import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-06-06 16:26
 * @Description:
 */
@Data
@Schema(title = "电传拍发训练VO")
public class TelexPatTrainVO {

  @Schema(title = "id")
  private String id;
  /**
   * 类型（0数字连贯，1字母连贯，2组合连贯
   */
  @Schema(title = "0数字连贯，1字母连贯，2组合连贯")
  private Integer type;
  /**
   * 报文组数
   */
  @Schema(title = "报文组数")
  private Integer numbs;

  @Schema(title = "标题")
  private String title;
  /**
   *  0 未开始 1 进行中 2 暂停 3 完成
   */
  @Schema(title = "0 未开始 1 进行中 2 暂停 3 完成")
  private Integer status = 0;
  /**
   * 时长
   */
  @Schema(title = "时长")
  private String duration;
  /**
   * // 全报文数量
   */
  @Schema(title = "全报文数量")
  private Integer totalNumber = 0;
  /**
   * // 错误数量
   */
  @Schema(title = "错误数量")
  private Integer errorNumber = 0;
  /**
   *正确率
   */
  @Schema(title = "正确率")
  private Integer accuracy = 0;
  /**
   *速率
   */
  @Schema(title = "速率")
  private String speed;

  @Schema(title = "内容")
  private String content;
}
