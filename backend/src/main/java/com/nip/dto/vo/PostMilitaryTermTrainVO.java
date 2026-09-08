package com.nip.dto.vo;

import com.nip.entity.PostMilitaryTermTrainTestPaperEntity;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-06-24 15:17
 * @Description:
 */
@Data
@Schema(name = "PostMilitaryTermTrainVO")
public class PostMilitaryTermTrainVO {


  @Schema(name = "id")
  private String id;


  /**
   * 创建人
   */
  @Schema(name = "创建人")
  private String userId;

  /**
   * 军语密语类型[ "",""]
   */
  @Schema(name = "军语密语类型[ \"\",\"\"]")
  private List<String> types;

  /**
   * 训练名称
   */
  @Schema(name = "训练名称")
  private String name;

  /**
   * 状态
   */
  @Schema(name = "状态")
  private Integer status;

  /**
   * 军语数量
   */
  @Schema(name = "军语数量")
  private Integer totalNumber;

  /**
   * 正确数量
   */
  @Schema(name = "正确数量")
  private Integer correctNumber;

  /**
   * 错误数量
   */
  @Schema(name = "错误数量")
  private Integer errorNumber;

  /**
   * 正确率
   */
  @Schema(name = "正确率")
  private BigDecimal accuracy;


  /**
   * 训练时长(秒)
   */
  @Schema(name = "训练时长(秒)")
  private Integer duration;

  /**
   * 成绩
   */
  @Schema(name = "成绩")
  private BigDecimal score;

  @Schema(name = "试卷内容")
  private List<PostMilitaryTermTrainTestPaperEntity> testPaperList;


}
