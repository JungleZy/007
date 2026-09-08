package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class SimulationRouterRoomSimpDto {
  private Integer id;
  private String name;
  private Integer isCable = 0;
  private String createUserId;
  private Integer stats;
  private LocalDateTime createTime;
  private Integer bdType;
  private Integer bwType;
  private Integer bwCount;
  private Integer pageCount;
  @Schema(title = "页开始标识，0 否 1 是")
  private Integer isStartSign = 1;
}
