package com.nip.entity.simulation.router;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @Author: wushilin
 * @Data: 2023-03-23 10:46
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "simulation_router_room_page_value") //对应的数据库表
@Cacheable(value = false)
public class SimulationRouterRoomPageValueEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;


  /**
   * 用户id
   */
  private String userId;

  /**
   * 训练id
   */
  private Integer roomId;


  /**
   * 页码
   */
  private Integer pageNumber;

  /**
   * 填报内容
   */
  private String value;

}
