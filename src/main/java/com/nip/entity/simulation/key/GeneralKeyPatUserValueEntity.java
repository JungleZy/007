package com.nip.entity.simulation.key;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 用户的拍发数据
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "general_key_pat_user_value") //对应的数据库表
@Cacheable(value = false)
public class GeneralKeyPatUserValueEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;
  /**
   * 训练ID
   */
  private Integer trainId;
  /**
   * 用户ID
   */
  private String userId;
  /**
   * 页码
   */
  private Integer pageNumber;
  /**
   * 页内排序
   */
  private Integer sort;
  /**
   * 生成的key
   */
  @Column(name = "`key`")
  private String key;
  /**
   * 用户拍发的值
   */
  private String value;

  /**
   * 拍发的时长
   */
  private String time;
}
