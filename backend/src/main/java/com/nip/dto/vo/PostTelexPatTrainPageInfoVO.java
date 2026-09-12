package com.nip.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nip.dto.CaptureInterval;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-03-18 16:35
 * @Description:
 */
@Data
@Schema(title = "分页内容详情")
public class PostTelexPatTrainPageInfoVO {

  @Schema(title = "报底")
  private List<PostTelexPatTrainPageVO> pageVo;

  @Schema(title = "用户拍发内容")
  private String codeAll;

  @Schema(title = "该页是否已提交，包括空白页")
  private boolean submitted;

  @Schema(title = "该页已确认的有效用时（秒）")
  private Integer validTime;

  @Schema(title = "该页已确认的速率")
  private String speed;

  @Schema(title = "训练轮次")
  private Integer attempt;

  @Schema(title = "相对本轮开始时间的有效采集区间（毫秒）")
  private List<CaptureInterval> captureIntervals;

  @Schema(title = "服务端接收时间")
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
  private LocalDateTime receivedAt;
}
