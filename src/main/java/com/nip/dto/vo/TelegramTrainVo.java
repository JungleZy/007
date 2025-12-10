package com.nip.dto.vo;

import lombok.Data;

import java.util.Date;

/**
 * TelegramTrainEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-20 11:02
 */
@Data

public class TelegramTrainVo {

  private String id;
  private String title;
  private String startTime;
  private String pauseTime;
  private String endTime;
  private String sustainTime = "0";
  private Integer status = 0;
  private Integer type=0; // 0.数码报，1.字码报，2.混合报,3.点划报
  private Integer totalKnockNumber = 0; // 全报文数量
  private Integer totalNumber = 0; // 全报文数量
  private Integer errorNumber = 0; // 错误数量
  private String accuracy = "0"; // 正确率
  private String speed = "0"; // 速率
  private Integer rateDotMinMs = 1;         // 点间隔最小  标准比例，点持1、划持3、码隔3、词隔5，例hi ai:....   ..     ._   ..
  private Integer rateDotMaxMs = 80;        // 点间隔最大
  private Integer rateLineMinMs = 81;       // 线间隔最小
  private Integer rateLineMaxMs = 240;      // 线间隔最大
  private Integer rateIntervalMinMs = 81;   // 码的最小间隔时间
  private Integer rateIntervalMaxMs = 240;  // 码的最大间隔时间
  private Integer bigIntervalMinMs = 241;   // 组的最小间隔时间
  private Integer bigIntervalMaxMs = 400;   // 组的最大间隔时间
  private String createUserId;
  private String createTime = new Date().getTime() + "";
  private String nowFloorId; // 当前正在编辑的Id
  // 0 不随机 1随机
  private Integer isRandom;
  // 0 短码  1长码
  private Integer numberType;
}
