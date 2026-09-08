package com.nip.dto.vo;

import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-05-11 17:22
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
//@ApiModel(value = "手键拍发报文VO")
public class PostTelegramTrainContentVO {

  //@ApiModelProperty(value = "报底内容",position = 1)
  private String messageBody;

  //@ApiModelProperty(value = "训练未开始",position = 2)
  private List<PostTelegramTrainContentAddParam> messageKey;

  //@ApiModelProperty(value = "拍发记录",position = 3)
  private String finishInfo;

  //@ApiModelProperty(value = "点划标准值",position = 4)
  private String standard;

  /**
   * 解析后的报文格式内容
   */
    //@ApiModelProperty(value = "解析后的报文格式内容",position = 5)
  private String resolver;
}
