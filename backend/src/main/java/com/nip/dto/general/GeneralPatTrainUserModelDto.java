package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.websocket.Session;
import lombok.Data;

@Data
@RegisterForReflection
public class GeneralPatTrainUserModelDto {
  private String id;
  private String userName;
  private String userImg;
  // 0参训人 1组训人
  private Integer role;
  private Session session;

  /**
   * 状态 0 离线 1在线 2 准备
   */
  private Integer status;
}
