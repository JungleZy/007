package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * MilitaryTermDataEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-06-23 14:46
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "新增军语DTO")
@RegisterForReflection
public class MilitaryTermDataDto {

  @Schema(name = "0 新增军语类型 1 新增秘语 ")
  private Integer type;

  @Schema(name = "父ID")
  private String parentId;

  @Schema(name = "名称")
  private String key;

  @Schema(name = "值")
  private String value;

  @Schema(name = "位置坐标")
  private Integer sort = 0;

}
