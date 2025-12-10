package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 14:04
 * @Description:
 */
@Data
@Schema(name = "汉字训练字库DTO")
@RegisterForReflection
public class PostEnteringExerciseWordStockDto {

  @Schema(name = "id")
  private Integer id;


  @Schema(name = "名称")
  private String name;

  /**
   * 0 异音同字 1  同音异字 2 连音词组 3 军语词组 4 文章
   */
  @Schema(name = "类型 0五笔文章 1五笔军语 2 英语文章 3拼音军语 4拼音文章")
  private Integer type;

  /**
   * 内容
   */
  @Schema(name = "内容")
  private String content;

  @Schema(name = "组数/字数")
  private Integer wordSize;

}
