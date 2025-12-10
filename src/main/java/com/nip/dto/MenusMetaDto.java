package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * MenusMetaDto
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-08-02 15:39
 */
@Data
@RegisterForReflection
public class MenusMetaDto {
  private String icon;
  private String iconF;
  private String height;
  private String title;
  private Boolean isMenu = false;
  private Boolean isBread = false;
}
