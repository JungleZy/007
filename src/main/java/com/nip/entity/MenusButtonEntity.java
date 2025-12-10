package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MenusButtonEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-08-02 15:34
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_menus_button") //对应的数据库表
@Cacheable(value = false)
public class MenusButtonEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String menusId;
  private String title;
  @Column(name = "`key`")
  private String key;
}
