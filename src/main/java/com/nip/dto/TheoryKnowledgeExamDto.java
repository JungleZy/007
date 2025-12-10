package com.nip.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/2/22 16:47
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TheoryKnowledgeExamDto {
  private String id;
  /**
   * 考核名称
   */
  private String title;
  private String startTime;
  /**
   * 时长
   */
  private String duration;
  /**
   * 创建人
   */
  private String createUserId;
  /**
   * 创建时间
   */
  private String createTime = new Date().getTime() + "";
  /**
   * 监考人
   */
  private String teacher;
  private TestPaperDto testPaper;
  private List<String> stuId;
}
