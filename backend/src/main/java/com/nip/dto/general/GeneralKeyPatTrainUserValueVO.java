package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class GeneralKeyPatTrainUserValueVO {

  private Integer pageNumber;

  private Integer sort;

  private String time;

  private String key;

  private String value;

  private Integer trainId;

  private String userId;
}
