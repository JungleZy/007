package com.nip.ws.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * 请求模型
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2018-12-14 12:25
 */
@Data
@RegisterForReflection
public class RequestModel {
  private int code;
  private String sendUser;
  private String receiveUser;
  private String data;


}
