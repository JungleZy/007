package com.nip.entity.simulation.key;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 岗位电子键-多组多行信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "general_key_pat_train_more") //对应的数据库表
@Cacheable(value = false)
public class GeneralKeyPatTrainMoreEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "id", nullable = false)
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 训练id
   */
  @Column(name = "train_id")
  private Integer trainId;

  /**
   * 用户id
   */
  @Column(name = "user_id")
  private String userId;

  /**
   * 页码
   */
  @Column(name = "page_number")
  private Integer pageNumber;

  /**
   * 多组
   */
  @Column(name = "more_group")
  private String moreGroup;

  /**
   * 多行
   */
  @Column(name = "more_line")
  private String moreLine;

}
