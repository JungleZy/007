package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;


/**
 * @Author: wushilin
 * @Data: 2023-02-21 17:01
 * @Description:  收报管理-训练表 阶段配置表
 */
@Data
@Entity(name = "t_ticker_tape_train_stage_setting")
@Cacheable(value = false)
public class TickerTapeTrainStageSettingEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;
  /**
   * 阶段配置内容
   */
  private String stageArray;
}
