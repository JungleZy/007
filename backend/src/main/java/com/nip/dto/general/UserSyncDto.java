package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * 综合组训离线导出/导入用的用户行。
 *
 * <p>刻意不含 {@code password}/{@code token}/{@code deviceId}：该 DTO 会随
 * {@code /api/generalKeyPat/getTrainInfo(Batch)} 序列化给客户端，一旦带上凭据字段，
 * 拿到任意训练导出的人就拿到了参训者与创建者的活动会话（P0-01 / SEC-01）。
 * 导入侧按字段名整体拷贝，缺字段即建号不落凭据 —— 新账号无口令，须管理员 resetPassword。
 */
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
