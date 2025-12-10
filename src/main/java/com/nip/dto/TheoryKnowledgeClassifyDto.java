package com.nip.dto;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-07-08 09:08
 * @Description:
 */
@Data
@Schema(name = "知识电专业/难易分类Dto")
public class TheoryKnowledgeClassifyDto {

  @Schema(name = "id",title = "id")
  private String id;

  /**
   *0 专业分类 1 难易分类
   */
  @Schema(name = "type",title = "0 专业分类 1 难易分类")
  private Integer type;

  /**
   * 名称
   */
  @Schema(name = "name",title = "名称")
  private String name;


}
