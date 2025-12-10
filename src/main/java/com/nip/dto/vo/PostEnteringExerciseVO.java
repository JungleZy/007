package com.nip.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 09:58
 * @Description:
 */
@Data
@Schema(name = "打字训练对象")
public class PostEnteringExerciseVO {

  @Schema(name = "id")
  private String id;

  /**
   * 类型 0 异音同字 1同音异字 2连音词组 3军语词组 4文章练习
   */
  @Schema(name = "类型 0五笔文章 1五笔军语 2 英语文章 3拼音军语 4拼音文章  ")
  private Integer type;


  /**
   * 开始时间
   */
  @Schema(name = "开始时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime startTime;

  /**
   * 结束时间
   */
  @Schema(name = "结束时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime endTime;

  /**
   * 创建时间
   */
  @Schema(name = "创建时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime createTime;


  /**
   * 正确率
   */
  @Schema(name = "正确率")
  private Double accuracy;

  /**
   * 速度
   */
  @Schema(name = "速度")
  private Integer speed;

  /**
   * 文本内容
   */
  @Schema(name = "文本内容")
  private String content;

  /**
   * 训练时长
   */
  @Schema(name = "训练时长")
  private Integer duration;

  /**
   * 状态 0未开始 1进行中 2结束
   */
  @Schema(name = "状态 0未开始 1进行中 2结束")
  private Integer status;

  @Schema(name = "训练名称")
  private String name;

  @Schema(name = "正确个数")
  private Integer correctNum;

  @Schema(name = "错误个数")
  private Integer errorNum;
}
