package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GradingRuleEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-05-10 18:14:31
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_grading_rule") //对应的数据库表
@Cacheable(value = false)
public class GradingRuleEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private Integer type = 0; // 0，手键规则；1，电子键规则；2，电传规则
  private String title; // 规则名称
  private Integer score; // 总分
  private Integer status = 0; // 状态，0，启用，1，关闭
  private Integer isDefault = 1; // 是否使默认，0，使，1，否
  private String content; // 规则json

}
