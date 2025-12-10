package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class SimulationRouterRoomContentMessageDto {
  private String id;
  private String name;
  private Integer isCable;
  private String createUserId;
  private Integer stats;
  private LocalDateTime createTime;
  private Integer bdType;
  private Integer bwType;
  private Integer bwCount;
  private String content;
  private String mainSignal;
  private String interferenceSignal;
  private Integer roomId;
  private String userName;
  private String userImg;
  private Integer totalTime;
  private String setting;
}
