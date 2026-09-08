package com.nip.common.constants;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 16:57
 * @Description:
 */

public enum  TickerTapeTrainStatusEnum {
  //未开始
  NOT_STARTED(0,"未开始"),
  //
  UNDERWAY(1,"进行中"),
  //
  PAUSE(2,"暂停"),
  //
  FINISH(3,"结束")
  ;

  /**
   *0:未开始 1:进行中 2：暂停 3：结束
   */
  private Integer code;
  /**
   * 名称
   */
  private String name;

  TickerTapeTrainStatusEnum(Integer code, String name) {
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
