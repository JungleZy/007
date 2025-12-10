package com.nip.common.exception;

import com.nip.common.constants.ServiceExceptionEnum;

public class NIPException extends RuntimeException {

  private Integer code;

  private String message;

  public NIPException(ServiceExceptionEnum serviceExceptionEnum) {
    this.code = serviceExceptionEnum.getCode();
    this.message = serviceExceptionEnum.getMessage();
  }

  public NIPException(String message) {
    this.code = 500;
    this.message = message;
  }

  public Integer getCode() {
    return code;
  }

  public void setCode(Integer code) {
    this.code = code;
  }

  @Override
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
