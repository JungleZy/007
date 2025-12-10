package com.nip.common.constants;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 16:57
 * @Description:
 */

public enum TickerTapeTrainTypeEnum {
  //未开始
  WORD(0,"字码报"),
  //
  NUMBER(1,"数码报"),
  //
  BLEND(2,"混合报"),
  //
  BASE(21,"基础"),

  COD(22,"科式")
  ;

  /**
   *0:未开始 1:进行中 2：暂停 3：结束
   */
  private Integer code;
  /**
   * 名称
   */
  private String name;

  TickerTapeTrainTypeEnum(Integer code, String name) {
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
