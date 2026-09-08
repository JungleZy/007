package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-03-15 14:33
 * @Description:
 */
@Data
@Schema(title = "电子键拍发内容")
public class PostTelegraphTelexPatTrainPageVO {

  @Schema(title = "用户拍发的内容")
  private List<PostTelegraphTelexPatTrainPageMessageVO> messageVO;

}
