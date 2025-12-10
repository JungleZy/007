package com.nip.dto.sql;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UserInfoAllByStatusDescDto
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-19 11:33
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class FindUserByStatusDescDto {
  private String id;
  private String idCard;
  private String phone;
  private Integer status;
  private String userAccount;
  private String userImg;
  private String userName;
  private Integer userSex;
  private String title;
}
