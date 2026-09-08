package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.math.BigDecimal;

@Data
@RegisterForReflection
public class TheoryKnowledgeQuestionCheckDto {
  private String id;
  /**
   * 测验类型，1、单选题，2、多选题，3、判断题，4、填空题，5、简答题
   */
  private Integer type;
  /**
   * 题目
   */
  private String topic;
  /**
   * 选项
   */
  private String options;
  /**
   * 答案
   */
  private String answer;
  /**
   * 解析
   */
  private String analysis;
  /**
   * 创建人id
   */
  private String createUserId;

  /**
   * 节点ip
   */
  private String levelId;

  private BigDecimal teacherScore;
}
