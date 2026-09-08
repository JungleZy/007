package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.io.Serializable;

/**
 * @Author: wushilin
 * @Data: 2023-04-10 15:13
 * @Description:
 */
@Data
@RegisterForReflection
public class GeneralTickerPatTrainUpdateDto implements Serializable {

  private Integer trainId;

  private Integer status;
}
