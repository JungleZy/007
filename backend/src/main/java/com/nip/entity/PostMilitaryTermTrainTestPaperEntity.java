package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2022-06-24 15:17
 * @Description:
 */
@Data
@Entity(name = "t_post_military_term_train_test_paper")
@Cacheable(value = false)
public class PostMilitaryTermTrainTestPaperEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 训练id
   */
  private String trainId;

  /**
   * 题目
   */
  private String title;

  /**
   * 选项
   */
  @Column(name = "`option`")
  private String option;

  /**
   * 正确答案
   */
  private String correctAnswer;

  /**
   * 用户选择的答案
   */
  private String userAnswer;

}
