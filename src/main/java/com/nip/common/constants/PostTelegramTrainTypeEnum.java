package com.nip.common.constants;

/**
 * @Author: wushilin
 * @Data: 2022-05-26 13:50
 * @Description:
 */
public enum PostTelegramTrainTypeEnum {
  //数码报
  NUMBER_MESSAGE(0,"数码报"),
  STRING_MESSAGE(1,"字码报"),
  MIX_MESSAGE(2,"混合报")
  ;
  private Integer type;
  private String name;

  PostTelegramTrainTypeEnum(Integer type, String name) {
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
