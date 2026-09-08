package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 17:04
 * @Description:
 */
@Data
@Entity(name = "t_post_telegram_train_content_value")
@Cacheable(value = false)
public class PostTelegramTrainContentValueEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 训练id
   */
  private String trainId;

  /**
   * 报底页数
   */
  private Integer floorNumber;

  /**
   * 客户按下松开的时间
   */
  private String messageBody;
}
