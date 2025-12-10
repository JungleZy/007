package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: wushilin
 * @Data: 2023-03-15 14:03
 * @Description:
 */
@Data
@Entity(name = "t_post_telex_pat_train_page")
@AllArgsConstructor
@NoArgsConstructor
@Cacheable(value = false)
public class PostTelexPatTrainPageEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 训练id
   */
  private String trainId;

  /**
   * 生成得key
   */
  @Column(name = "`key`")
  private String key;

  /**
   * 用户拍发得内容
   */
  private String value;


  /**
   * 页码
   */
  private Integer pageNumber;


  /**
   * 排序字段
   */
  private Integer sort;

}
