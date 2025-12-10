package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;

/**
 * @Author: wushilin
 * @Data: 2022-07-08 14:56
 * @Description:
 */
@Data
@Schema(name = "TheoryKnowledgeVO")
public class TheoryKnowledgeVO {

  private String id;
  /**
   *类型
   */
  private Integer type ;
  /**
   * 开启关闭状态
   */
  private Integer status ;
  private String cover = "/006/cover/base.jpg";
  /**
   * 标题
   */
  private String title;
  private String createUserId;
  private String createTime = new Date().getTime() + "";
  /**
   * 学分
   */
  private Double credit;

  /**
   * 难度id
   */
  private String difficultyId;

  /**
   * 难易名称
   */
  private String difficultyName;

  /**
   * 专业id
   */
  private String specialtyId;


  /**
   * 专业名称
   */
  private String specialtyName;
}
