package com.nip.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/9 15:14
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TheoryKnowledgeTestUserDto {
  /**
   * 理论学习id
   */
  private String knowledgeId;
  /**
   * 随堂测试id
   */
  private String knowledgeSwfId;
  /**
   * 题目与答案，这个数据通过t_theory_knowladge_test_content形成json格式保存在这里
   */
  private String content;
  /**
   * 得分
   */
  private Integer score;
}
