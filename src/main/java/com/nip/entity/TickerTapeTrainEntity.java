package com.nip.entity;

import com.nip.dto.sql.TickerTapeTrainDaoCountBaseTrain;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收报训练
 *
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/4/6 13:40
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_ticker_tape_train") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries({@NamedNativeQuery(name = "count_base_train", query =
  "select sum(valid_time) totalTime,count(id) totalCount ,IFNULL(AVG(rate),0) avgSpeed " + "from t_ticker_tape_train "
    + "where user_id = :userId and type = :type", resultSetMapping = "count_base_train_mapping"),
  @NamedNativeQuery(name = "count_letter_train", query =
    "select sum(valid_time) totalTime,count(id) totalCount ,IFNULL(AVG(rate),0) avgSpeed " + "from t_ticker_tape_train "
      + "where user_id = ?1 and type < 3 and `status` = 3", resultSetMapping = "count_base_train_mapping")})

@SqlResultSetMappings({@SqlResultSetMapping(name = "count_base_train_mapping", classes = {
  @ConstructorResult(targetClass = TickerTapeTrainDaoCountBaseTrain.class, columns = {
    @ColumnResult(name = "totalTime", type = Integer.class), @ColumnResult(name = "totalCount", type = Integer.class),
    @ColumnResult(name = "avgSpeed", type = BigDecimal.class)})})})
public class TickerTapeTrainEntity implements Serializable {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  private String name;

  /**
   * '速率
   */
  private Integer rate;

  /**
   * '0': '字码报','1': '数码报','2': '混合报', 11 基础练习 12科室训练
   */
  private Integer type;

  /**
   * 报文内容
   */
  private String codeMessageBody;

  /**
   * 0：短码 1:长码
   */
  private Integer codeShort;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();

  /**
   * 开始时间
   */
  private LocalDateTime startTime;

  /**
   * 结束时间
   */
  private LocalDateTime endTime;

  /**
   * 有效时长
   */
  private String validTime;

  /**
   * 0:未开始 1:进行中 2：暂停 3：结束
   */
  private Integer status;

  /**
   * 创建人id
   */
  private String userId;

  /**
   * 报文上次的位置 "2,0"
   */
  private String mark;

  /**
   * 进度
   */
  private Integer schedule;

  /**
   * 是否低速训练 0是 1否
   */
  private Integer isLowRate;

}
