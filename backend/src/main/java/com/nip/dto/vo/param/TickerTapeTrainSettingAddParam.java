package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 15:58
 * @Description:
 */
@Schema(title = "插入速率配置对象")
@Data
public class TickerTapeTrainSettingAddParam implements Serializable {

  @Schema(title = "id")
  private List<PostTickerTapeTrainSettingAddParamList> paramList;

}
