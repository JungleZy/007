package com.nip.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/21 17:44
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_telex_pat") //对应的数据库表
@Cacheable(value = false)
public class TelexPatEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String userId;
  private Integer count;
  private Integer mistake;
  /**
   * 训练时长
   */
  private Long duration;
  /**
   * 0数码训练，1：字码训练，2：特殊键 3 改错练习
   */
  private Integer type;
}
