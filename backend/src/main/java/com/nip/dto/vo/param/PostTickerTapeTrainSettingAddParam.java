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
@Data
@Schema(title = "插入速率配置对象")
public class PostTickerTapeTrainSettingAddParam implements Serializable {

  @Schema(title = "id")
  private List<PostTickerTapeTrainSettingAddParamList> paramList;

  @Schema(title = "低速训练 点 标准时长")
  private Integer dotStandardTime;
}
