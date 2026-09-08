package com.nip.dto;

import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 17:21
 * @Description:
 */
//@ApiModel("记录手机键拍发内容")
@Data
@RegisterForReflection
public class PostTelegramTrainContentValueDto {
  /**
   * 训练id
   */
  //@ApiModelProperty(value = "训练id",position = 1)
  private String trainId;

  /**
   * 报底页数
   */
  //@ApiModelProperty(value = "报底页数",position = 2)
  private Integer floorNumber;

  /**
   * 客户按下松开的时间
   */
  //@ApiModelProperty(value = "报文内容",position = 3)
  private List<PostTelegramTrainContentAddParam> messageBody;


  //@ApiModelProperty(value = "基准值",position = 4)
  private List<PostTelegramTrainFinishInfoDto> standard;

  //@ApiModelProperty(value = "完成信息",position = 5)
  private String finishInfo;


  //@ApiModelProperty(value = "有效时长",position = 2)
  private Integer validTime;

  //@ApiModelProperty(value = "速率",position = 3)
  private String speed;

  //@ApiModelProperty(value = "错误个数",position = 4)
  private Integer errorNumber;

  //@ApiModelProperty(value = "正确率",position = 5)
  private String accuracy;


}
