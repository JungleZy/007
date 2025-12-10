package com.nip.dto;

import com.nip.entity.RoleEntity;
import lombok.Data;

import java.util.List;

/**
 * SaveRoleEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-05-08 15:04
 */
@Data
public class SaveRoleDto {
  private RoleEntity role;
  private List<String> menus;
}
