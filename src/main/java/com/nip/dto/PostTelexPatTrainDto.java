package com.nip.dto;


import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 11:19
 * @Description:
 */
@Data
@Schema(title = "添加电传拍发DTO")
@RegisterForReflection
public class PostTelexPatTrainDto {

  @Schema (title = "训练名称")
  private String name;

  @Schema(title = "是否是固定报 0 随机报 1 固定报")
  private Integer isCable = 0;

  @Schema(title = "如果是固定报，该值为固定报编号，否则为空")
  private String cableId;

  @Schema(title = "如果是固定报，可以选择起始页，默认第一页")
  private Integer startPage;

  @Schema (title ="训练类型 0 数字连贯 1 字母连贯 2 组合连贯")
  private Integer type;

  @Schema (title ="组数")
  private Integer groupNumber;

  @Schema (title ="内容")
  private String content;

  @Schema (title ="评分规则Id")
  private String ruleId;

  @Schema (title ="训练类型 0 电传拍发 1 数据报拍发")
  private Integer trainType;

  @Schema(title = "报底类型 0 挨指 1 对手 2 随机")
  private Integer patType;
}
