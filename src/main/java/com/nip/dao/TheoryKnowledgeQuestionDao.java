package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TheoryKnowledgeQuestionEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/1/19 14:20
 */

@ApplicationScoped
public class TheoryKnowledgeQuestionDao extends BaseRepository<TheoryKnowledgeQuestionEntity, String> {

  public List<TheoryKnowledgeQuestionEntity> findAllByLevelIdIn(List<String> id) {
    return find("levelId in (?1)", id).list();
  }

  public List<TheoryKnowledgeQuestionEntity> findAllByLevelIdInAndTopicLike(List<String> id, String t) {
    return find("levelId in (?1) and topic like ?2", id, t).list();
  }

  public List<TheoryKnowledgeQuestionEntity> findAllByLevelIdInAndType(List<String> id, int type) {
    return find("levelId in (?1) and type = ?2", id, type).list();
  }

  public List<TheoryKnowledgeQuestionEntity> findAllByLevelIdInAndTypeAndTopicLike(List<String> id, int type, String t) {
    return find("levelId in (?1) and type =?2 and topic like ?3", id, type, t).list();
  }

  public List<TheoryKnowledgeQuestionEntity> findAllByLevelId(String levelId) {
    return find("levelId = ?1", levelId).list();
  }

}
