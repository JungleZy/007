package com.nip.dto.sql;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * UserInfoAllByStatusDescDto
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-19 11:33
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class FindUserMonthAvgScoreDto {
  private String m;
  private BigDecimal avg;
}
