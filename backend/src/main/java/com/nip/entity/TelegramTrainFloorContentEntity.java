package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TelegramTrainFloorContentEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-20 11:02
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_telegram_train_floor_content") //对应的数据库表
@Cacheable(value = false)
public class TelegramTrainFloorContentEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String floorId;
  private Integer sort;
  private String moresKey;
  private String moresValue = "[]";
  private String moresTime = "[]";
}
