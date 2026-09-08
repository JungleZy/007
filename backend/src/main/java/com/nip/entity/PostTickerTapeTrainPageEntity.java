package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2023-03-15 14:03
 * @Description:
 */
@Data
@Entity(name = "t_post_ticker_tape_train_page")
@Cacheable(value = false)
public class PostTickerTapeTrainPageEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 训练id
   */
  private String trainId;


  /**
   * 页码
   */
  private Integer pageNumber;

  /**
   * 生成得key
   */
  @Column(name = "`key`")
  private String key;

  /**
   * 用户拍发得内容
   */
  private String value;


  /**
   * 排序字段
   */
  private Integer sort;

}
