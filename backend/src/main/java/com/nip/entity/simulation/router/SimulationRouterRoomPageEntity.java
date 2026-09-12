package com.nip.entity.simulation.router;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @Author: wushilin
 * @Data: 2023-03-23 09:56
 * @Description: 训练报底
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "simulation_router_room_page") //对应的数据库表
@Table(name = "simulation_router_room_page", uniqueConstraints = @UniqueConstraint(
    name = "uk_simulation_page_room_page_sort", columnNames = {"room_id", "page_number", "sort"}))
@Cacheable(value = false)
public class SimulationRouterRoomPageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
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
   * 排序字段
   */
  @Column(nullable = false)
  private Integer sort;

  /**
   * 报底
   */
  @Column(name = "`key`")
  private String key;

}
