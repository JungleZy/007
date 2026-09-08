package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class SimulationRouterRoomContentDto {
  private String name;
  private Integer isCable;
  private String createUserId;
  private Integer stats;
  private Integer playStatus;
  private Integer totalTime;
  private Integer id;
  private Integer roomId;
  private String content;
  private String mainSignal;
  private String interferenceSignal;
  private Integer bdType;
  private Integer bwType;
  private Integer bwCount;
  private Integer isRandom;
}
