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
public class FindMenusByRoleIdDto {
  private String id;
  private String parentId;
  private String key;
  private String path;
  private String name;
  private String icon;
  private String iconF;
  private String height;
  private String title;
  private Integer isMenu;
  private Integer isBread;
  private String component;
  private Integer sort;
  private String per;
}
