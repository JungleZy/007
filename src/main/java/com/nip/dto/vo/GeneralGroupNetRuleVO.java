package com.nip.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

@Data
@Schema(title = "综合组网运用评分规则VO")
public class GeneralGroupNetRuleVO {

  @Schema(title = "id")
  private Integer id;

  /**
   * 配置
   */
  @Schema(title = "配置")
  private String xyScore;

  /**
   * 配置
   */
  @Schema(title = "设备")
  private String device;

  /**
   * 代码
   */
  @Schema(title = "代码")
  private String code;

  @Schema(title = "创建时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime createTime;

}

