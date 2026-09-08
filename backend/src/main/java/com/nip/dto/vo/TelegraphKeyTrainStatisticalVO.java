package com.nip.dto.vo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
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
@Accessors(chain = true)
@Schema(title = "电子键拍发统计VO")
public class TelegraphKeyTrainStatisticalVO {

  @Schema(title = "id")
  private String id;
  /**
   * 类型（0单子统计 1综合统计
   */
  @Schema(title = "0基础训练 1单字训练 2综合练习")
  private Integer type;

  /**
   * 训练次数
   */
  @Schema(title = "训练次数")
  private Integer totalCount;

  /**
   * 训练总时长
   */
  @Schema(title = "训练总时长")
  private String totalTime;

  /**
   * 平均速率
   */
  @Schema(title = "平均速率")
  private BigDecimal avgSpeed;

  /**
   * 用户id
   */
  @Schema(title = "用户id")
  private String userId;

}
