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
@Schema(name = "电传拍发统计VO对象")
public class TelexPatTrainStatisticalVO {

  @Schema(name = "id",title = "id")
  private String id;
  /**
   * 类型（0 单字训练 1 数字连贯 2字母连贯 3混合连贯
   */
  @Schema(name = "type",title = "0 单字训练 1 数字连贯 2字母连贯 3混合连贯")
  private Integer type;

  /**
   * 训练次数
   */
  @Schema(name = "totalCount",title = "训练次数")
  private Integer totalCount;

  /**
   * 训练总时长
   */
  @Schema(name = "totalTime",title = "训练总时长")
  private String totalTime;

  /**
   * 平均速率
   */
  @Schema(name = "avgSpeed",title = "平均速率")
  private BigDecimal avgSpeed;

  /**
   * 用户id
   */
  @Schema(name = "userId",title = "用户id")
  private String userId;

}
