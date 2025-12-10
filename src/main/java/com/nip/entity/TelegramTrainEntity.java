package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * TelegramTrainEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-20 11:02
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_telegram_train") //对应的数据库表
@Cacheable(value = false)
public class TelegramTrainEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 该类表示一个报文的元数据
   * 包含了报文的各种属性，如标题、时间、状态、类型、统计信息等
   */
  private String title; // 报文标题
  private String startTime; // 开始时间
  private String pauseTime; // 暂停时间
  private String endTime; // 结束时间
  private String sustainTime = "0"; // 持续时间，默认为0
  private Integer status = 0; // 状态，默认为0
  private Integer type=0; // 报文类型，默认为0.数码报，1.字码报，2.混合报,3.点划报
  private Integer totalKnockNumber = 0; // 敲击的总数量
  private Integer totalNumber = 0; // 全报文数量
  private Integer errorNumber = 0; // 错误数量
  private String accuracy = "0"; // 正确率，默认为0
  private String speed = "0"; // 速率，默认为0
  /**
   * 以下是一些关于报文速率的配置参数
   * 包括点、线、码、组间隔的最小和最大时间
   */
  private Integer rateDotMinMs = 1;         // 点间隔最小  标准比例，点持1、划持3、码隔3、词隔5，例hi ai:....   ..     ._   ..
  private Integer rateDotMaxMs = 80;        // 点间隔最大
  private Integer rateLineMinMs = 81;       // 线间隔最小
  private Integer rateLineMaxMs = 240;      // 线间隔最大
  private Integer rateIntervalMinMs = 81;   // 码的最小间隔时间
  private Integer rateIntervalMaxMs = 240;  // 码的最大间隔时间
  private Integer bigIntervalMinMs = 241;   // 组的最小间隔时间
  private Integer bigIntervalMaxMs = 400;   // 组的最大间隔时间
  private String createUserId; // 创建用户的ID
  private String createTime = new Date().getTime() + ""; // 创建时间，默认为当前时间戳
  private String nowFloorId; // 当前正在编辑的Id
}
