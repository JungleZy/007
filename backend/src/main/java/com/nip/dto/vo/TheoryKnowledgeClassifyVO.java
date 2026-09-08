package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-07-08 09:08
 * @Description:
 */
@Data
@Schema(name = "知识电专业/难易分类VO")
public class TheoryKnowledgeClassifyVO {

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


  @Schema(name = "createTime",title = "创建时间")
  private LocalDateTime createTime;

}
