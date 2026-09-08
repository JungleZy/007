package com.nip.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nip.dto.PostTelegramTrainFinishInfoDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-05-05 11:39
 * @Description:
 */
@Data
//@ApiModel("岗位训练-手键拍发")
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class PostTelegramTrainVO {

  //@ApiModelProperty(value = "id",position = 1)
  private String id;

  /**
   * 训练名称
   */
  //@ApiModelProperty(value = "训练名称",position = 2)
  private String name;

  private Integer isCable;

  /**
   * 类型 0 数码报 1 字码报 2 混合报
   */
  //@ApiModelProperty(value = "类型 0 数码报 1 字码报 2 混合报",position = 3)
  private Integer type;

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
   * 报底数
   */
  //@ApiModelProperty(value = "报底数",position = 6)
  private Integer messageNumber;
  /**
   * 报底数
   */
  //@ApiModelProperty(value = "报底数",position = 6)
  private Integer messageGroup;

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

  /**
   * 有效时长
   */
  //@ApiModelProperty(value = "有效时长",position = 9)
  private Long validTime;

  /**
   * 速率
   */
  //@ApiModelProperty(value = "速率",position = 10)
  private String speed;

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


  /**
   * 错误个数
   */
  //@ApiModelProperty(value = "错误个数",position = 14)
  private Integer errorNumber;

  /**
   * 正确率
   */
  //@ApiModelProperty(value = "正确率",position = 15)
  private String accuracy;


  //@ApiModelProperty(value = "当前保底ID",position = 16)
  private Integer floorNow;

  //@ApiModelProperty(value = "各页速率记录",position = 17)
  private String speedLog;

  //@ApiModelProperty(value = "完成内容，用户统计使用",position = 18)
  private List<PostTelegramTrainFinishInfoDto> finishInfo;

  //@ApiModelProperty(value = "完成内容",position = 19)
  private List<String> messageBody;

  //@ApiModelProperty(value = "分数",position = 15)
  private String score;

  //@ApiModelProperty(value = "评分类型",position = 16)
  private String ruleId;

  //@ApiModelProperty(value = "漏拍个数",position = 17)
  private Integer lack;


  //@ApiModelProperty(value = "规则内容",position = 18)
  private String ruleContent;

  //@ApiModelProperty(value = "扣分详情",position = 19)
  private String deductInfo;


  //@ApiModelProperty(value = "已存在保底" ,position = 20)
  private List<Integer> existNumber;

  //@ApiModelProperty(value = "统计信息 点、划、间隔",position = 21)
  private String statisticInfo;

  //@ApiModelProperty(value = "基准值",position = 22)
  private List<String> standards;


  //@ApiModelProperty(value = "解析后的报文内容",position = 30)
  private List<String> resolver;

}
