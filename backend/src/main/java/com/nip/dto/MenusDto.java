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
public class MenusDto implements Comparable<MenusDto> {
  private String id;
  private String parentId;
  private String key;
  private String path;
  private String name;
  private MenusMetaDto meta;
  private String component;
  private Integer sort = 0;
  private List<MenusButtonEntity> permissions;
  private List<MenusDto> children;

  @Override
  public int compareTo(MenusDto o) {
    return this.sort - o.sort;
  }
}
