package com.nip.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 11:15
 * @Description:
 */
@Data
@Schema(title = "电传拍发VO对象")
@RegisterForReflection
public class PostTelexPatTrainVO {

  @Schema(title = "id")
  private String id;

  /**
   * 训练名称
   */
  @Schema(title = "训练名称")
  private String name;

  /**
   * 是否是固定报 0 固定报 1 随机报
   */
  private Integer isCable;

  /**
   * 0 数字连贯1 字母连贯 2 组合连贯
   */
  @Schema(title = "0 数字连贯1 字母连贯 2 组合连贯")
  private Integer type;

  /**
   * 创建人ID
   */
  @Schema(title = "创建人ID")
  private String createUser;

  /**
   * 状态 0 未开始 1进行中 2 暂停 3完成
   */
  @Schema(title = "状态 0 未开始 1进行中 2 暂停 3完成")
  private Integer status;


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

  /**
   * 组数
   */
  @Schema(title = "组数")
  private Integer groupNumber;

  /**
   * 组数
   */
  @Schema(title = "页数")
  private Integer pageNumber;

  /**
   * 错误个数
   */
  @Schema(title = "错误个数")
  private Integer errorNumber;

  /**
   * 正确率
   */
  @Schema(title = "正确率")
  private String accuracy;

  /**
   * 速率
   */
  @Schema(title = "速率")
  private String speed;

  /**
   * 速率
   */
  private String speedLog;

  /**
   * 训练时长
   */
  @Schema(title = "训练时长")
  private Integer validTime;

  /**
   * 每页训练时长
   */
  private String validTimeLog;

  /**
   * 创建时间
   */
  @Schema(title = "创建时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime createTime = LocalDateTime.now();

  /**
   * 内容
   */
  @Schema(title = "内容")
  private String content;


  @Schema(title = "得分")
  private String score;

  @Schema(title = "更正次数")
  private Integer change;

  @Schema(title = "扣分详情")
  private String deductInfo;

  @Schema(title = "已存在的页")
  private List<PostTelexPatTrainPageVO> existPage;

  @Schema(title = "用户提交的拍发内容")
  private List<PostTelexPatTrainPageValueVO> codeAll;
  /**
   * 平均速率（20230606BBB新增）
   */
  @Schema(title = "平均速率")
  private String totalSpeed;

}
