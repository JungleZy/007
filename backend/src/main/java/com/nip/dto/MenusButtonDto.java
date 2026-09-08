package com.nip.dto;

import com.nip.entity.MenusButtonEntity;
import com.nip.entity.MenusEntity;
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
public class MenusButtonDto {
  private MenusEntity menus;
  private List<MenusButtonEntity> permissions;
}
