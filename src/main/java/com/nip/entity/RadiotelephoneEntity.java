package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 09:09
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_radiotelephone_train")
@Cacheable(value = false)
public class RadiotelephoneEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;


  /**
   * 用户id
   */
  private String userId;


  /**
   * 0 通报用语 1 军语密语
   */
  private Integer type;

  /**
   * 总时长
   */
  private String totalTime;

  /**
   * 训练次数
   */
  private Integer totalCount;

}
