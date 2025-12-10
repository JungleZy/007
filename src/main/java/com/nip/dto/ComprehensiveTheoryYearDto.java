package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author: wushilin
 * @Data: 2022-04-01 11:51
 * @Description:
 */
@Data
@RegisterForReflection
public class ComprehensiveTheoryYearDto {

  private String kId;

  private BigDecimal credit;

  private Integer type;

  private String ksId;

  private String userId;
}
