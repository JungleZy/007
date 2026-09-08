package com.nip.dto.vo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/29 8:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(title = "电子键拍发综合训练VO")
public class TelegraphKeyPatSyntheticalVO {

  @Schema(title ="id")
  private String id;

  /**
   * 标题
   */
  @Schema(title ="标题")
  private String title;

  /**
   *  0 未开始 1 进行中 2 暂停 3 完成
   */
  @Schema(title ="0 未开始 1 进行中 2 暂停 3 完成")
  private Integer status;

  /**
   * 时长
   */
  @Schema(title ="时长")
  private String duration;

  /**
   * // 全报文数量
   */
  @Schema(title ="全报文数量")
  private Integer totalNumber;

  @Schema(title = "训练报文 0数码 1字码 2混合码 ")
  private Integer messageType;

  /**
   * // 错误数量
   */
  @Schema(title ="错误数量")
  private Integer errorNumber;

  /**
   *正确率
   */
  @Schema(title ="正确率")
  private Double accuracy;

  /**
   *速率
   */
  @Schema(title ="速率")
  private String speed;


  /**
   * 报文内容
   */
  @Schema(title ="报文内容")
  private String content;
}
