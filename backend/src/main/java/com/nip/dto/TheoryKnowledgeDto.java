package com.nip.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;

/**
 * TheoryKnowledgeDto
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-22 18:45
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "TheoryKnowledgeDto")
public class TheoryKnowledgeDto {
  private String id;
  private Integer type = 0;
  private Integer status = 0;
  private String cover = "/006/cover/base.jpg";
  private String title;
  private String createUserId;
  private String createUserName;
  private String createTime = new Date().getTime() + "";
  /**
   * 章节总数
   */
  private long swfs;
  /**
   * 已完成统计
   */
  private Integer doneCount;
  /**
   * 随堂测试总数
   */
  private Integer swfTestCount;


  /**
   * 难易分类
   */
  @Schema(name = "难易分类")
  private String difficultyName;

  /**
   * 专业分类
   */
  @Schema(name = "专业分类")
  private String specialtyName;
}
