package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/20 10:26
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_question_level") //对应的数据库表
@Cacheable(value = false)
public class TheoryKnowledgeQuestionLevelEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 父id
   */
  private String parentId;
  /**
   * 名字
   */
  private String name;
  /**
   * 创建人id
   */
  private String createUserId;
  /**
   * 创建时间
   */
  private String createTime = new Date().getTime() + "";
}
