package com.nip.dto.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class HandKeyRecentTrainVO {
  private String startTime;
  private Integer trainTime;
  private BigDecimal score;
  private String speed;
}
