package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/2/22 15:38
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_knowledge_exam_test_paper") //对应的数据库表
@Cacheable(value = false)
public class TheoryKnowledgeExamTestPaperEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 名称
   */
  private String name;
  /**
   * 原试卷id
   */
  private String testPaperId;
  /**
   * 总分
   */
  private Integer total;
  /**
   * 及格分比
   */
  private String passTheExamThan;
  /**
   * 考核id
   */
  private String examId;
  /**
   * 及格分
   */
  private Integer passMark;
  private String singleChoiceList;
  private String multipleChoiceList;
  private String judgeList;
  private String completionList;
  private String shortAnswer;
}
