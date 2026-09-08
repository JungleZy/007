package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * TheoryKnowledgeTestContentEntity
 * 随堂测试下属题
 * @author  < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date    2022-01-03 14:31:14
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_knowledge_test_content") //对应的数据库表
@Cacheable(value = false)
public class TheoryKnowledgeTestContentEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String knowledgeId;
  private String knowledgeSwfId;
  private String knowledgeTestId;
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
   * 排序
   */
  private Integer sort;
  /**
   * 创建人id
   */
  private String createUserId;
  /**
   * 创建时间
   */
  private String createTime = new Date().getTime() + "";
}
