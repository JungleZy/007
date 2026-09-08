package com.nip.ws.model;

/**
 * @Author: wushilin
 * @Data: 2023-03-02 09:39
 * @Description:
 */
public enum SocketResponseModelEnum {
  //成功
  SUCCESS(1,"成功"),
  //错误
  ERR(-1,"失败");
  private Integer code;
  private String  name;

  SocketResponseModelEnum(Integer code, String name) {
    this.code = code;
    this.name = name;
  }

  public Integer getCode() {
    return code;
  }

  public void setCode(Integer code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
