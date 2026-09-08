package com.nip.dto;

import com.nip.dto.vo.TheoryKnowledgeSwfVO;
import com.nip.entity.TheoryKnowledgeEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * TheoryKnowledgeDto
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-22 18:45
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TheoryKnowledgesDto {
  private TheoryKnowledgeEntity knowledge;
  private List<TheoryKnowledgeSwfVO> knowledgeSwfs;
}
