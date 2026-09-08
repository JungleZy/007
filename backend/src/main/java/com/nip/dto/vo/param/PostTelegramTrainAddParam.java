package com.nip.dto.vo.param;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-05-05 11:49
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(title = "添加岗位训练-手键拍发")
public class PostTelegramTrainAddParam {

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
  /**
   * 类型 0 数码报 1 字码报 2 混合报
   */
  @Schema(title = "类型 0 数码报 1 字码报 2 混合报")
  private Integer type;

  /**
   * 0 短码 1 长码
   */
  @Schema(title = "false 短码 true 长码")
  private Boolean codeSort;

  /**
   * 是否随机 0否 1是
   */
  @Schema(title = "是否随机 false否 true是")
  private Boolean isRandom;

  /**
   * 报底数
   */
  @Schema(title = "报底数")
  private Integer messageNumber;


  @Schema(title = "评分规则Id")
  private String ruleId;

  @Schema(title = "报文内容")
  private List<List<PostTelegramTrainContentAddParam>> messageBody;


  @Schema(title = "平均报")
  private Boolean isAverage;


}
