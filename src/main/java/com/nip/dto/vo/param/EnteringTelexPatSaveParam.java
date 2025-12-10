package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-04-21 10:26
 * @Description:
 */
@Data
@Schema(title = "添加对象")
public class EnteringTelexPatSaveParam {

  @Schema(title = "id")
  private String id;

  /**
   * 0 口诀练习 1字根练习 2 拆字练习
   */
  @Schema(title = "0 口诀练习 1字根练习 2 拆字练习")
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

  /**
   * 报文类型名称
   */
  @Schema(title = "报文类型名称")
  private String messageName;
}
