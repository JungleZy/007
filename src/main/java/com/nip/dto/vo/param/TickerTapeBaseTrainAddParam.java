package com.nip.dto.vo.param;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 16:29
 * @Description:
 */
@Data
@Schema(title = "新增基础/科式训练计划对象")
@NoArgsConstructor
@AllArgsConstructor
public class TickerTapeBaseTrainAddParam {

  @Schema(title = "id")
  private String id;
  /**
   * 0:数码报 1 字码报 2 混合报
   */
  @Schema(title = "'0': '字码报','1': '数码报','2': '混合报',21 基础练习 22科室训练")
  private Integer type;

  @Schema(title = "时长")
  private String validTime;

  @Schema(title = "轮")
  private String mark;

  @Schema(title = "阶段")
  private Integer  schedule;

}
