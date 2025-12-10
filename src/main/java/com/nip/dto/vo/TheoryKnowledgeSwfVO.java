package com.nip.dto.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-08-10 16:14
 * @Description:
 */
@Data
@Accessors(chain = true)
public class TheoryKnowledgeSwfVO {
  private String id;
  private String knowledgeId;
  private String cover = "/006/cover/base.jpg";
  /**
   * 标题
   */
  private String title;
  /**
   * 内容
   */
  private String content;
  /**
   * 创建人id
   */
  private String createUserId;
  private String createTime = System.currentTimeMillis()+"";
  /**
   * 章节排序
   */
  private Integer sort;
  private Integer score = null;
  private Boolean haveTest;
  private double record = 0;

  //多个测验
  private List<TheoryKnowledgeTestVO> test;
}
