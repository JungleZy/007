package com.nip.dto.vo;

import lombok.Data;
import lombok.experimental.Accessors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@Accessors(chain = true)
@Schema(name = "训练统计VO对象")
public class UserTrainDurationStatVO {
  @Schema(name = "手键训练时长（秒）")
  private Integer handKeyDuration;
  @Schema(name = "电子键训练时长（秒）")
  private Integer electronicKeyDuration;
  @Schema(name = "收报训练时长（秒）")
  private Integer receiveDuration;
  @Schema(name = "数据报训练时长（秒）")
  private Integer datagramDuration;
  @Schema(name = "电传拍发训练时长（秒）")
  private Integer telexDuration;
  @Schema(name = "拼音训练时长（秒）")
  private Integer pinyinDuration;
  @Schema(name = "五笔训练时长（秒）")
  private Integer wubiDuration;
  @Schema(name = "英语训练时长（秒）")
  private Integer englishDuration;
}
