package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/19 14:20
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_question") //对应的数据库表
@Cacheable(value = false)
public class TheoryKnowledgeQuestionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
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
   * 创建时间
   */
  private String createTime = new Date().getTime() + "";
  /**
   * 节点ip
   */
  private String levelId;
}
