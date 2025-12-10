package com.nip.entity.simulation.ticker;

import com.nip.dto.GeneralKeyPatTrainScoreDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2023-04-08 14:54
 * @Description: 综合训练手键拍发
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Entity(name = "general_ticker_pat") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries({
    @NamedNativeQuery(
        name = "find_general_ticker_pat_train_score_dto",
        query = "SELECT IFNULL(tu.score,0) as score,t.rule_content as ruleContent " +
            "from general_ticker_pat t " +
            "LEFT JOIN general_ticker_pat_train_user tu on tu.train_id=t.id " +
            "WHERE t.`status` =2 and t.train_type =1 and t.end_time >= ?2 " +
            "and t.end_time <= ?3 and tu.user_id in ?1",
        resultSetMapping = "general_ticker_pat_train_score_dto"),
})
@SqlResultSetMappings({
    @SqlResultSetMapping(name = "general_ticker_pat_train_score_dto", classes = @ConstructorResult(targetClass = GeneralKeyPatTrainScoreDto.class, columns = {
        @ColumnResult(name = "score"), @ColumnResult(name = "ruleContent")
    })),
})
public class GeneralTickerPatTrainEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Schema(title = "是否固定报0 不 1是")
  private Integer isCable;

  /**
   * 训练名称
   */
  private String name;

  /**
   * 类型 0 数码报 1 字码报 2 混合报
   */
  private Integer type;

  /**
   * 训练类型 0个人训练 1岗位训练
   */
  private Integer trainType;

  /**
   * 短码长码 0 短码 1 长码
   */
  private Integer codeSort;

  /**
   * 是否随机 0否 1是
   */
  private Integer isRandom;


  /**
   * 是否平均0否 1是
   */
  private Integer isAverage;

  /**
   * 报底
   */
  private Integer messageNumber;


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
  private Long validTime;

  /**
   * 0未开始，1，进行中，2已完成
   */
  private Integer status;

  /**
   * 创建人ID
   */
  private String createUser;

  /**
   * 评分规则Id
   */
  private String ruleId;

  /**
   * 评分规则信息
   */
  private String ruleContent;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();

}
