package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * TheoryKnowledgeSwfEntity
 *理论学习章节
 * @author  < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date    2022-01-03 18:12:14
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_knowledge_swf") //对应的数据库表
@Cacheable(value = false)
public class TheoryKnowledgeSwfEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String knowledgeId;
  private String cover = "/006/cover/base.jpg";
  /**
   * 标题
   */
  private String title;
  /**
   * 内容
   */
  private String content;
  /**
   * 创建人id
   */
  private String createUserId;
  private String createTime = new Date().getTime() + "";
  /**
   * 章节排序
   */
  private Integer sort;
  private Integer score = null;
  private Boolean haveTest;
  private double record = 0;
}
