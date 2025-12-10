package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UserRoleEntity
 *
 * @author  < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date    2022-01-03 18:12:38
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_user_role") //对应的数据库表
@Cacheable(value = false)
public class UserRoleEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String userId;
  private String roleId;
}
