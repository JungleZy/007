package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class SimulationRouterRoomUserDto {
  private String id;
  private String userName;
  private String userImg;
  private Integer channel;
  private String contentValue;
  private Integer userStatus;
}
