package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/23 15:43
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_key_points") //对应的数据库表
@Cacheable(value = false)
public class KeyPointsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 0:手键拍发 1：电子键拍发 2：电传拍发
   */
  private Integer type;
  /**
   * 内容
   */
  private String content;

}
