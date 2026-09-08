package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 14:10
 * @Description:
 */
@Data
@Schema(title = "查询对象")
public class EnteringExerciseWordStockQueryParam {

  @Schema(title = "类型0 异音同字 1  同音异字 2 连音词组 3 军语词组 4 文章/类型 0 拼音文章 1 拼音军语 2 五笔文章 3 五笔军语 4 英语文章")
  private Integer type;
}
