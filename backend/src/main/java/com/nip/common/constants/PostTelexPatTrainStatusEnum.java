package com.nip.common.constants;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 11:24
 * @Description:
 */
public enum PostTelexPatTrainStatusEnum {
  //xx
  NOT_STARTED(0,"未开始"),
  //
  UNDERWAY(1,"进行中"),

  PAUSE(2,"暂停"),

  FINISH(3,"完成")
  ;
  private Integer status;
  private String name;

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

  PostTelexPatTrainStatusEnum(Integer status, String name) {
    this.status = status;
    this.name = name;
  }
}
