package com.nip.dto.vo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2023-03-31 14:03
 * @Description: 手键拍发统计数据
 */
@Data
//@ApiModel(value = "手键拍发统计数据")
@RegisterForReflection
public class PostTelegramTrainStatisticsVO {

  //@ApiModelProperty(value = "点细个数")
  private Integer dotMinNumber = 0 ;

  //@ApiModelProperty(value = "点粗个数")
  private Integer dotMaxNumber = 0;

  //@ApiModelProperty(value = "点完美个数")
  private Integer dotPerfectNumber = 0;

  //@ApiModelProperty(value = "划短个数")
  private Integer lineMinNumber = 0 ;

  //@ApiModelProperty(value = "划长个数")
  private Integer lineMaxNumber = 0;

  //@ApiModelProperty(value = "划完美")
  private Integer linePerfectNumber = 0;

  //@ApiModelProperty(value = "码间隔过小")
  private Integer codeMinNumber = 0 ;

  //@ApiModelProperty(value = "码间隔过大")
  private Integer codeMaxNumber = 0;

  //@ApiModelProperty(value = "码间隔完美")
  private Integer codePerfectNumber = 0;

  //@ApiModelProperty(value = "词间隔过小")
  private Integer wordMinNumber = 0;

  //@ApiModelProperty(value = "词间隔过大")
  private Integer wordMaxNumber = 0;

  //@ApiModelProperty(value = "词间隔完美")
  private Integer wordPerfectNumber = 0;

  //@ApiModelProperty(value = "组间隔过小")
  private Integer groupMinNumber = 0;

  //@ApiModelProperty(value = "组间隔过大")
  private Integer groupMaxNumber = 0;

  //@ApiModelProperty(value = "组间隔完美")
  private Integer groupPerfectNumber = 0;

  //@ApiModelProperty(value = "点时长平均")
  private Integer dotAvg = 0;

  //@ApiModelProperty(value = "划时长平均")
  private Integer lineAvg = 0;

  //@ApiModelProperty(value = "码间隔时长平均")
  private Integer codeAvg = 0;

  //@ApiModelProperty(value = "词间隔时长平均")
  private Integer wordAvg = 0;

  //@ApiModelProperty(value = "组间隔时长平均")
  private Integer groupAvg = 0;


}
