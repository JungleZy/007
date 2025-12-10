package com.nip.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/20 18:35
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TheoryKnowledgeQuestionLevelDto {
  private String id;
  /**
   * 父id
   */
  private String parentId;
  /**
   * 名字
   */
  private String name;
}
