package com.nip.ws.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * UserModel
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-05-25 16:51
 */
@Data
@RegisterForReflection
public class UserModel {
  private String id;
  private String name;
  private String userImg;
  private Integer type = 0; // 默认身份类型0；1，发报，2，收报
  private Integer status = 0; // 默认状态0；1，已准备；2，已拒绝；3.进行中；4，已完成
}
