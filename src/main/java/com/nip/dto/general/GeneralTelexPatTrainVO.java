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

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(title = "电子键组训")
@RegisterForReflection
public class GeneralTelexPatTrainVO {

  @Schema(title = "id")
  private String id;

  @Schema(title = "训练名称")
  private String title;

  @Schema(title = "是否固定报0 不 1是")
  private Integer isCable;

  @Schema(title = "报底数")
  private Integer totalNumber;

  @Schema(title = "报文页数")
  private Integer pageCount;

  @Schema(title = "创建时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime createTime;

  @Schema(title = "开始时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime startTime;

  @Schema(title = "结束时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime endTime;

  @Schema(title = "训练时长")
  private Long validTime;

  @Schema(title = "0未开始，1，进行中，2已完成")
  private Integer status;

  @Schema(title = "创建人ID")
  private String createUser;

  @Schema(title = "评分规则ID")
  private String ruleId;

  @Schema(title = "评分规则")
  private String ruleContent;

  @Schema(title = "参训人员")
  private List<GeneralTelexPatUserInfoVO> userInfoList;

  @Schema(title = "报文内容")
  private List<String> contentValue;
  @Schema(title = "类型 0 数字连贯 1 字母连贯 2 组合连贯")
  private Integer type;
  @Schema(title = "训练类型 0 数据报 1 电传训练")
  private Integer trainType;
  @Schema(title = "报底类型，0 挨指报 1 对手报 2 随机报")
  private Integer patType;

}
