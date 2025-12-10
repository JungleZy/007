package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class GeneralTelexPatTrainUserValueVO {
  private String trainId;
  private String userId;
  private Integer pageNumber;
  private Integer sort;
  private String key;
  private String value;
}
