package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-06-24 15:45
 * @Description:
 */
@Schema(name = "军语密语添加训练参数")
@Data
@RegisterForReflection
public class PostMilitaryTermTrainAddDto {

  @Schema(name = "类型")
  private List<String> types;

  @Schema(name = "数量")
  private Integer totalNumber;

  @Schema(name = "训练名称")
  private String name;
}
