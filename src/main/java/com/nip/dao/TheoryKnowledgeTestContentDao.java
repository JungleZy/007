package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TheoryKnowledgeTestContentEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * TheoryKnowledgeTestContentDao
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-01-03 14:31:56
 */

@ApplicationScoped
public class TheoryKnowledgeTestContentDao extends BaseRepository<TheoryKnowledgeTestContentEntity, String> {
  /**
   * 根据知识ID获取所有的测验题
   *
   * @param knowledgeSwfId
   * @return
   */
  public List<TheoryKnowledgeTestContentEntity> findAllByKnowledgeSwfId(String knowledgeSwfId) {
    return find("knowledgeSwfId =?1", knowledgeSwfId).list();
  }

  /**
   * 根据教案ID获取所有的测验题
   *
   * @param knowledgeId
   * @return
   */
  public List<TheoryKnowledgeTestContentEntity> findAllByKnowledgeId(String knowledgeId) {
    return find("knowledgeId = ?1", knowledgeId).list();
  }

  /**
   * 根据测验ID获取测验题
   *
   * @param knowledgeTestId
   * @return
   */
  public List<TheoryKnowledgeTestContentEntity> findAllByKnowledgeTestId(String knowledgeTestId) {
    return find("knowledgeTestId =?1", Sort.by("createTime").ascending(), knowledgeTestId).list();
  }

  /**
   * 根据创建人ID获取所有的测验题
   *
   * @param createUserId
   * @return
   */
  public List<TheoryKnowledgeTestContentEntity> findAllByCreateUserId(String createUserId) {
    return find("createUserId =?1", createUserId).list();
  }

  @Transactional
  public void deleteAllByKnowledgeId(String id) {
    delete("knowledgeId = ?1", id);
  }

  public void deleteAllByKnowledgeTestId(String knowledgeTestId) {
    delete("knowledgeTestId = ?1", knowledgeTestId);
  }

  @Transactional
  public void deleteByKnowledgeIdAndCreateUserIdAndKnowledgeSwfIdAndKnowledgeTestId(String knowledgeId, String userId, String knowledgeSwfId, String knowledgeTestId) {
    delete("knowledgeId = ?1 and createUserId = ?2 and knowledgeSwfId = ?3 and knowledgeTestId=?4", knowledgeId, userId, knowledgeSwfId, knowledgeTestId);
  }

}
