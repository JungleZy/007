package com.nip.dto.vo;


import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-07-08 09:20
 * @Description:
 */
@Data
@Schema(name = "TheoryKnowledgeClassifyPageVO",title = "知识难易/专业分类分页VO")
public class TheoryKnowledgeClassifyPageVO {

  @Schema(name = "difficultyList",title = "难易")
  private List<TheoryKnowledgeClassifyVO> difficultyList;

  @Schema(name = "specialtyList",title = "专业")
  private List<TheoryKnowledgeClassifyVO> specialtyList;

}
