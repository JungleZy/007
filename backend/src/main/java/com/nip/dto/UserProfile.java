package com.nip.dto;

import com.nip.entity.UserEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class UserProfile {
  private String id;
  private String wkno;
  private String phone;
  private String email;
  private String userAccount;
  private String userImg;
  private String userName;
  private String idCard;
  private Integer userSex;
  private String updateFlag;
  private Integer status;
  private String bday;
  private String eday;
  private String dday;

  public static UserProfile from(UserEntity user) {
    return new UserProfile(user.getId(), user.getWkno(), user.getPhone(), user.getEmail(), user.getUserAccount(),
        user.getUserImg(), user.getUserName(), user.getIdCard(), user.getUserSex(), user.getUpdateFlag(),
        user.getStatus(), user.getBday(), user.getEday(), user.getDday());
  }
}
