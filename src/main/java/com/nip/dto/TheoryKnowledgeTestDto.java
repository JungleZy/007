package com.nip.dto;

import com.nip.entity.TheoryKnowledgeTestContentEntity;
import com.nip.entity.TheoryKnowledgeTestEntity;
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
public class TheoryKnowledgeTestDto {
  private TheoryKnowledgeTestEntity knowledgeTest;
  private List<TheoryKnowledgeTestContentEntity> knowledgeTestContents;
}
