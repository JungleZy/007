package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RoleEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-05-08 15:04
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_role_menus") //对应的数据库表
@Cacheable(value = false)
public class RoleMenusEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String roleId;
  private String menuId;
  private String per;
}
