package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TheoryKnowledgeQuestionLevelEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-07-27 12:06
 * @Description:
 */
@ApplicationScoped
public class TheoryKnowledgeQuestionLevelDao extends BaseRepository<TheoryKnowledgeQuestionLevelEntity, String> {

  public List<TheoryKnowledgeQuestionLevelEntity> findAllByParentId(String id) {
    return find("parentId = ?1", id).list();
  }

}
