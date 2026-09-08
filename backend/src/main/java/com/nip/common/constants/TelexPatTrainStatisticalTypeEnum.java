package com.nip.common.constants;

/**
 * @Author: wushilin
 * @Data: 2022-06-01 17:19
 * @Description:
 */
public enum TelexPatTrainStatisticalTypeEnum {
  //
  WORD(0,"单字训练"),
  NUMBER(1,"数字连管"),
  LETTER(2,"字母连贯"),
  GROUP(3,"组合连贯")
  ;
  private Integer type;
  private String name;

  TelexPatTrainStatisticalTypeEnum(Integer type, String name) {
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
