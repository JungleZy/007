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
// user_id 是业务唯一键：GET /comprehensive/getUserInfo 在读路径缓存易错题，没有这条唯一约束时
// 两个并发首调会各插一行，之后 findByUserId 的 firstResult() 只会命中其中一行，另一行成孤儿。
// 配套迁移：backend/database/migrations/2026-09-08-01-unique-lazy-create.sql
@Table(name = "t_theory_knowledge_test_fallible", uniqueConstraints =
    @UniqueConstraint(name = "uk_theory_test_fallible_user", columnNames = {"user_id"}))
@Cacheable(value = false)
public class TheoryKnowledgeTestFallibleEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String userId;
  private Integer number;
  private String content;
}
