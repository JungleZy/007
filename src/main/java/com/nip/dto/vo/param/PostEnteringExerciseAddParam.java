package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-05-17 10:37
 * @Description:
 */
@Data
@Schema(title = "添加打字训练")
public class PostEnteringExerciseAddParam {

  @Schema(title = "训练类型 0五笔文章 1五笔军语 2 英语文章 3拼音军语 4 拼音文章   ")
  private Integer type;

  @Schema(title = "训练名称")
  private String name;

  @Schema(title = "文章id")
  private Integer wordId;
}
