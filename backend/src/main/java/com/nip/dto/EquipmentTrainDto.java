package com.nip.dto;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-12-06 11:21
 * @Description:
 */
@Data
@Schema(title = "设备操作-训练dto")
public class EquipmentTrainDto {

  @Schema(title = "id")
  private String id;

  @Schema(title = "训练内容")
  private String content;

  @Schema(title = "设备id")
  private String deviceId;

  @Schema(title = "设备名称")
  private String deviceName;

  @Schema(title = "训练名称")
  private String trainName;

  @Schema(title = "训练状态 0 未开始 1已结束")
  private Integer trainStatus;

  @Schema(title = "训练类型 0综合（默认联络文件不区分工作模式）  1定频 2跳频 3自动控制 4自适应 ")
  private Integer trainType;
}
