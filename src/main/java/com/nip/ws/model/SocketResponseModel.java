package com.nip.ws.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: wushilin
 * @Data: 2023-03-02 09:35
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class SocketResponseModel<T> {
  /**
   * -1 错误消息 1 正常消息
   */
  private Integer code;
  private String sendName;
  private String receiveName;
  private T data;

  /**
   * 成功消息体
   * @param data
   * @param sendName
   * @param receiveName
   * @param <T>
   * @return
   */
  public static<T> SocketResponseModel success(T data, String sendName, String receiveName){
    return new SocketResponseModel<T>(SocketResponseModelEnum.SUCCESS.getCode(),sendName,receiveName,data);
  }
  public static <T> SocketResponseModel success(T data) {
    return new SocketResponseModel<T>(SocketResponseModelEnum.SUCCESS.getCode(),"","", data);
  }
  public static<T> SocketResponseModel err(T data, String sendName, String receiveName){
    return new SocketResponseModel<T>(SocketResponseModelEnum.ERR.getCode(),sendName,receiveName,data);
  }

}
