package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TelegramTrainLogEntity
 *
 * @author  < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date    2022-01-13 10:45:28
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_telegram_train_log") //对应的数据库表
@Cacheable(value = false)
public class TelegramTrainLogEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String telegramTrainId;
  private Integer type; // 0.点，1.划，2.点划间隔，3.字元间隔，4.单词间隔
  private String value;
  private String creatTime;
}
