package com.nip.common.constants;

/**
 * @Author: wushilin
 * @Data: 2022-04-20 15:04
 * @Description:
 */
public enum PostEnteringExerciseTypeEnum {
  //x
  DYZ(0,"五笔文章"),
  TZYY(1,"五笔军语"),
  LYCZ(2,"英语文章"),
  JYCZ(3,"拼音军语"),
  ARTICLE(4,"拼音文章")
  ;
  private Integer code;
  private String name;

  PostEnteringExerciseTypeEnum(Integer code, String name) {
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
