package com.nip.dto.vo.param;


import lombok.Data;


/**
 * @Author: wushilin
 * @Data: 2023-07-07 09:41
 * @Description:
 */
@Data
//@ApiModel(value = "设备描述vo")
public class DeviceDescriptionAddParam {


  //@ApiModelProperty(value = "设备id")
  //@NotNull(message = "设备id不能是空")
  private Integer deviceId;

  //@ApiModelProperty(value = "标题")
  //@NotBlank(message = "标题不能是空")
  private String title;

  //@ApiModelProperty(value = "内容")
  //@NotBlank(message = "内容不能是空")
  private String content;
}
