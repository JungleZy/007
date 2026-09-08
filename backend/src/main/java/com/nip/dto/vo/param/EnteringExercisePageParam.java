package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 14:33
 * @Description:
 */
@Data
@Schema(title ="查询所有训练")
public class EnteringExercisePageParam {

  @Schema(title = "类型 0 拼音 1五笔")
  private Integer type;
}
