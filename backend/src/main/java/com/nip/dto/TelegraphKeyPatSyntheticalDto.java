package com.nip.dto;


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
@Schema(title = "电子键拍发综合训练Dto")
public class TelegraphKeyPatSyntheticalDto {

  @Schema(title = "id")
  private String id;

  /**
   * 标题
   */
  @Schema(title = "标题")
  private String title;

  @Schema(title = "训练报文 0数码 1字码 2混合码")
  private Integer messageType;

  /** Client source/answer snapshot. Aggregate metrics are server-derived. */
  @Schema(title = "报文内容与答案行")
  private String content;

}
