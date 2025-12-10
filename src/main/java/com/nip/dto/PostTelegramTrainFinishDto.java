package com.nip.dto;


import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 09:59
 * @Description:
 */
//@ApiModel("岗位训练-手键拍发完成参数")
@Data
@RegisterForReflection
public class PostTelegramTrainFinishDto {

  //@ApiModelProperty(value = "id",position = 1)
  private String id;

  //@ApiModelProperty(value = "完成内容",position = 2)
  private List<PostTelegramTrainFinishInfoDto> finishInfo;

  private Integer validTime;


}
