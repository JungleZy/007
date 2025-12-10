package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备描述
 */
@Data
@Entity
//@ApiModel("设备描述")
@Table(name = "t_device_description")
//@EntityListeners(value = AuditingEntityListener.class)

public class DeviceDescriptionEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "id", nullable = false)
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 标题
   */
  @Column(name = "title")
  //@ApiModelProperty("标题")
  private String title;


  //@ApiModelProperty("设备id")
  private Integer deviceId;

  /**
   * 描述
   */
  //@ApiModelProperty("描述")
  @Column(name = "content")
  private String content;

  /**
   * 创建人
   */
  //@ApiModelProperty("创建人")
  @Column(name = "user_id")
  private String userId;

  /**
   * 创建时间
   */
  //@ApiModelProperty("创建时间")
  @Column(name = "create_time")
  //CreatedDate
  private LocalDateTime createTime = LocalDateTime.now();

}
