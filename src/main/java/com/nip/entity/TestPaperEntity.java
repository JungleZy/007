package com.nip.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/24 14:49
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_knowledge_test_paper") //对应的数据库表
@Cacheable(value = false)
public class TestPaperEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 名称
   */
  private String name;
  /**
   * 节点id
   */
  private String levelId;
  /**
   * 总分
   */
  private Integer total;
  /**
   * 及格分比
   */
  private String passTheExamThan;
  /**
   * 及格分
   */
  private Integer passMark;
  /**
   * 创建人
   */
  private String createUserId;
  /**
   * 创建时间
   */
  private String createTime = String.valueOf(new Date().getTime());
  /**
   * 创建人名称
   */
  private String createUserName;
}
