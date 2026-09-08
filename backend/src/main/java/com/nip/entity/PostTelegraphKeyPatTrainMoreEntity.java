package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

/**
 * 岗位电子键-多组多行信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "t_post_telegraph_key_pat_train_more")
@Cacheable(value = false)
public class PostTelegraphKeyPatTrainMoreEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 训练id
   */
  @Schema(name = "训练id")
  @Column(name = "train_id")
  private String trainId;

  /**
   * 用户id
   */
  @Column(name = "user_id")
  @Schema(name = "用户id")
  private String userId;

  /**
   * 页码
   */
  @Schema(name = "页码")
  @Column(name = "page_number")
  private Integer pageNumber;

  /**
   * 多组
   */
  @Schema(name = "多组")
  @Column(name = "more_group")
  private String moreGroup;

  /**
   * 多行
   */
  @Schema(name = "多行")
  @Column(name = "more_line")
  private String moreLine;

}
