package com.nip.ws.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.Map;

/**
 * 响应模型
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2018-12-14 12:24
 */
@Data
@RegisterForReflection
public class ResponseModel {
  private int code;
  private String sendUser;
  private String receiveUser;
  private String data;
  private Map map;

  public ResponseModel(int code) {
    this.code = code;
  }
  public ResponseModel(int code, String data) {
    this.code = code;
    this.data = data;
  }
  public ResponseModel(int code, Map map) {
    this.code = code;
    this.map = map;
  }
  public ResponseModel(int code,String sendUser,String receiveUser, String data) {
    this.code = code;
    this.sendUser = sendUser;
    this.receiveUser = receiveUser;
    this.data = data;
  }
}
