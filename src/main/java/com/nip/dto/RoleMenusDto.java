package com.nip.dto;

import com.nip.entity.RoleEntity;
import lombok.Data;

import java.util.List;

/**
 * RoleMenusDto
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-08-05 14:18
 */
@Data
public class RoleMenusDto {
  private RoleEntity role;
  private List<MenusDto> menusAll;
  private List<MenusTwoDto> menusChecked;
}
