package com.nip.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@Entity(name = "t_role")
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Cacheable(value = false)
public class RoleEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String title;
  private String remark;
  private Integer isAdmin;
  private Integer isDefault;
}
