package com.nip.dto.vo.param;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-05-27 09:09
 * @Description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(title = "收报训练上传结果")
public class PostTickerTapeTrainUploadResultParam {

  @Schema(title = "训练id")
  private String id;

  @Schema(title = "训练结果")
  private List<List<String>> result;

  @Schema(title = "base64照片")
  private List<String> images;
}
