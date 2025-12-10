package com.nip.dto.sql;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @Author: wushilin
 * @Data: 2023-07-28 09:23
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class TickerTapeTrainDaoCountBaseTrain {
  private Integer totalTime;
  private Integer totalCount;
  private BigDecimal avgSpeed;
}
