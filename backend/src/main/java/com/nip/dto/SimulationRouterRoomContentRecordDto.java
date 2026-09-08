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
public class SimulationRouterRoomContentRecordDto {
  private Integer id;
  private String name;
  private Integer isCable;
  private String createUserId;
  private Integer stats;
  private LocalDateTime createTime;
  private Integer roomId;
  private Integer bdType;
  private Integer bwType;
  private Integer bwCount;
  private Integer pageCount;
  private String userName;
  private String userImg;
}
