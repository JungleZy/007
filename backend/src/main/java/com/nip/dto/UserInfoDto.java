package com.nip.dto;

import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import lombok.Data;

import java.util.List;

/**
 * LoginDto
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-08-06 9:23
 */
@Data
public class UserInfoDto {
  private UserEntity user;
  private RoleEntity role;
  private List<MenusDto> menus;
}
