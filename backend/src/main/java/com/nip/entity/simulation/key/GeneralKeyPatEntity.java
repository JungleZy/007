package com.nip.entity.simulation.key;

import com.nip.dto.GeneralKeyPatTrainScoreDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 组训-电子键拍发
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "general_key_pat") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries({
    @NamedNativeQuery(
        name = "find_general_key_pat_train_score_dto",
        query = "SELECT pu.score,p.rule_content as ruleContent from general_key_pat p " +
            " LEFT JOIN general_key_pat_user pu on pu.train_id = p.id " +
            " where p.create_time>=:startTime and p.create_time<=:endTime and p.`status` =2 and p.train_type =1 and pu.user_id in :uidList",
        resultSetMapping = "general_key_pat_train_score_dto"),
})
@SqlResultSetMappings({
    @SqlResultSetMapping(name = "general_key_pat_train_score_dto", classes = @ConstructorResult(targetClass = GeneralKeyPatTrainScoreDto.class, columns = {
        @ColumnResult(name = "score"), @ColumnResult(name = "ruleContent")
    })),
})
public class GeneralKeyPatEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /**
   * 房间名称
   */
  private String title;

  private Integer isCable = 0;

  /**
   * 报底数量
   */
  private Integer totalNumber;

  /**
   * 评分规则ID
   */
  private String ruleId;

  /**
   * 评分规则内容
   */
  private String ruleContent;

  /**
   * 创建人UID
   */
  private String createUser;

  /**
   * 创建训练时间
   */
  private LocalDateTime createTime = LocalDateTime.now();

  /**
   * 开始训练时间
   */
  private LocalDateTime startTime;

  /**
   * 结束训练时间
   */
  private LocalDateTime endTime;

  /**
   * 训练状态
   * 0未开始，1，进行中，2已完成
   */
  private Integer status;

  /**
   * 有效时长
   */
  private Long validTime;

  private Integer messageType;

  private Integer isAverage;

  private Integer isRandom;

  /**
   * 0个人训练 1考核训练
   */
  private Integer trainType;
}
