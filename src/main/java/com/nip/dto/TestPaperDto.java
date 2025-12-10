package com.nip.dto;

import lombok.Data;

import java.util.List;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/24 15:36
 */
@Data
public class TestPaperDto {
  private String id;
  /**
   * 名称
   */
  private String name;
  /**
   * 原试卷id
   */
  private String testPaperId;
  /**
   * 节点id
   */
  private String levelId;
  /**
   * 总分
   */
  private Integer total;
  /**
   * 及格分比
   */
  private String passTheExamThan;
  /**
   * 及格分
   */
  private Integer passMark;
  /**
   * 创建时间
   */
  private String createTime ;
  private List<TestPaperQuestionDto> singleChoice;
  private List<TestPaperQuestionDto> multipleChoice;
  private List<TestPaperQuestionDto> judge;
  private List<TestPaperQuestionDto> completion;
  private List<TestPaperQuestionDto> shortAnswer;
}
