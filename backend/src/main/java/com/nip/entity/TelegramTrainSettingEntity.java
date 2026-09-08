package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TelegramTrainSettingEntity
 *
 * @author  < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date    2022-02-08 10:09:32
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_telegram_train_setting") //对应的数据库表
@Cacheable(value = false)
public class TelegramTrainSettingEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private Integer type;
  @Column(name = "`key`",nullable = false)
  private String key;
  private String value;
}
