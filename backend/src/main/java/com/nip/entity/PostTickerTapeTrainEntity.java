package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收报训练
 *
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/4/6 13:40
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_post_ticker_tape_train") //对应的数据库表
@Cacheable(value = false)
@DynamicUpdate
@DynamicInsert
public class PostTickerTapeTrainEntity implements Serializable {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  private String name;

  /**
   * 是否是固定报 0 固定报 1 随机报
   */
  private Integer isCable;

  /**
   * 速率
   */
  private Integer rate;

  /**
   * 0 数码报 1 字码报 2 混合报
   */
  private Integer type;

  /**
   * 报文内容
   */
  private String codeMessageBody;

  /**
   * 报文数量
   */
  private Integer messageNumber;

  /**
   * 0：短码 1:长码
   */
  private Integer codeShort;

  /**
   * 创建时间
   */
  private LocalDateTime createTime = LocalDateTime.now();

  /**
   * 开始时间
   */
  private LocalDateTime startTime;

  /**
   * 结束时间
   */
  private LocalDateTime endTime;

  /**
   * 有效时长
   */
  private String validTime;

  /**
   * 分数
   */
  private String score;

  /**
   * 0:未开始 1:进行中 2：结束 3：已评分
   */
  private Integer status;

  /**
   * 创建人id
   */
  private String userId;

  /**
   * 存放提交的结果
   */
  private String result;

  /**
   * 多码
   */
  private Integer moreCode;

  /**
   * 少码
   */
  private Integer lackCode;

  /**
   * 错码
   */
  private Integer errorCode;

  /**
   * 多组
   */
  private Integer moreGroup;

  /**
   * 少组
   */
  private Integer lackGroup;

  /**
   * 图片
   */
  private String images;

  /**
   * 播报干扰
   */
  private String disturb;

  /**
   * 是否平均 0 否 1 是
   */
  private Integer isAvg;

  /**
   * 是否随机 0 否 1 是
   */
  private Integer isRandom;

  /**
   * 报文组数
   */
  private Integer totalNumber;
  /**
   * 比例
   */
  private Integer ratio;

  /**
   * 页开始标识，0 否 1 是
   */
  private Integer isStartSign = 1;

}
