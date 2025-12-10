package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 14:42
 * @Description:
 */
@Data
@Entity(name = "t_post_radiotelephone_term_data")
@Cacheable(value = false)
public class PostRadiotelephoneTermDataEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;
  /**
   * 0 single 1 frase
   */
  private Integer type;
  @Column(name = "`key`")
  private String key;
  private String value;
  /**
   * 排序字段 (已弃用)
   */
  private Integer sort;

}
