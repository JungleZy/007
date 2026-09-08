package com.nip.common.constants;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 16:57
 * @Description:
 */

public enum TickerTapeTrainStatisticalTypeEnum {
  //未开始
  BASE(0,"基础训练"),
  //
  COD(1,"科式训练"),
  //
  LETTER(2,"单词练习")
  ;

  /**
   *0:未开始 1:进行中 2：暂停 3：结束
   */
  private Integer code;
  /**
   * 名称
   */
  private String name;

  TickerTapeTrainStatisticalTypeEnum(Integer code, String name) {
    this.code = code;
    this.name = name;
  }

  public Integer getCode() {
    return code;
  }

  public void setCode(Integer code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
