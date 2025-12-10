package com.nip.entity.simulation.ticker;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "general_ticker_pat_train_page") //对应的数据库表
@Cacheable(value = false)
public class GeneralTickerPatTrainPageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 训练ID
   */
  private Integer trainId;
  /**
   * 保底编号
   */
  private Integer floorNumber;
  /**
   * 排序字段
   */
  private Integer sort;
  /**
   * 摩尔斯电码key
   */
  private String moresKey;
  /**
   * 值
   */
  private String moresValue = "[]";
  /**
   * 输入时间
   */
  private String moresTime = "[]";
  /**
   * 拍发的电码
   */
  private String patKeys;

}
