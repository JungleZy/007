package com.nip.entity;

import com.nip.dto.vo.PostTelexPatTrainVO;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 11:06
 * @Description:
 */
@Data
@Entity(name = "t_post_telex_pat_train")
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Cacheable(value = false)
@NamedNativeQueries({
    @NamedNativeQuery(name = "find_train_list", query =
        "SELECT t.id,t.name,t.is_cable isCable,t.type,t.status,t.speed,t.valid_time validTime ,t.score,t.total_speed totalSpeed," +
            "CASE WHEN t.is_cable = 0 THEN t.group_number ELSE pc.groupNumber END AS groupNumber " +
            "FROM t_post_telex_pat_train t " +
            "LEFT JOIN ( SELECT train_id, count( id ) groupNumber FROM t_post_telex_pat_train_page GROUP BY train_id ) pc ON pc.train_id = t.id " +
            "LEFT JOIN ( SELECT train_id, max( page_number ) pageNumber FROM t_post_telex_pat_train_page ) pm ON pm.train_id = t.id " +
            "WHERE t.train_type=:trainType AND t.create_user= :userId ORDER BY t.create_time desc", resultSetMapping = "train_list")
})

@SqlResultSetMappings({
    @SqlResultSetMapping(name = "train_list", classes = {
        @ConstructorResult(targetClass = PostTelexPatTrainVO.class, columns = {
            @ColumnResult(name = "id", type = String.class),
            @ColumnResult(name = "name", type = String.class),
            @ColumnResult(name = "isCable", type = Integer.class),
            @ColumnResult(name = "type", type = Integer.class),
            @ColumnResult(name = "status", type = Integer.class),
            @ColumnResult(name = "speed", type = String.class),
            @ColumnResult(name = "totalSpeed", type = String.class),
            @ColumnResult(name = "validTime", type = Integer.class),
            @ColumnResult(name = "score", type = String.class),
            @ColumnResult(name = "groupNumber", type = Integer.class)
        })
    })
})
public class PostTelexPatTrainEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /**
   * 训练名称
   */
  @Column(name = "`name`")
  private String name;

  /**
   * 是否是固定报 0 固定报 1 随机报
   */
  private Integer isCable;

  /**
   * 0 数字连贯1 字母连贯 2 组合连贯
   */
  private Integer type;

  /**
   * 训练类型 0 电传拍发 1 数据报拍发
   */
  private Integer trainType;

  /**
   * 创建人ID
   */
  private String createUser;

  /**
   * 状态 0 未开始 1进行中 2 暂停 3完成
   */
  @Column(name = "`status`")
  private Integer status;

  /**
   * 开始时间
   */
  private LocalDateTime startTime;

  /**
   * 结束时间
   */
  private LocalDateTime endTime;

  /**
   * 组数
   */
  private Integer groupNumber;

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
   * 速率
   */
  private String speedLog;

  /**
   * 训练时长
   */
  private Integer validTime;

  /**
   * 每页训练时长
   */
  private String validTimeLog;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();

  /**
   * 内容
   */
  private String content;

  /**
   * 得分
   */
  private String score;

  /**
   * 修改次数
   */
  @Column(name = "`change`")
  private Integer change;

  /**
   * 评分规则id
   */
  private String ruleId;

  /**
   * 具体评分规则
   */
  private String ruleContent;

  /**
   * 扣分规则
   */
  private String deductInfo;

  /**
   * 平均速率（20230606BBB新增）
   */
  private String totalSpeed;

  /**
   * 报底类型 0 挨指 1 对手
   */
  private Integer patType;
}
