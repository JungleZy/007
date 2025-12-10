package com.nip.dto.vo.param.simulation.tickerPat;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-05-05 17:33
 * @Description:
 */
@Schema(title = "查询训练详情参数")
@Data
@RegisterForReflection
public class GeneralTickerPatTrainQueryParam {
  private Integer id;
  private String uid;
}
