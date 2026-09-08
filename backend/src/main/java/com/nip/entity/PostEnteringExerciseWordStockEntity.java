package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 13:56
 * @Description:
 */
@Data
@Entity(name = "t_post_entering_exercise_word_stock")
@Cacheable(value = false)
public class PostEnteringExerciseWordStockEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 类型 0 拼音文章 1 拼音军语 2 五笔文章 3 五笔军语 4 英语文章
   */
  private Integer type;

  /**
   * 名称
   */
  private String name;


  /**
   * 内容
   */
  private String content;

  /**
   * 创建人ID
   */

  private String createUserId;


  private Integer wordSize;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();

}
