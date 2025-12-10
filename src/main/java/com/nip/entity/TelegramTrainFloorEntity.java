package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TelegramTrainFloorEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-20 11:02
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_telegram_train_floor") //对应的数据库表
@Cacheable(value = false)
public class TelegramTrainFloorEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String trainId;
  private Integer sort;
  private Integer type = 0;
  private Integer numberType = 0;
  private Integer contentNumber = 0;
}
