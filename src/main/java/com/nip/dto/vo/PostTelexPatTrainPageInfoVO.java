package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-03-18 16:35
 * @Description:
 */
@Data
@Schema(title = "分页内容详情")
public class PostTelexPatTrainPageInfoVO {

  @Schema(title = "报底")
  private List<PostTelexPatTrainPageVO> pageVo;

  @Schema(title = "用户拍发内容")
  private String codeAll;
}
