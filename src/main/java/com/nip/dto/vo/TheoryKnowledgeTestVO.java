package com.nip.dto.vo;

import com.nip.entity.TheoryKnowledgeTestContentEntity;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-08-10 16:20
 * @Description:
 */
@Data
@Accessors(chain = true)
public class TheoryKnowledgeTestVO {

  private String id;
  private String title;
  private String knowledgeId;
  private String knowledgeSwfId;
  /**
   * 是否是启用版本，0否，1是
   */
  private Integer versions;
  /**
   * 创建时间，存在多套题，通过创建时间区分，最晚的为最新的
   */
  private String createTime = new Date().getTime() + "";
  /**
   * 创建人ID
   */
  private String createUserId;

  private List<TheoryKnowledgeTestContentEntity> knowledgeTestContents;


}
