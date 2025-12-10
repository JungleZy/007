package com.nip.common.constants;

public enum SimulationRouterRoomStatsConstants {
  START("开始"),
  FINISH("结束");


  private String content;

  SimulationRouterRoomStatsConstants(String content) {
    this.content = content;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }
}
