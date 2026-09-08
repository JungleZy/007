package com.nip.dto.vo;


import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-02-27 16:30
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "")
@Accessors(chain = true)
@RegisterForReflection
public class TheoryKnowledgeQuestionErrorTopVO {
  private String id;
  private Integer number;
  private Integer type;
  private String topic;
}
