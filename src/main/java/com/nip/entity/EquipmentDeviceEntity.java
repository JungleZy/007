package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;


/**
 * @Author: wushilin
 * @Data: 2022-10-31 11:45
 * @Description:
 */
@Data
@Accessors(chain = true)
@Entity(name = "t_equipment_device")
@Cacheable(value = false)
public class EquipmentDeviceEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;


  /**
   * 设备ID
   */
  private String deviceId;

  /**
   * 设备名称
   */
  private String name;

  /**
   * 图片地址
   */
  private String image;

  /**
   * 表格字段
   */
  private String option;

  /**
   * 是否启用
   */
  private int isEnable;


  /**
   * 要点
   */
  private String keyPoints;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();
}
