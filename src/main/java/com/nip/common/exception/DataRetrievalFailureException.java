package com.nip.common.exception;

import jakarta.annotation.Nullable;

public class DataRetrievalFailureException extends NonTransientDataAccessException {
  public DataRetrievalFailureException(String msg) {
    super(msg);
  }

  public DataRetrievalFailureException(String msg, @Nullable Throwable cause) {
    super(msg, cause);
  }
}
