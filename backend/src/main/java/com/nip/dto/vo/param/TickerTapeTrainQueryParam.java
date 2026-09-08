package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

/**
 * @Author: wushilin
 * @Data: 2022-04-11 10:41
 * @Description:
 */
@Schema(title = "训练查询对象")
@Data
public class TickerTapeTrainQueryParam implements Serializable {

  private String id;
}
