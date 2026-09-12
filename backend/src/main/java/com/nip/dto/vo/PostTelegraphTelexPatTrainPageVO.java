package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import com.nip.dto.CaptureInterval;

/**
 * @Author: wushilin
 * @Data: 2023-03-15 14:33
 * @Description:
 */
@Data
@Schema(title = "电子键拍发内容")
public class PostTelegraphTelexPatTrainPageVO {

  @Schema(title = "采集协议版本，0 为历史训练，1 为原始采集协议")
  private Integer protocolVersion;

  @Schema(title = "训练轮次")
  private Integer attempt;

  @Schema(title = "相对本成员采集起点的服务端已过毫秒数")
  private long serverElapsedMs;

  @Schema(title = "该页是否已有原始提交行")
  private boolean submitted;

  @Schema(title = "该页已确认的原始采集区间")
  private List<CaptureInterval> savedCaptureIntervals;

  @Schema(title = "用户拍发的内容")
  private List<PostTelegraphTelexPatTrainPageMessageVO> messageVO;

}
