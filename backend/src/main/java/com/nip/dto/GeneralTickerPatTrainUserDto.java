package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class GeneralTickerPatTrainUserDto {
  private Integer id;
  private Integer trainId;
  private String userId;
  private Integer role;
  private BigDecimal score;
  private String deductInfo;
  private String statisticInfo;
  private Integer errorNumber;
  private String accuracy;
  private String speed;
  private String speedLog;
  private Integer lack;
  private LocalDateTime finishTime;
  private LocalDateTime createTime;
  private Integer isFinish;
  private String userName;
  private String userImg;
}
