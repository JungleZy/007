package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TheoryKnowledgeSwfEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * TelegramTrainDao
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-20 11:15
 */
@ApplicationScoped
public class TheoryKnowledgeSwfDao extends BaseRepository<TheoryKnowledgeSwfEntity, String> {

  public List<TheoryKnowledgeSwfEntity> findAllByKnowledgeIdOrderBySortAsc(String id) {
    return find("knowledgeId = ?1", Sort.by("sort").ascending(), id).list();
  }

  @Transactional
  public void deleteAllByKnowledgeId(String id) {
    delete("knowledgeId = ?1", id);
  }

  // 根据KnowlegdeId 查询课件
  public List<TheoryKnowledgeSwfEntity> findAllByKnowledgeIdIn(List<String> ids) {
    return find("knowledgeId in (?1)", ids).list();
  }
}
