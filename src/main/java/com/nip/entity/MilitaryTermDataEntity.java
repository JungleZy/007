package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * MilitaryTermDataEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-06-23 14:46
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_military_term_data") //对应的数据库表
@Accessors(chain = true)
@Cacheable(value = false)
public class MilitaryTermDataEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String parentId;
  @Column(name = "`key`")
  private String key;
  private String value;
  private Integer sort = 0;
}
