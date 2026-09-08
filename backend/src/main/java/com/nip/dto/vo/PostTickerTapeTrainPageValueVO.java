package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-03-21 13:50
 * @Description:
 */
@Data
@Schema(title = "收报训练vo")
public class PostTickerTapeTrainPageValueVO {

  @Schema(title = "报底")
  List<PostTickerTapeTrainPageVO> messageBody;

  @Schema(title = "填报内容")
  List<String> value;



}
