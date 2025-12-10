package com.nip.entity.simulation.telex;

import com.nip.dto.GeneralTelexPatUserDto;
import com.nip.dto.GeneralTelexPatUserSimpleDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "general_telex_pat_user") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries({
    @NamedNativeQuery(
        name = "find_general_telex_pat_user_dto",
        query = "SELECT u.user_name userName,u.user_img userImg,t.id,t.accuracy,t.deduct_info deductInfo,t.speed,t.error_number errorNumber," +
            "t.statistic_info statisticInfo,t.score,t.is_finish isFinish," +
            "t.create_time createTime,t.finish_time finishTime,t.duration,t.train_id trainId,t.user_id userId,t.role " +
            "from general_telex_pat_user t LEFT JOIN t_user u on u.id = t.user_id " +
            "where t.train_id = ?1 AND !ISNULL(u.id)",
        resultSetMapping = "general_telex_pat_user_dto"),
    @NamedNativeQuery(
        name = "find_general_telex_pat_user_simple_dto",
        query = "SELECT u.user_name userName,u.user_img userImg,t.user_id userId,t.role " +
            "from general_telex_pat_user t LEFT JOIN t_user u on u.id = t.user_id " +
            "where t.train_id = ?1 AND !ISNULL(u.id)",
        resultSetMapping = "general_telex_pat_user_simple_dto"),
})
@SqlResultSetMappings({
    @SqlResultSetMapping(name = "general_telex_pat_user_dto", classes = @ConstructorResult(targetClass = GeneralTelexPatUserDto.class, columns = {
        @ColumnResult(name = "id"), @ColumnResult(name = "accuracy")
        , @ColumnResult(name = "deductInfo"), @ColumnResult(name = "speed")
        , @ColumnResult(name = "errorNumber"), @ColumnResult(name = "statisticInfo")
        , @ColumnResult(name = "score"), @ColumnResult(name = "isFinish")
        , @ColumnResult(name = "createTime", type = LocalDateTime.class), @ColumnResult(name = "finishTime", type = LocalDateTime.class)
        , @ColumnResult(name = "duration"), @ColumnResult(name = "trainId")
        , @ColumnResult(name = "userId"), @ColumnResult(name = "role")
        , @ColumnResult(name = "userName")
        , @ColumnResult(name = "userImg")
    })),
    @SqlResultSetMapping(name = "general_telex_pat_user_simple_dto", classes = @ConstructorResult(targetClass = GeneralTelexPatUserSimpleDto.class, columns = {
        @ColumnResult(name = "userId"), @ColumnResult(name = "role")
        , @ColumnResult(name = "userName")
        , @ColumnResult(name = "userImg")
    })),
})
public class GeneralTelexPatUserEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Schema(title = "编号", required = true, type = SchemaType.STRING)
  private String id;

  /**
   * 训练ID
   */
  private String trainId;

  /**
   * 用户ID
   */
  private String userId;

  /**
   * 角色 0 参训人 1 组训人
   */
  private Integer role;

  /**
   * 正确率
   */
  private BigDecimal accuracy;

  /**
   * 扣分详情
   */
  private String deductInfo;

  /**
   * 总码率
   */
  private BigDecimal speed;
  /**
   * 每页码率
   */
  private String speedLog;
  /**
   * 总时长
   */
  private Integer validTime;

  /**
   * 每页时长
   */
  private String validTimeLog;

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
  private Integer isFinish = 0;

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
}
