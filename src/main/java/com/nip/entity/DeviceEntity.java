package com.nip.entity;


import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 装备操作设备表
 */
@Data
@Entity
//@ApiModel("装备操作设备表")
@Table(name = "t_device")
//@EntityListeners(value = AuditingEntityListener.class)
@Cacheable(value = false)
public class DeviceEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * id
   */
  @Id
  //@ApiModelProperty("id")
  @Column(name = "id", nullable = false)
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 设备名称
   */
  //@ApiModelProperty("设备名称")
  @Column(name = "device_name")
  private String deviceName;

  /**
   * 设备编号
   */
  //@ApiModelProperty("设备编号")
  @Column(name = "device_number")
  private String deviceNumber;

  /**
   * 设备图片
   */
  //@ApiModelProperty("设备图片")
  @Column(name = "device_img")
  private String deviceImg;

  /**
   * 设备分类id
   */
  //@ApiModelProperty("设备分类id")
  @Column(name = "device_type_id")
  private Integer deviceTypeId;

  //@ApiModelProperty(value = "创建id")
  private String userId;

  //@ApiModelProperty(value = "创建时间")
  //@CreatedDate
  private LocalDateTime createTime = LocalDateTime.now();

}
