package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 汉字录入要点讲解
 * @version v1.0.01
 * @Author：wushilin
 * @Date:Create 2022/3/23 15:43
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_entering_key_points") //对应的数据库表
@Cacheable(value = false)
public class EnteringKeyPointsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 0:收报方法 1情况处置 2常见毛病和修正方法
   */
  private Integer type;
  /**
   * 内容
   */
  private String content;
}
