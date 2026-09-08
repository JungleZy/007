package com.nip.ws.model;

import com.nip.common.constants.SimulationResponseModelEnum;
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
public class SimulationResponseModel<T> {
  /**
   * -1 错误消息 1 正常消息
   */
  private Integer code;
  private String sendName;
  private String receiveName;
  private T data;

  /**
   * 成功消息体
   *
   * @param data
   * @param sendName
   * @param receiveName
   * @param <T>
   * @return
   */
  public static <T> SimulationResponseModel success(T data, String sendName, String receiveName) {
    return new SimulationResponseModel<T>(SimulationResponseModelEnum.SUCCESS.getCode(), sendName, receiveName, data);
  }

  public static <T> SimulationResponseModel err(T data, String sendName, String receiveName) {
    return new SimulationResponseModel<T>(SimulationResponseModelEnum.ERR.getCode(), sendName, receiveName, data);
  }

}
