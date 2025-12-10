package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 09:47
 * @Description:
 */
@Data
public class EnteringExerciseAddParam {

  @Schema(title = "训练类型 0 异音同字 1同音异字 2连音词组 3军语词组 4文章练习 5口诀练习 6字根练习 7拆字练习 8词组练习 9 五笔连音词组")
  private Integer type;

  @Schema(title = "训练名称")
  private String name;
}
