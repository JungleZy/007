package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity(name = "t_device_type")
@Cacheable(value = false)
public class DeviceTypeEntity {

  /**
   * id
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 类型名称
   */
  private String typeName;

  /**
   * 创建人
   */
  private String userId;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();

}
