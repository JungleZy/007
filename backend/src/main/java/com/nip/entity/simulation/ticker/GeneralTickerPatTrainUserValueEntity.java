package com.nip.entity.simulation.ticker;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "general_ticker_pat_train_user_value") //对应的数据库表
@Cacheable(value = false)
public class GeneralTickerPatTrainUserValueEntity implements Serializable {


  @Id
  @Column(name = "id", nullable = false)
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;


  /**
   * 训练id
   */
  private Integer trainId;

  /**
   * 用户id
   */
  private String userId;

  /**
   * 报底页数
   */
  private Integer floorNumber;

  /**
   * 客户按下松开的时间
   */
  private String messageBody;

  /**
   * 基准值
   */
  private String standard;

  /**
   * 完成信息
   */
  private String finishInfo;

  /**
   * 解析后的报文格式内容
   */
  private String resolver;

}
