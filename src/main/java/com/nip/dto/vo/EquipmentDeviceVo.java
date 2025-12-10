package com.nip.dto.vo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2022-10-31 11:53
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(name = "装备操作设备表")
@RegisterForReflection
public class EquipmentDeviceVo {

  @Schema(name = "id")
  private String id;

  @Schema(name = "设备ID" )
  private String deviceId;

  @Schema(name = "设备名称" )
  private String name;

  /**
   * 图片地址
   */
  @Schema(name = "图片地址")
  private String image;

  /**
   * 表格字段
   */
  @Schema(name = "表格字段")
  private String option;

  /**
   * 是否启用
   */
  @Schema(name = "是否启用")
  private int isEnable;

  /**
   * 创建时间
   */
  @Schema(name = "创建时间")
  private LocalDateTime createTime;

}
