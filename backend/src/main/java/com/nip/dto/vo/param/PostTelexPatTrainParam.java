package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-05-06 14:36
 * @Description:
 */
@Data
@Schema(title = "岗位训练-电传拍发详情参数")
public class PostTelexPatTrainParam {
  private String id;
}
