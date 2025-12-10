package com.nip.dto.general;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-04-08 18:05
 * @Description:
 */
@Data
@Schema(title = "岗位训练-手键拍发")
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@RegisterForReflection
public class GeneralTickerPatTrainVO {
  @Schema(title = "id")
  private Integer id;

  /**
   * 训练名称
   */
  @Schema(title = "训练名称")
  private String name;

  private Integer isCable;
  /**
   * 类型 0 数码报 1 字码报 2 混合报
   */
  @Schema(title = "类型 0 数码报 1 字码报 2 混合报")
  private Integer type;

  @Schema(title = "0个人训练 1考核训练")
  private Integer trainType;

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
   * 是否平均0否 1是
   */
  @Schema(title = "是否平均0否 1是")
  private Integer isAverage;

  /**
   * 报底组数
   */
  @Schema(title = "报底组数")
  private Integer messageNumber;

  /**
   * 报底页数
   */
  @Schema(title = "报底页数")
  private Integer pageCount;
  /**
   * 开始时间
   */
  @Schema(title = "开始时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime startTime;

  /**
   * 结束时间
   */
  @Schema(title = "结束时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime endTime;


  @Schema(title = "训练时长")
  private Long validTime;


  /**
   * 0未开始，1，进行中，2未完成已暂停，3已完成
   */
  @Schema(title = "0未开始，1，进行中，2已完成")
  private Integer status;

  /**
   * 创建人ID
   */
  @Schema(title = "创建人ID")
  private String createUser;

  /**
   * 创建时间
   */
  @Schema(title = "创建时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime createTime;


  @Schema(title = "评分类型")
  private String ruleId;

  @Schema(title = "评分规则")
  private String ruleContent;


  @Schema(title = "参训人员")
  private List<GeneralTickerPatTrainUserInfoVO> userInfoList;


  @Schema(title = "报文内容")
  private List<String> contentValue;


}
