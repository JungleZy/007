package com.nip.common.constants;

/**
 * @Author: wushilin
 * @Data: 2022-05-05 11:55
 * @Description: 岗位训练-手键拍发状态枚举
 */
public enum PostRadiotelephoneTrainStatusEnum {
  //xx
  NOT_STARTED(0,"未开始"),
  //
  UNDERWAY(1,"进行中"),

  FINISH(2,"完成")
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

  PostRadiotelephoneTrainStatusEnum(Integer status, String name) {
    this.status = status;
    this.name = name;
  }
}
