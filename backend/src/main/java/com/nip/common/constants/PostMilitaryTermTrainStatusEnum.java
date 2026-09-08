package com.nip.common.constants;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 10:06
 * @Description:
 */
public enum PostMilitaryTermTrainStatusEnum {
  //xx
  NOT_STARTED(0,"未开始"),
  //
  UNDERWAY(1,"进行中"),
  //
  FINISH(2,"完成"),

  ;
  private Integer status;
  private String name;

  PostMilitaryTermTrainStatusEnum(Integer status, String name) {
    this.status = status;
    this.name = name;
  }

  public Integer getStatus() {
    return status;
  }

  public void setStatus(Integer status) {
    this.status = status;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
