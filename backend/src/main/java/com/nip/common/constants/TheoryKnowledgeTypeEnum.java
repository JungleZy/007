package com.nip.common.constants;

/**
 * TheoryKnowledgeType枚举类
 * @Author: wushilin
 * @Data: 2022-04-01 15:32
 * @Description:
 */
public enum TheoryKnowledgeTypeEnum {
  //s
  BASE(0,"基础"),
  //
  QEUIP(1,"职掌装备"),
  //
  WORK (2,"执勤业务")
  ;

  TheoryKnowledgeTypeEnum(Integer type, String name) {
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

  private Integer type;
  private String  name;
}
