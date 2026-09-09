package com.nip.common.constants;

/**
 * 返回码
 *
 * @author zeng
 */
public enum ResponseCode {

  SUCCESS(200, "ok", ""),
  PARAMS_ERROR(202, "请求参数错误", ""),
  SYSTEM_ERROR(500, "服务器错误", ""),
  // 已删除的历史重复项：CODE_202（202「本次授权已过期」，全仓零引用）、
  // CODE_500（与 SYSTEM_ERROR 同码同文案，3 个 ExceptionMapper 已改用 SYSTEM_ERROR）、
  // CODE_200（与 SUCCESS 同码同文案，TheoryKnowledgeService 已收口到 SUCCESS）。
  // 鉴权系列 203/204/206 是 JWTInterceptor 与 UnauthorizedExceptionMapper 的客户端契约，
  // 码值与文案均不得改动。
  CODE_203(203, "token不能为空", ""),
  CODE_204(204, "设备标识不能为空", ""),
  CODE_206(206, "账号登录凭证异常", ""),
  CODE_207(207, "无权限", "");
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
