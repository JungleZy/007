package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 17:04
 * @Description:
 */
@Data
@Entity(name = "t_post_telegram_train_floor_content_value")
@Cacheable(value = false)
public class PostTelegramTrainContentFloorValueEntity {
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

  /**
   * 基准值
   */
  private String standard;

  /**
   * 完成信息
   */
  private String finishInfo;

  /**
   * 解析后的报文格式内容
   */
  private String resolver;

}
