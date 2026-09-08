package com.nip.dto.vo;

import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import lombok.Data;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-03-10 15:25
 * @Description:
 */
@Data
//@ApiModel(value = "追加保底")
public class PostTelegramTrainAddContentValueVO {

  //@ApiModelProperty(value = "训练id")
  private String trainId;

  //@ApiModelProperty(value = "追加内容")
  private List<List<PostTelegramTrainContentAddParam>> messageBody;;


}
