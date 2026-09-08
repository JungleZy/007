package com.nip.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @Author: wushilin
 * @Data: 2023-08-22 10:38
 * @Description:
 */
@Data
@Schema(title = "组网运用vo对象")
@RegisterForReflection
public class GroupNetTrainListPageVO {

  @Schema(title = "id")
  private Integer id;

  /**
   * 训练名称
   */
  @Schema(title = "训练名称")
  private String trainName;

  /**
   * 设备类型
   */
  @Schema(title = "设备名称")
  private String deviceName;

  @Schema(title = "设备类型名称")
  private String deviceTypeName;


  @Schema(title = "得分")
  private BigDecimal score;


  @Schema(title = "创建时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
  private LocalDateTime createTime;
}
