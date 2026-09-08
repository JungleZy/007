package com.nip.common.constants;

/**
 * @Author: wushilin
 * @Data: 2022-04-20 15:04
 * @Description:
 */
public enum EnteringExerciseTypeEnum {
  //x
  DYZ(0,"异音同字"),
  TZYY(1,"同音异字"),
  LYCZ(2,"连音词组"),
  JYCZ(3,"军语词组"),
  ARTICLE(4,"文章"),
  WBLYCZ(9,"五笔连音词组")
  ;
  private Integer code;
  private String name;

  EnteringExerciseTypeEnum(Integer code, String name) {
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
