package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class KeyPatPageTransferDto {
  private String trainId;
  private String userId;
  private Integer pageNumber;
  private String key;
  private String value;
  private String time;
  private Integer sort;
}
