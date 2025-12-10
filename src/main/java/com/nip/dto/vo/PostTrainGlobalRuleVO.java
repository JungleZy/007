package com.nip.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2023-03-14 09:22
 * @Description:
 */
@Data
//@ApiModel(value = "岗位报话、汉字录入评分")
public class PostTrainGlobalRuleVO {

//  @ApiModelProperty(value = "id",position = 1)
  private Integer id;

  /**
   * 类型
   */
//  @ApiModelProperty(value = "训练类型 0 勤务用语 1军语密语 2拼音训练 3英语训练 4五笔训练",position = 2)
  private Integer type;

//  @ApiModelProperty(value = "级别",position = 3)
  private String level;

  /**
   * 正确率
   */
//  @ApiModelProperty(value = "正确率",position = 4)
  private String accuracy;


  /**
   * 描述
   */
//  @ApiModelProperty(value = "描述",position = 5)
  private String description;

  /**
   * 创建时间
   */
//  @ApiModelProperty(value = "创建时间",position = 6)
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime createTime;
}
