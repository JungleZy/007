package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 13:56
 * @Description: 知识专业和难易分类表
 */
@Data
@Entity(name = "t_theory_knowledge_classify")
@Cacheable(value = false)
public class TheoryKnowledgeClassifyEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   *0 专业分类 1 难易分类
   */
  private Integer type;

  /**
   * 名称
   */
  private String name;

  private LocalDateTime createTime = LocalDateTime.now();

  /**
   * 创建人id
   */
  private String createId;
}
