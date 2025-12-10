package com.nip.entity.simulation.key;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 拍发目标数据
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "general_key_pat_page") //对应的数据库表
@Cacheable(value = false)
public class GeneralKeyPatPageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 生成的key
   */
  @Column(name = "`key`")
  private String key;

  /**
   * 页码
   */
  private Integer pageNumber;

  /**
   * 排序字段
   */
  private Integer sort;

  private String time;

  /**
   * 训练ID
   */
  private Integer trainId;

  private String value;
}
