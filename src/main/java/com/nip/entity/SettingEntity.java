package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SettingEntity
 *
 * @author  < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date    2022-01-03 18:12:52
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_setting") //对应的数据库表
@Cacheable(value = false)
public class SettingEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private Integer code;
  private String content;
}
