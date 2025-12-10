package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/24 15:28
 */
@Data
@RegisterForReflection
public class TheoryKnowledgeQuestionAllDto {
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
   * 创建人名称
   */
  private String createUserName;
  /**
   * 创建时间
   */
  private String createTime;
  /**
   * 节点ip
   */
  private String levelId;
}
