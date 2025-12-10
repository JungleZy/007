package com.nip.dto;


import com.google.gson.annotations.Expose;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-05-12 17:02
 * @Description:
 */
//@ApiModel("训练统计内容对象")
@Data
@RegisterForReflection
public class PostTelegramTrainFinishInfoDto {

  //@ApiModelProperty(value = "点",position = 1)
  private Integer dot;

  //@ApiModelProperty(value = "划",position = 2)
  private Integer line;

  //@ApiModelProperty(value = "码间隔",position = 3)
  private Integer codeGap;

  //@ApiModelProperty(value = "词间隔",position = 4)
  private Integer wordGap;

  //@ApiModelProperty(value = "组间隔",position = 5)
  private Integer groupGap;

  //@ApiModelProperty(value = "偏移量",position = 6)
  private Integer offSize;
  //@ApiModelProperty(value = "拍发记录",position = 7)
  @Expose(serialize = false,deserialize = false)
  private List<PatLogs> patLogs;

  @Data
  //@ApiModel(value = "整个拍发内容")
  @RegisterForReflection
  public static class PatLogs{

    //@ApiModelProperty(value = "名称",position = 1)
    private String name;

    //@ApiModelProperty(value = "类型",position = 2)
    private Integer key;

    //@ApiModelProperty(value = "时长",position = 3)
    private Integer value;

  }
}
