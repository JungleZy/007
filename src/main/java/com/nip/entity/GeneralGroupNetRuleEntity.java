package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@Entity(name = "t_general_group_net_rule")
@Cacheable(value = false)
public class GeneralGroupNetRuleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 配置
   */
  private String xyScore;

  /**
   * 配置
   */
  private String device;

  /**
   * 代码
   */
  private String code;

  private LocalDateTime createTime = LocalDateTime.now();

}
