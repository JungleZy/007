package com.nip.dto;

import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2022-07-02 09:28
 * @Description:
 */
@Data
public class UserDto {
  private String id;
  private String userImg;
  private String userAccount;
  private String phone;
  private String userName;
  private String idCard;
  private Integer userSex;
  private String password;
  private String eday;
}
