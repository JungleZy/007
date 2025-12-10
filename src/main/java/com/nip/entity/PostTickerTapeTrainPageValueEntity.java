package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * @Author: wushilin
 * @Data: 2023-03-15 14:03
 * @Description:
 */
@Data
@Entity(name = "t_post_ticker_tape_train_page_value")
@AllArgsConstructor
@NoArgsConstructor
@Cacheable(value = false)
public class PostTickerTapeTrainPageValueEntity {
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
   * 填报内容
   */
  private String value;

  /**
   * 图片
   */
  private String image;

}
