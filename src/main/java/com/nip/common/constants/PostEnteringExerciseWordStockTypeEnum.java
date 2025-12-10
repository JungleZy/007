package com.nip.common.constants;

/**
 * @Author: wushilin
 * @Data: 2022-06-01 14:34
 * @Description:
 */
public enum  PostEnteringExerciseWordStockTypeEnum {
  //
  WUBI_WORD(0,"五笔文章"),
  WUBI_JY(1,"五笔军语"),
  ENGLISHT_WORD(2,"英语文章"),
  PY_JY(3,"拼音军语"),
  PY_WORD(4,"拼音文章")
  ;
  private Integer type;
  private String name;

  PostEnteringExerciseWordStockTypeEnum(Integer type, String name) {
    this.type = type;
    this.name = name;
  }

  public Integer getType() {
    return type;
  }

  public void setType(Integer type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
