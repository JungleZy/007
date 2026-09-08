package com.nip.entity.simulation.router;

import com.nip.dto.SimulationRouterRoomDto;
import com.nip.dto.SimulationRouterRoomSimpDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2023-03-01 16:57
 * @Description: 仿真训练 线路通报 房间类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "simulation_router_room") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries({
    @NamedNativeQuery(
        name = "find_simulation_router_room_dto",
        query = "SELECT r.id,r.is_cable isCable,r.name,r.is_start_sign isStartSign,rc.content,r.create_user_Id createUserId,r.stats,r.create_time createTime,rc.bd_type bdType,rc.bw_type bwType,rc.bw_count bwCount,u.user_name userName,u.user_img userImg " +
            "from simulation_router_room r " +
            "LEFT JOIN simulation_router_room_user ru on r.id = ru.room_id " +
            "LEFT JOIN simulation_router_room_content rc on rc.room_id = r.id " +
            "left join t_user u on r.create_user_Id = u.id " +
            "where r.room_type = 2 and ru.user_id = ?1 order by r.create_time desc ",
        resultSetMapping = "simulation_router_room_dto"),
    @NamedNativeQuery(
        name = "find_simulation_recept_room_dto",
        query = "SELECT r.id,r.is_cable isCable,r.name,r.is_start_sign isStartSign,rc.content,r.create_user_Id createUserId,r.stats,r.create_time createTime,rc.bd_type bdType,rc.bw_type bwType,rc.bw_count bwCount,u.user_name userName,u.user_img userImg " +
            "from simulation_router_room r " +
            "LEFT JOIN simulation_router_room_user ru on r.id = ru.room_id " +
            "LEFT JOIN simulation_router_room_content rc on rc.room_id = r.id " +
            "left join t_user u on r.create_user_Id = u.id " +
            "where r.room_type = 3 and ru.user_id = ?1 order by r.create_time desc ",
        resultSetMapping = "simulation_router_room_dto"),
    @NamedNativeQuery(
        name = "find_simulation_router_room_simp_dto",
        query = "SELECT r.id,r.name,r.is_start_sign isStartSign,r.is_cable isCable,rc.content,r.create_user_Id createUserId,r.stats,r.create_time createTime,rc.bd_type bdType,rc.bw_type bwType,rc.bw_count bwCount " +
            "from simulation_router_room r " +
            "LEFT JOIN simulation_router_room_user ru on r.id = ru.room_id " +
            "LEFT JOIN simulation_router_room_content rc on rc.room_id = r.id " +
            "where r.room_type = 0 and (ru.user_id = ?1 or r.create_user_Id = ?1) order by r.create_time desc ",
        resultSetMapping = "simulation_router_room_simp_dto"),
})
@SqlResultSetMappings({
    @SqlResultSetMapping(name = "simulation_router_room_dto", classes = @ConstructorResult(targetClass = SimulationRouterRoomDto.class, columns = {
        @ColumnResult(name = "id"), @ColumnResult(name = "name"), @ColumnResult(name = "isCable")
        , @ColumnResult(name = "createUserId"), @ColumnResult(name = "stats"), @ColumnResult(name = "isStartSign")
        , @ColumnResult(name = "createTime", type = LocalDateTime.class), @ColumnResult(name = "bdType")
        , @ColumnResult(name = "bwType"), @ColumnResult(name = "bwCount")
        , @ColumnResult(name = "userName"), @ColumnResult(name = "userImg")
    })),
    @SqlResultSetMapping(name = "simulation_router_room_simp_dto", classes = @ConstructorResult(targetClass = SimulationRouterRoomSimpDto.class, columns = {
        @ColumnResult(name = "id"), @ColumnResult(name = "name"), @ColumnResult(name = "isCable")
        , @ColumnResult(name = "createUserId"), @ColumnResult(name = "stats"), @ColumnResult(name = "isStartSign")
        , @ColumnResult(name = "createTime", type = LocalDateTime.class), @ColumnResult(name = "bdType")
        , @ColumnResult(name = "bwType"), @ColumnResult(name = "bwCount")
    })),
})
public class SimulationRouterRoomEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;


  /**
   * 房间名称
   */
  private String name;

  /**
   * 是否是固定报 0 随机报 1 固定报
   */
  private Integer isCable = 0;

  /**
   * 创建人id
   */
  private String createUserId;

  /**
   * 房间状态 0 开启 1进行中 2结束
   */
  private Integer stats;

  /**
   * 播报状态 0 暂停 1 播报
   */
  private Integer playStatus;

  /**
   * 房间类型 0 线路通报 1 快速/干扰报 2通报教学
   */
  private Integer roomType;


  private LocalDateTime createTime = LocalDateTime.now();

  /**
   * 训练耗时时长(秒)
   */
  private Integer totalTime;


  /**
   * 开始训练时间
   */
  private LocalDateTime startTime;


  /**
   * 训练配置（快报、干扰报）
   */
  private String setting;
  private Integer isStartSign = 1;
}
