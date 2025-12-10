package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class UserSyncDto {

  private String id;
  /**
   * 工号 - 唯一
   */
  private String wkno;
  /**
   * 手机号码
   */
  private String phone;
  /**
   * 电子邮箱
   */
  private String email;
  /**
   * 用户名
   */
  private String userAccount;
  /**
   * 用户头像
   */
  private String userImg;
  /**
   * 用户姓名
   */
  private String userName;
  /**
   * 身份证
   */
  private String idCard;
  /**
   * 用户性别
   */
  private Integer userSex;
  /**
   * 用户密码
   */
  private String password;
  /**
   * token
   */
  private String token;
  /**
   * 设备编号
   */
  private String deviceId;
  /**
   * 登录时间
   */
  private String updateFlag;
  /**
   * 账号状态：0、正常，1、审核中，-1、停用中
   */
  private Integer status;
  /**
   * 生日
   */
  private String bday;
  /**
   * 入职时间
   */
  private String eday;
  /**
   * 离职时间
   */
  private String dday;

}
