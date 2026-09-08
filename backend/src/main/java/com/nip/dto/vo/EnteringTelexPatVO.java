package com.nip.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-04-21 10:18
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(name = "汉字录入五笔训练VO")
public class EnteringTelexPatVO {

  @Schema(name = "id")
  private String id;

  /**
   * 创建人ID
   */
  @Schema(name = "创建人ID")
  private String createUserId;

  /**
   * 0 口诀练习 1字根练习 2 拆字练习
   */
  @Schema(name = "0 口诀练习 1字根练习 2 拆字练习")
  private Integer type;

  /**
   * 训练时长
   */
  @Schema(name = "训练时长")
  private Integer totalTime;

  /**
   * 训练次数
   */
  @Schema(name = "训练次数")
  private Integer totalNum;

  /**
   * 错误次数
   */
  @Schema(name = "错误次数")
  private Integer totalError;

  /**
   * 报文类型名称
   */
  @Schema(name = "报文类型名称")
  private String messageName;

}
