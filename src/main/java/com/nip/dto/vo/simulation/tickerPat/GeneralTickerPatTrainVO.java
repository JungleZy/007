package com.nip.dto.vo.simulation.tickerPat;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-04-08 18:05
 * @Description:
 */
@Data
//@ApiModel("岗位训练-手键拍发")
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@RegisterForReflection
public class GeneralTickerPatTrainVO {
  //@ApiModelProperty(value = "id",position = 1)
  private Integer id;

  /**
   * 训练名称
   */
  //@ApiModelProperty(value = "训练名称",position = 2)
  private String name;

  /**
   * 类型 0 数码报 1 字码报 2 混合报
   */
  //@ApiModelProperty(value = "类型 0 数码报 1 字码报 2 混合报",position = 3)
  private Integer type;

  //@ApiModelProperty(value = "0个人训练 1考核训练")
  private Integer trainType;

  /**
   * 0 短码 1 长码
   */
  //@ApiModelProperty(value = "false 短码 true 长码",position = 4)
  private Boolean codeSort;

  /**
   * 是否随机 0否 1是
   */
  //@ApiModelProperty(value = "是否随机 false否 true是",position = 5)
  private Boolean isRandom;



  /**
   * 是否平均0否 1是
   */
  //@ApiModelProperty(value = "是否平均0否 1是",position = 5)
  private Integer isAverage;

  /**
   * 报底数
   */
  //@ApiModelProperty(value = "报底数",position = 6)
  private Integer messageNumber;

  /**
   * 开始时间
   */
  //@ApiModelProperty(value = "开始时间",position = 7)
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
  private LocalDateTime startTime;

  /**
   * 结束时间
   */
  //@ApiModelProperty(value = "结束时间",position = 8)
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
  private LocalDateTime endTime;



  //@ApiModelProperty(value = "训练时长",position = 9)
  private Long validTime;


  /**
   *  0未开始，1，进行中，2未完成已暂停，3已完成
   */
  //@ApiModelProperty(value = "0未开始，1，进行中，2已完成",position = 11)
  private Integer status;

  /**
   * 创建人ID
   */
  //@ApiModelProperty(value = "创建人ID",position = 12)
  private String createUser;

  /**
   * 创建时间
   */
  //@ApiModelProperty(value = "创建时间",position = 13)
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
  private LocalDateTime createTime;



  //@ApiModelProperty(value = "评分类型",position = 16)
  private String ruleId;

  //@ApiModelProperty(value = "评分规则",position = 17)
  private String ruleContent;


  //@ApiModelProperty(value = "参训人员")
  private List<GeneralTickerPatTrainUserInfoVO> userInfoList;


  //@ApiModelProperty(value = "报文内容")
  private List<String> contentValue;


}
