package com.nip.dto;

import lombok.Data;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/24 15:38
 */
@Data
public class TestPaperQuestionDto {
  private String id;
  /**
   * 试卷id
   */
  private String testpaperId;
  /**
   * 试题库id
   */
  private String parentId;
  /**
   * 分值
   */
  private Integer score;
  /**
   * 排序
   */
  private Integer sort;
  /**
   * 测验类型，1、单选题，2、多选题，3、判断题，4、填空题，5、简答题
   */
  private Integer type;
  /**
   * 题目
   */
  private String topic;
  /**
   * 选项
   */
  private String options;
  /**
   * 答案
   */
  private String answer;
  /**
   * 解析
   */
  private String analysis;
}
