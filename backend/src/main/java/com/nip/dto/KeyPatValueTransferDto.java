package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class KeyPatValueTransferDto {

  private String trainId;
  private String userId;
  private Integer pageNumber;
  private String key;
  private String value;
  private String time;
  private Integer sort;

  public KeyPatValueTransferDto() {
  }

  public KeyPatValueTransferDto(String userId, String trainId, Integer pageNumber, String key, String value, String time, Integer sort) {
    this.userId = userId;
    this.trainId = trainId;
    this.pageNumber = pageNumber;
    this.key = key;
    this.value = value;
    this.time = time;
    this.sort = sort;
  }
}
