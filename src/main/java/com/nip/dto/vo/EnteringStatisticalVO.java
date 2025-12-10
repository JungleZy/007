package com.nip.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * @version v1.0.01
 * @Author: BBB
 * @Date: Create 2022/3/29 8:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "拼音训练统计VO对象")
public class EnteringStatisticalVO {

  @Schema(name = "id")
  private String id;
  /**
   * 类型（0异音同字 1 同音异字 2 连音词组
   */
  @Schema(name = "0异音同字 1 同音异字 2 连音词组")
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
