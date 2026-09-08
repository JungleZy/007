package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-09-14 09:21
 * @Description:
 */
@Data
@Schema(name = "理论学习文档内容")
public class TheoryKnowledgeDocumentContentVO {
  @Schema(name = "type",title = "1 图片地址 2 word文档内容")
  private Integer type;

  @Schema(name = "wordContent",title = "文档内容")
  private String wordContent;

  @Schema(name = "imgUrls",title = "ppt图片地址")
  private List<String> imgUrls;
}
