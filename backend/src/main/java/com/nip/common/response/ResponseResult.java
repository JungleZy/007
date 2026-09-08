package com.nip.common.response;

import com.nip.common.constants.ResponseCode;

public class ResponseResult<T> {
  /**
   * 成功
   *
   * @param: data
   * @param: <T>
   */
  public static <T> Response<T> success() {
    return new Response<>(ResponseCode.SUCCESS.getCode(), null, "ok");
  }

  public static <T> Response<T> success(T data) {
    return new Response<>(ResponseCode.SUCCESS.getCode(), data, "ok");
  }

  public static <T> Response<T> success(String message, T data) {
    return new Response<>(ResponseCode.SUCCESS.getCode(), data, message);
  }

  /**
   * 失败
   *
   * @param: errorCode
   */
  public static <T> Response<T> error() {
    return new Response<>(ResponseCode.SYSTEM_ERROR.getCode(), null, ResponseCode.SYSTEM_ERROR.getMessage());
  }

  public static <T> Response<T> error(ResponseCode errorCode) {
    return new Response<>(errorCode.getCode(), null, errorCode.getMessage(), errorCode.getDescription());
  }

  public static <T> Response<T> error(String message) {
    return new Response<>(ResponseCode.SYSTEM_ERROR.getCode(), null, message);
  }

  public static <T> Response<T> error(ResponseCode errorCode, String message, String description) {
    return new Response<>(errorCode.getCode(), null, message, description);
  }

  public static <T> Response<T> error(int code, String message, String description) {
    return new Response<>(code, null, message, description);
  }

  public static <T> Response<T> error(int code, String message) {
    return new Response<>(code, null, message, null);
  }

  public static <T> Response<T> error(ResponseCode errorCode, String description) {
    return new Response<>(errorCode.getCode(), null, errorCode.getMessage(), description);
  }
}
