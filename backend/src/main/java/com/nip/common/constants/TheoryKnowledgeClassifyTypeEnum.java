package com.nip.common.constants;

/**
 * @Author: wushilin
 * @Data: 2022-06-06 11:43
 * @Description:
 */
public enum TheoryKnowledgeClassifyTypeEnum {
  //
  specialty(0,"专业"),
  difficulty(1,"难易")
  ;
  private Integer type;

  private String name;

  TheoryKnowledgeClassifyTypeEnum(Integer type, String name) {
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
