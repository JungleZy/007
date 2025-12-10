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
public class PostEnteringExerciseWordStockQueryParam {

  @Schema(title = "类型 0五笔文章 1五笔军语 2 英语文章 3拼音军语 4拼音文章")
  private Integer type;
}
