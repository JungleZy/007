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
@Entity(name = "t_post_telegram_train_floor_content") //对应的数据库表
@Cacheable(value = false)
public class PostTelegramTrainFloorContentEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 保底编号
   */
  private Integer floorNumber;
  /**
   * 排序字段
   */
  private Integer sort;
  /**
   * 摩尔斯电码key
   */
  private String moresKey;
  /**
   * 值
   */
  private String moresValue = "[]";
  /**
   * 输入时间
   */
  private String moresTime = "[]";

  /**
   * 训练ID
   */
  private String trainId;

  /**
   * 拍发的电码
   */
  private String patKeys;
}
