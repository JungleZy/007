package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(title = "电子键组训-添加")
@RegisterForReflection
public class GeneralKeyPatAddParamDto {

  @Schema(title = "训练名称")
  private String title;

  @Schema(title = "是否是固定报 0 随机报 1 固定报")
  private Integer isCable = 0;

  @Schema(title = "如果是固定报，该值为固定报编号，否则为空")
  private String cableId;

  @Schema(title = "如果是固定报，可以选择起始页，默认第一页")
  private Integer startPage;

  @Schema(title = "报底数")
  private Integer totalNumber;

  @Schema(title = "评分规则Id")
  private String ruleId;

  @Schema(title = "参训人员id")
  private List<String> userId;

  @Schema(title = "报底类型 0数码报 1字码报 2混合报")
  private Integer messageType;

  @Schema(title = "是否平均")
  private Integer isAverage;

  @Schema(title = "是否随机")
  private Integer isRandom;

  @Schema(title = "0个人训练 1考核训练")
  private Integer trainType;

}
