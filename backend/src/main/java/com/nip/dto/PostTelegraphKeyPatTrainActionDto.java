package com.nip.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nip.common.utils.StrictIntegerDeserializer;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@RegisterForReflection
public class PostTelegraphKeyPatTrainActionDto {
  private String id;
  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  private Integer protocolVersion;
  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  private Integer attempt;
}
