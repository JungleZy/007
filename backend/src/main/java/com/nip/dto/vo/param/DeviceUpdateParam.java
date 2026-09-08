package com.nip.dto.vo.param;


import com.nip.dto.vo.DeviceDescriptionVO;
import lombok.Data;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-07-07 09:37
 * @Description:
 */
@Data
//@ApiModel(value = "设备信息VO")
public class DeviceUpdateParam {

  private Integer id;

  /**
   * 设备名称
   */
  //@ApiModelProperty("设备名称")
  private String deviceName;

  /**
   * 设备编号
   */
 //@ApiModelProperty("设备编号")
  private String deviceNumber;

  /**
   * 设备图片
   */
  //@ApiModelProperty("设备图片")
  private String deviceImg;

  /**
   * 设备分类id
   */
  //@ApiModelProperty("设备分类id")
  private Integer deviceTypeId;


  //@ApiModelProperty(value = "描述信息")
  //@NotNull(message = "不能是空")
  private List<DeviceDescriptionVO> descriptions;
}
