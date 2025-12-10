package com.nip.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-12-06 11:23
 * @Description:
 */
@Data
@Schema(name = "装备操作-训练VO")
public class EquipmentTrainVO {
  @Schema(name = "id")
  private String id;

  @Schema(name = "用户id")
  private String userId;

  @Schema(name = "训练内容")
  private String content;

  @Schema(name = "训练时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  private LocalDateTime trainTime;

  @Schema(name = "设备id")
  private String deviceId;

  @Schema(name = "训练名称")
  private String trainName;

  @Schema(name = "训练状态 0 未开始 1已结束")
  private Integer trainStatus;

  @Schema(name = "训练类型 0综合（默认联络文件不区分工作模式）  1定频 2跳频 3自动控制 4自适应 ")
  private Integer trainType;

  @Schema(name = "设备名称")
  private String deviceName;
}
