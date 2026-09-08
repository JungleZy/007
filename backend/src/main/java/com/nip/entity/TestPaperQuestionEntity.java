package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/24 15:00
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_knowledge_test_paper_question") //对应的数据库表
@Cacheable(value = false)
public class TestPaperQuestionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 试卷id
   */
  private String testPaperId;
  /**
   * 试题库id
   */
  private String parentId;
  /**
   * 分值
   */
  private Integer score;
  /**
   * 排序
   */
  private Integer sort;
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
}
