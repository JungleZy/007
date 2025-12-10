package com.nip.common.constants;

public enum SimulationDisturdTopicEnum {

  //推送主题
  TOPIC_ONE("1", "1路推送"),
  TOPIC_TWO("2", "2路推送"),
  TOPIC_THREE("3", "3路推送"),
  TOPIC_ZERO("0", "全局推送"),
  TOPIC_BEGIN("begin", "开始训练"),
  TOPIC_END("end", "结束训练"),
  TOPIC_SELECT("select", "切换频道"),
  TOPIC_RESULT("result", "提交结果"),
  TOPIC_TRAIN_START("1", "教员开始训练"),
  TOPIC_TRAIN_PAUSE("2", "教员暂停训练"),
  TOPIC_TRAIN_GOON("3", "教员继续训练"),
  TOPIC_TRAIN_FINISH("4", "教员结束训练"),
  TOPIC_TRAIN_ONLINE("online", "学员准备消息");

  private String type;
  private String name;

  SimulationDisturdTopicEnum(String type, String name) {
    this.type = type;
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
