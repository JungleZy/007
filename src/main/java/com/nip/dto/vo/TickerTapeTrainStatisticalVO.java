package com.nip.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/29 8:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "收报训练统计VO对象")
public class TickerTapeTrainStatisticalVO {

  @Schema(name = "id")
  private String id;
  /**
   * 类型（0 基础练习 1科室练习 2单字练习
   */
  @Schema(name = "0 基础练习 1科室练习 2单字练习")
  private Integer type;

  /**
   * 训练次数
   */
  @Schema(name = "训练次数")
  private Integer totalCount;

  /**
   * 训练总时长
   */
  @Schema(name = "训练总时长")
  private String totalTime;

  /**
   * 平均速率
   */
  @Schema(name = "平均速率")
  private BigDecimal avgSpeed;

  /**
   * 用户id
   */
  @Schema(name = "用户id")
  private String userId;

}
