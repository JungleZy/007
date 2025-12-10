package com.nip.common.constants;

/**
 * 返回码
 *
 * @author zeng
 */
public enum ResponseCode {

  SUCCESS(200, "ok", ""),
  PARAMS_ERROR(202, "请求参数错误", ""),
  NULL_ERROR(204, "请求参数为空", ""),
  SYSTEM_ERROR(500, "服务器错误", ""),
  CODE_200(200,"ok" , "" ),
  CODE_202(202,"本次授权已过期" , "" ),
  CODE_203(203,"token不能为空" , "" ),
  CODE_204(204,"设备标识不能为空" , "" ),
  CODE_206(206,"账号登录凭证异常" , "" ),
  CODE_500(500,"服务器错误" , "" );
  //返回码
  private final int code;
  //操作响应信息
  private final String message;
  //响应信息的详细描述
  private final String description;

  //构造函数
  ResponseCode(int code, String message, String description) {
    this.code = code;
    this.message = message;
    this.description = description;
  }

  //get方法
  public int getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public String getDescription() {
    return description;
  }
}
