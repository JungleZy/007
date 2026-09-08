package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class TelexPatValueTransferDto {

  private String trainId;
  private String userId;
  private Integer pageNumber;
  private String key;
  private String value;
  private Integer sort;

  public TelexPatValueTransferDto() {
  }

  public TelexPatValueTransferDto(String userId, String trainId, Integer pageNumber, String key, String value, Integer sort) {
    this.userId = userId;
    this.trainId = trainId;
    this.pageNumber = pageNumber;
    this.key = key;
    this.value = value;
    this.sort = sort;
  }
}
