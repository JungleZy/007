package com.nip.dto.vo.simulation.tickerPat;

import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2023-04-08 19:21
 * @Description:
 */
@Data
public class GeneralTickerPatTrainUserValueVO {
  /**
   * 报底页数
   */
  private Integer floorNumber;

  /**
   * 客户按下松开的时间
   */
  private String messageBody;

  /**
   * 基准值
   */
  private String standard;

  /**
   * 完成信息
   */
  private String finishInfo;
}
