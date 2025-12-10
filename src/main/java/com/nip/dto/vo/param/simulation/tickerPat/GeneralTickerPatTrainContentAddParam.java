package com.nip.dto.vo.param.simulation.tickerPat;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2023-04-08 17:48
 * @Description:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(title ="训练报文内容")
@RegisterForReflection
public class GeneralTickerPatTrainContentAddParam {
  @Schema(title = "id")
  private String id;

  @Schema(title = "key")
  private String moresKey;

  @Schema(title = "value")
  private String moresValue;

  @Schema(title = "value")
  private String moresTime;

  @Schema(title = "拍发的key(数据回显使用字段)")
  private String patKeys;

  @Schema(title = "patLogs")
  private String patLogs = "[]";
}
