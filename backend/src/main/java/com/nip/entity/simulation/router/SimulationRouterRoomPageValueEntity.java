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
@Table(name = "simulation_router_room_page_value", uniqueConstraints = @UniqueConstraint(
    name = "uk_simulation_value_room_user_page", columnNames = {"room_id", "user_id", "page_number"}))
@Cacheable(value = false)
public class SimulationRouterRoomPageValueEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;


  /**
   * 用户id
   */
  @Column(nullable = false)
  private String userId;

  /**
   * 训练id
   */
  @Column(nullable = false)
  private Integer roomId;


  /**
   * 页码
   */
  @Column(nullable = false)
  private Integer pageNumber;

  /**
   * 填报内容
   */
  @Column(columnDefinition = "longtext")
  private String value;

}
