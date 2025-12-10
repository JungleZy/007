package com.nip.entity.simulation.ticker;

import com.nip.dto.GeneralTickerPatTrainUserDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "general_ticker_pat_train_user") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries({
    @NamedNativeQuery(
        name = "find_general_ticker_pat_train_user_dto",
        query = "SELECT u.user_name userName,u.user_img userImg,t.id,t.train_id trainId,t.user_id userId,t.role,t.score,t.deduct_info deductInfo,t.statistic_info statisticInfo," +
            "t.error_number errorNumber,t.accuracy,t.speed,t.speed_log speedLog,t.lack,t.finish_time finishTime,t.create_time createTime,t.is_finish isFinish " +
            "from general_ticker_pat_train_user t LEFT JOIN t_user u on u.id = t.user_id " +
            "where t.train_id = ?1 and  if(?2  is not null and ?2!='',u.id=?2,1=1) AND ! ISNULL( u.id )",
        resultSetMapping = "general_ticker_pat_train_user_dto"),
})
@SqlResultSetMappings({
    @SqlResultSetMapping(name = "general_ticker_pat_train_user_dto", classes = @ConstructorResult(targetClass = GeneralTickerPatTrainUserDto.class, columns = {
        @ColumnResult(name = "id"), @ColumnResult(name = "trainId"), @ColumnResult(name = "userId"), @ColumnResult(name = "role"),
        @ColumnResult(name = "score", type = BigDecimal.class), @ColumnResult(name = "deductInfo"), @ColumnResult(name = "statisticInfo"),
        @ColumnResult(name = "errorNumber"),
        @ColumnResult(name = "accuracy"), @ColumnResult(name = "speed"), @ColumnResult(name = "speedLog"), @ColumnResult(name = "lack"),
        @ColumnResult(name = "finishTime", type = LocalDateTime.class), @ColumnResult(name = "createTime", type = LocalDateTime.class),
        @ColumnResult(name = "isFinish"), @ColumnResult(name = "userName"),
        @ColumnResult(name = "userImg"),
    })),
})
public class GeneralTickerPatTrainUserEntity implements Serializable {


  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  private Integer trainId;

  private String userId;

  /**
   * 角色 0 参训人 1 组训人
   */
  private Integer role;

  /**
   * 得分
   */
  private BigDecimal score;

  /**
   * 扣分详情
   */
  private String deductInfo;

  /**
   * 统计信息 点、划、间隔
   */
  private String statisticInfo;

  /**
   * 错误个数
   */
  private Integer errorNumber;

  /**
   * 正确率
   */
  private String accuracy;

  /**
   * 速率
   */
  private String speed;

  /**
   * 拍发记录
   */
  private String speedLog;

  /**
   * 漏拍
   */
  private Integer lack;


  /**
   * 训练结束时间
   */
  private LocalDateTime finishTime;


  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();

  /**
   * 0 未完成 1 已完成
   */
  private Integer isFinish;

}
