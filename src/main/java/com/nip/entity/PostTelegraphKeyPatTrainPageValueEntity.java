package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @Author: wushilin
 * @Data: 2023-03-15 14:03
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_post_telegraph_key_pat_train_page_value")
@Accessors(chain = true)
@Cacheable(value = false)
public class PostTelegraphKeyPatTrainPageValueEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 训练id
   */
  private String trainId;

  /**
   * 页码
   */
  private Integer pageNumber;


  /**
   * 生成的key，多拍的key是#
   */
  @Column(name = "`key`")
  private String key;


  /**
   * 用户拍发得内容
   */
  private String value;

  /**
   * 拍发得时间
   */
  private String time;

  /**
   * 排序字段
   */
  private Integer sort;

  public PostTelegraphKeyPatTrainPageValueEntity(String trainId, Integer pageNumber, String key, String value, String time, Integer sort) {
    this.trainId = trainId;
    this.pageNumber = pageNumber;
    this.key = key;
    this.value = value;
    this.time = time;
    this.sort = sort;
  }

}
