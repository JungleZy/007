package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 17:36
 * @Description:
 */
@Data
@Schema(name = "岗位报话训练Dto")
@RegisterForReflection
public class PostRadiotelephoneDto {

  @Schema(name = "0 single/默写用语 1 frase/默写含义")
  private Integer type;

  @Schema(name = "数量")
  private Integer number;

  @Schema(name = "速率")
  private BigDecimal speed;

  @Schema(name = "干扰类型")
  private List<String> disturb;

  @Schema(name = "训练名称")
  private String name;

  @Schema(name = "训练类型 0 听报训练 1 默写训练")
  private Integer trainType;

}
