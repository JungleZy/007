package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @Author: wushilin
 * @Data: 2022-04-11 09:17
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_receive_key_points")
@Cacheable(value = false)
public class ReceiveKeyPointsEntity implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 0:手键拍发 1：电子键拍发 2：电传拍发
   */
  private Integer type;
  /**
   * 内容
   */
  private String content;
}
