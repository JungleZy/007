package com.nip.common.constants;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 16:57
 * @Description:
 */

public enum PostTickerTapeTrainStatusEnum {
  //未开始
  NOT_STARTED(0,"未开始"),
  //
  UNDERWAY(1,"进行中"),
  //
  FINISH(2,"结束"),
  //
  HAS_SCORE(3,"已评分")
  ;

  /**
   *0:未开始 1:进行中 2：暂停 3：结束
   */
  private Integer code;
  /**
   * 名称
   */
  private String name;

  PostTickerTapeTrainStatusEnum(Integer code, String name) {
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
