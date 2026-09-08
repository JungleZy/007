package com.nip.common.exception;

import jakarta.annotation.Nullable;

public abstract class DataAccessException extends NestedRuntimeException {
  public DataAccessException(String msg) {
    super(msg);
  }

  public DataAccessException(@Nullable String msg, @Nullable Throwable cause) {
    super(msg, cause);
  }
}
