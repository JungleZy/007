package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * TheoryKnowledgeTestEntity
 * 随堂测试表
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-01-03 14:31:14
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_knowledge_test_fallible") //对应的数据库表
@Cacheable(value = false)
public class TheoryKnowledgeTestFallibleEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String userId;
  private Integer number;
  private String content;
}
