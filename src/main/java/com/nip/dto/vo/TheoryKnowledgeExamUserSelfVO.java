package com.nip.dto.vo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/2/22 15:38
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "自测训练VO")
@RegisterForReflection
public class TheoryKnowledgeExamUserSelfVO {


  @Schema(name = "title",title = "训练title")
  private String title;

  /**
   * 开始时间
   */
  @Schema(name = "start_time",title = "开始时间")
  private String start_time;

  @Schema(name = "score",title = "得分")
  private Integer score;

  @Schema(name = "content",title = "提交的答案")
  private String content;

  @Schema(name = "examId",title = "测试id")
  private String examId;



}
