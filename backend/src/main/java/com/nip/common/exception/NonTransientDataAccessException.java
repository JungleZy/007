package com.nip.common.exception;

import jakarta.annotation.Nullable;

public abstract class NonTransientDataAccessException extends DataAccessException {
  public NonTransientDataAccessException(String msg) {
    super(msg);
  }

  public NonTransientDataAccessException(@Nullable String msg, @Nullable Throwable cause) {
    super(msg, cause);
  }
}
