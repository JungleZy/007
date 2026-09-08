package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;


/**
 * @Author: wushilin
 * @Data: 2022-04-12 13:56
 * @Description:
 */
@Data
@Entity(name = "t_entering_exercise_word_stock")
@Cacheable(value = false)
public class EnteringExerciseWordStockEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 0 异音同字 1  同音异字 2 连音词组 3 军语词组 4 文章
   */
  private Integer type;

  /**
   * 内容
   */
  private String content;
}
