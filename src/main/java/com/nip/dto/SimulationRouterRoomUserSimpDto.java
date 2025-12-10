package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class SimulationRouterRoomUserSimpDto {
  private String id;
  private String name;
  private Integer userType;
  private String userImg;
  private Integer channel;
}
