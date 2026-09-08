package com.nip.entity.simulation.key;

import com.nip.dto.GeneralKeyPatUserDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 参训用户表
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "general_key_pat_user") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries({
    @NamedNativeQuery(
        name = "find_general_key_pat_user_dto",
        query = "SELECT u.user_name userName,u.user_img userImg,t.id,t.accuracy,t.deduct_info deductInfo,t.speed,t.error_number errorNumber," +
            "t.statistic_info statisticInfo,t.score,t.is_finish isFinish," +
            "t.create_time createTime,t.finish_time finishTime,t.duration,t.train_id trainId,t.user_id userId,t.role,t.content " +
            "from general_key_pat_user t LEFT JOIN t_user u on u.id = t.user_id " +
            "where t.train_id = ?1 AND !ISNULL(u.id)",
        resultSetMapping = "general_key_pat_user_dto"),
})
@SqlResultSetMappings({
    @SqlResultSetMapping(name = "general_key_pat_user_dto", classes = @ConstructorResult(targetClass = GeneralKeyPatUserDto.class, columns = {
        @ColumnResult(name = "id"), @ColumnResult(name = "accuracy")
        , @ColumnResult(name = "deductInfo"), @ColumnResult(name = "speed")
        , @ColumnResult(name = "errorNumber"), @ColumnResult(name = "statisticInfo")
        , @ColumnResult(name = "score"), @ColumnResult(name = "isFinish")
        , @ColumnResult(name = "createTime", type = LocalDateTime.class), @ColumnResult(name = "finishTime", type = LocalDateTime.class)
        , @ColumnResult(name = "duration"), @ColumnResult(name = "trainId")
        , @ColumnResult(name = "userId"), @ColumnResult(name = "role")
        , @ColumnResult(name = "content"), @ColumnResult(name = "userName")
        , @ColumnResult(name = "userImg")
    })),
})
public class GeneralKeyPatUserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 正确率
   */
  private String accuracy;

  /**
   * 扣分详情
   */
  private String deductInfo;

  /**
   * 速率
   */
  private String speed;

  /**
   * 错误个数
   */
  private Integer errorNumber;

  /**
   * 统计信息
   */
  private String statisticInfo;

  /**
   * 得分
   */
  private BigDecimal score;

  /**
   * 0 未完成 1 已完成
   */
  private Integer isFinish;

  /**
   * 训练创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();

  /**
   * 训练结束时间
   */
  private LocalDateTime finishTime;

  /**
   * 时长
   */
  private String duration;

  /**
   * 训练ID
   */
  private Integer trainId;

  /**
   * 用户ID
   */
  private String userId;

  /**
   * 角色 0 参训人 1 组训人
   */
  private Integer role;

  private String content;
}
