package com.nip.dto;

import lombok.Data;

import java.util.Date;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/29 9:58
 */
@Data
public class TelexPatTrainDto {
  private String id;
  /**
   * 类型（0数字连贯，1字母连贯，2组合连贯
   */
  private Integer type;
  /**
   * 报文组数
   */
  private Integer numbs;
  private String title;
  private Integer status = 0;
  /**
   * 时长
   */
  private String duration;
  /**
   * // 全报文数量
   */
  private Integer totalNumber = 0;
  /**
   * // 错误数量
   */
  private Integer errorNumber = 0;
  /**
   *正确率
   */
  private Integer accuracy = 0;
  /**
     *速率
   */
  private String speed ;
  private String createUserId;
  private String createTime = new Date().getTime() + "";
  private String content;
}
