package com.nip.common.constants;

public enum SimulationRoomTypeEnum {
  //线路通报
  ROUTER(0, "线路通报"),
  DISTURB(1, "快速/线路干扰"),
  REPORT(2, "通报教学"),
  RECEPT(3, "收报组训");
  private Integer type;
  private String name;

  SimulationRoomTypeEnum(Integer type, String name) {
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
