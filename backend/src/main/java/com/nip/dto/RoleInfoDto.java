package com.nip.dto;

import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * RoleInfoDto
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-08-05 14:18
 */
@Data
public class RoleInfoDto {
  private RoleEntity role;
  private List<Map<String, Object>> menus;
  private List<UserEntity> users;
}
