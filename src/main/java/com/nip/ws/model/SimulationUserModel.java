package com.nip.ws.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2023-03-02 09:14
 * @Description:
 */
@Data
@RegisterForReflection
public class SimulationUserModel {
  private String id;
  private String name;
  private String userImg;
  /**
   * 发送管道号
   */
  private Integer channel;

  private Integer userType;

  /**
   * 状态 0 离线 1在线 2 准备
   */
  private Integer status;
}
