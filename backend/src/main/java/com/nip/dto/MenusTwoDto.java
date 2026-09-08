package com.nip.dto;

import com.nip.entity.MenusButtonEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.List;

/**
 * MenusDto
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-08-02 15:39
 */
@Data
@RegisterForReflection
public class MenusTwoDto {
  private String id;
  private String parentId;
  private String key;
  private String path;
  private String name;
  private String icon;
  private String iconF;
  private String title;
  private String height;
  private Integer keepAlive;
  private String component;
  private Integer sort;
  private Integer isMenu;
  private Integer isBread;
  private String per;
  private List<MenusButtonEntity> permissions;

}
