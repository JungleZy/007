package com.nip.dto.vo.param;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 16:29
 * @Description:
 */
@Data
@Schema(title = "新增训练计划对象")
@NoArgsConstructor
@AllArgsConstructor
public class PostTickerTapeTrainAddParam {

  @Schema(title = "id")
  private String id;

  @Schema(title = "训练名称")
  private String name;

  /**
   * 是否是固定报 0 固定报 1 随机报
   */
  @Schema(title = "是否是固定报 0 随机报 1 固定报")
  private Integer isCable = 0;

  /**
   * 是否是固定报 0 固定报 1 随机报
   */
  @Schema(title = "如果是固定报，该值为固定报编号，否则为空")
  private String cableId;

  @Schema(title = "如果是固定报，可以选择起始页，默认第一页")
  private Integer startPage;

  @Schema(title = "速率")
  private Integer rate;

  /**
   * 0:数码报 1 字码报 2 混合报
   */
  @Schema(title = "0 数码报 1 字码报 2 混合报")
  private Integer type;

  /**
   * 0：短码 1:长码
   */
  @Schema(title = "0：短码 1:长码")
  private Integer codeShort;

  @Schema(title = "报文内容")
  private List<Map> codeMessageBody;

  @Schema(title = "是否低速 0否 1是")
  private Integer isLowRate;

  @Schema(title = "播报干扰")
  private String disturb;

  @Schema(title = "是否平均报 0 否 1 是")
  private Integer isAvg;

  @Schema(title = "是否随机 0 否 1 是")
  private Integer isRandom;

  @Schema(title = "报文组数")
  private Integer totalNumber;

  @Schema(title = "比例")
  private Integer ratio;

  @Schema(title = "页开始标识，0 否 1 是")
  private Integer isStartSign;

}
