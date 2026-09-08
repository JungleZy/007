package com.nip.dto.vo;

import lombok.Data;

/**
 * @Author: wushilin
 * @Data: 2023-07-07 09:41
 * @Description:
 */
@Data
//@ApiModel(value = "设备描述vo")
public class DeviceDescriptionVO {

 // @ApiModelProperty(value = "id")
  private Integer id;

  //@ApiModelProperty(value = "标题")
  private String title;

  //@ApiModelProperty(value = "内容")
  private String content;
}
