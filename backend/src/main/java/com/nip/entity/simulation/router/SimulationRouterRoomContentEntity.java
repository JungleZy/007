package com.nip.entity.simulation.router;

import com.nip.dto.SimulationRouterRoomContentDto;
import com.nip.dto.SimulationRouterRoomContentMessageDto;
import com.nip.dto.SimulationRouterRoomContentRecordDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2023-03-01 17:05
 * @Description: 仿真训练 线路通报 房间报底类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "simulation_router_room_content") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries({
    @NamedNativeQuery(
        name = "find_simulation_router_room_content_dto",
        query = "SELECT r.name,r.is_cable isCable, r.stats,r.create_user_Id createUserId,r.total_time totalTime,r.play_status playStatus," +
            "rc.id,rc.room_id roomId,rc.content,rc.main_signal mainSignal,rc.interference_signal interferenceSignal, " +
            "rc.bd_type bdType,rc.bw_type bwType,rc.bw_count bwCount,rc.is_random isRandom " +
            "from simulation_router_room r LEFT JOIN simulation_router_room_content rc on r.id = rc.room_id " +
            "WHERE r.id = ?1",
        resultSetMapping = "simulation_router_room_content_dto"),
    @NamedNativeQuery(
        name = "find_simulation_router_room_content_record_dto",
        query = "SELECT sr.id, sr.name,sr.is_cable isCable, sr.create_user_Id createUserId, sr.stats, sr.create_time createTime, " +
            "src.bw_type bwType, src.bd_type bdType, src.bw_count bwCount, src.room_id roomId, tu.user_name userName, " +
            "tu.user_img userimg " +
            "FROM simulation_router_room sr " +
            "LEFT JOIN simulation_router_room_content src ON sr.id = src.room_id " +
            "LEFT JOIN t_user tu ON sr.create_user_Id = tu.id " +
            "WHERE sr.room_type = 1 ORDER BY  create_time Desc , stats",
        resultSetMapping = "simulation_router_room_content_record_dto"),
    @NamedNativeQuery(
        name = "find_simulation_router_room_content_message_dto",
        query = "SELECT sr.name,sr.is_cable isCable, sr.create_user_Id createUserId, sr.stats, sr.create_time createTime, src.bw_type bwType," +
            " src.bd_type bdType, src.bw_count bwCount," +
            "src.content, src.main_signal mainSignal, src.interference_signal interferenceSignal, src.room_id roomId, " +
            "tu.id, tu.user_name userName, tu.user_img userimg,sr.total_time totalTime, sr.setting " +
            "FROM simulation_router_room sr " +
            "LEFT JOIN simulation_router_room_content src ON sr.id = src.room_id " +
            "left join t_user tu on sr.create_user_Id = tu.id " +
            "WHERE src.room_id =?1",
        resultSetMapping = "simulation_router_room_content_message_dto"),
})
@SqlResultSetMappings({
    @SqlResultSetMapping(name = "simulation_router_room_content_dto", classes = @ConstructorResult(targetClass = SimulationRouterRoomContentDto.class, columns = {
        @ColumnResult(name = "name"), @ColumnResult(name = "isCable"), @ColumnResult(name = "createUserId"),
        @ColumnResult(name = "stats"), @ColumnResult(name = "isRandom"),
        @ColumnResult(name = "totalTime"), @ColumnResult(name = "playStatus"),
        @ColumnResult(name = "id"), @ColumnResult(name = "roomId"),
        @ColumnResult(name = "content"), @ColumnResult(name = "mainSignal"),
        @ColumnResult(name = "interferenceSignal"), @ColumnResult(name = "bdType"),
        @ColumnResult(name = "bwType"), @ColumnResult(name = "bwCount"),
    })),
    @SqlResultSetMapping(name = "simulation_router_room_content_record_dto", classes = @ConstructorResult(targetClass = SimulationRouterRoomContentRecordDto.class, columns = {
        @ColumnResult(name = "id"), @ColumnResult(name = "name"), @ColumnResult(name = "isCable"),
        @ColumnResult(name = "createUserId"), @ColumnResult(name = "stats"),
        @ColumnResult(name = "createTime", type = LocalDateTime.class), @ColumnResult(name = "roomId"),
        @ColumnResult(name = "bdType"), @ColumnResult(name = "bwType"),
        @ColumnResult(name = "bwCount"), @ColumnResult(name = "userName"),
        @ColumnResult(name = "userImg"),
    })),
    @SqlResultSetMapping(name = "simulation_router_room_content_message_dto", classes = @ConstructorResult(targetClass = SimulationRouterRoomContentMessageDto.class, columns = {
        @ColumnResult(name = "id"), @ColumnResult(name = "name"), @ColumnResult(name = "isCable"),
        @ColumnResult(name = "createUserId"), @ColumnResult(name = "stats"),
        @ColumnResult(name = "createTime", type = LocalDateTime.class), @ColumnResult(name = "bdType"),
        @ColumnResult(name = "bwType"), @ColumnResult(name = "bwCount"),
        @ColumnResult(name = "content"), @ColumnResult(name = "mainSignal"),
        @ColumnResult(name = "interferenceSignal"), @ColumnResult(name = "roomId"),
        @ColumnResult(name = "userName"), @ColumnResult(name = "userImg"),
        @ColumnResult(name = "totalTime"), @ColumnResult(name = "setting"),
    })),
})
public class SimulationRouterRoomContentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 房间id
   */
  private Integer roomId;

  /**
   * 报文内容
   */
  private String content;

  /**
   * 主信号
   */
  private String mainSignal;

  /**
   * 干扰信号
   */
  private String interferenceSignal;


  /**
   * 报底类型 1=平均保底 2=乱码报底
   */
  private Integer bdType;

  /**
   * 报文类型 1=数字短码 2=数字长码 3=字码 4=混合报
   */
  private Integer bwType;

  /**
   * 报文组数
   */
  private Integer bwCount;

  /**
   * 是否随机
   */
  private Integer isRandom;


}
