package com.nip.dto.sql;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: wushilin
 * @Data: 2023-07-27 16:02
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class TheoryKnowledgeSwfRecordContStudyTimeDto {
  private String date;
  private Object time;
}
