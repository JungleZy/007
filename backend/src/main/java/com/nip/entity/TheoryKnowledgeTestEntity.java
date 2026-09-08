package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * TheoryKnowledgeTestEntity
 *随堂测试表
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-01-03 14:31:14
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_knowledge_test") //对应的数据库表
@Cacheable(value = false)
public class TheoryKnowledgeTestEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String title;
  private String knowledgeId;
  private String knowledgeSwfId;
  /**
   * 是否是启用版本，0否，1是
   */
  private Integer versions;
  /**
   * 创建时间，存在多套题，通过创建时间区分，最晚的为最新的
   */
  private String createTime = new Date().getTime() + "";
  /**
   * 创建人ID
   */
  private String createUserId;
}
