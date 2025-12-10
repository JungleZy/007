package com.nip.entity.simulation.router;

import com.nip.dto.SimulationRouterRoomUserDto;
import com.nip.dto.SimulationRouterRoomUserSimpDto;
import jakarta.persistence.*;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2023-03-01 17:02
 * @Description: 仿真训练 线路通报 房间人员类
 */
@Data
@Entity(name = "simulation_router_room_user") //对应的数据库表
@NamedNativeQueries({
    @NamedNativeQuery(
        name = "find_simulation_router_room_user_simp_dto",
        query = "SELECT u.id, u.user_name name,ru.user_type userType, u.user_img userImg, ru.channel " +
            "FROM simulation_router_room_user ru " +
            "LEFT JOIN t_user u " +
            "ON u.id = ru.user_id " +
            "WHERE ru.user_id = ?1  AND room_id = ?2",
        resultSetMapping = "simulation_router_room_user_simp_dto"),
    @NamedNativeQuery(
        name = "find_simulation_router_room_user_dto",
        query = "select tu.id,tu.user_name userName,tu.user_img userImg,sru.channel,sru.content_value contentValue,sru.user_status userStatus " +
            "from simulation_router_room_user sru " +
            "left join t_user tu on sru.user_id = tu.id " +
            "WHERE room_id =?1 and user_type=1",
        resultSetMapping = "simulation_router_room_user_dto"),
})
@SqlResultSetMappings({
    @SqlResultSetMapping(name = "simulation_router_room_user_dto", classes = @ConstructorResult(targetClass = SimulationRouterRoomUserDto.class, columns = {
        @ColumnResult(name = "id"), @ColumnResult(name = "userName")
        , @ColumnResult(name = "userImg"), @ColumnResult(name = "channel")
        , @ColumnResult(name = "contentValue"), @ColumnResult(name = "userStatus")
    })),
    @SqlResultSetMapping(name = "simulation_router_room_user_simp_dto", classes = @ConstructorResult(targetClass = SimulationRouterRoomUserSimpDto.class, columns = {
        @ColumnResult(name = "id"), @ColumnResult(name = "name")
        , @ColumnResult(name = "userType"), @ColumnResult(name = "userImg")
        , @ColumnResult(name = "channel")
    })),
})
public class SimulationRouterRoomUserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 房间id
   */
  private Integer roomId;

  /**
   * 人员id
   */
  private String userId;


  /**
   * 类型 0 发报 1 收报
   */
  private Integer userType;

  /**
   * 频道
   * 如果是发报人员对应发报频道
   * 如果是收报人员对应收报频道
   */
  private Integer channel;

  /**
   * 收报人员报文
   */
  private String contentValue;

  /**
   * 发报人状态 0 未完成 1 已完成
   */
  private Integer userStatus;


}
