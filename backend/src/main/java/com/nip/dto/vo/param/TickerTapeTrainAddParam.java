package com.nip.dto.vo.param;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 16:29
 * @Description:
 */
@Data
@Schema(title = "新增训练计划对象")
@NoArgsConstructor
@AllArgsConstructor
public class TickerTapeTrainAddParam {

  @Schema(title = "id")
  private String id;

  @Schema(title = "速率")
  private Integer rate;

  /**
   * 0:数码报 1 字码报 2 混合报
   */
  @Schema(title = "'0': '字码报','1': '数码报','2': '混合报',21 基础练习 22科室训练")
  private Integer type;

  /**
   * 0：短码 1:长码
   */
  @Schema(title = "0：短码 1:长码")
  private Integer codeShort;

  @Schema(title = "报文内容")
  private List<Map> codeMessageBody;

  @Schema(title = "训练名称")
  private String name;

  @Schema(title = "是否低速训练 0是 1否")
  private Integer isLowRate;

}
