package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-12-06 11:17
 * @Description:
 */
@Data
@Accessors(chain = true)
@Entity(name = "t_equipment_train")
@Cacheable(value = false)
public class EquipmentTrainEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 用户id
   */
  private String userId;

  /**
   * 训练内容
   */
  private String content;


  /**
   * 训练时间
   */
  private LocalDateTime trainTime;

  /**
   * 设备id
   */
  private String deviceId;

  /**
   * "训练名称"
   */
  private String trainName;

  /**
   * 训练状态 0 未开始 1已结束
   */
  private Integer trainStatus;

  /**
   * 训练类型
   */
  private Integer trainType;

  /**
   * 设备名称
   */
  private String deviceName;

}
