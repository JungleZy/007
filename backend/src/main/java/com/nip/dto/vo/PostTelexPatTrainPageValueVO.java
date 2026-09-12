package com.nip.dto.vo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nip.common.utils.StrictIntegerDeserializer;
import com.nip.dto.CaptureInterval;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-03-17 09:32
 * @Description:
 */
@Data
@Schema(title  = "每页拍发结果")
public class PostTelexPatTrainPageValueVO {
  /**
   * 训练id
   */
  @Schema(title = "id")
  private String trainId;


  /**
   * 页码
   */
  @Schema(title = "页码")
  private Integer pageNumber;

  /**
   * 拍发内容
   */
  @Schema(title = "拍发内容")
  private String patValue;

  @Schema(title = "训练轮次")
  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  private Integer attempt;

  @Schema(title = "相对本轮开始时间的有效采集区间（毫秒）")
  private List<CaptureInterval> captureIntervals;
}
