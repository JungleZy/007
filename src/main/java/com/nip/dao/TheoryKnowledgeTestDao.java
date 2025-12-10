package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TheoryKnowledgeTestEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * TheoryKnowledgeTestDao
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-01-03 14:31:19
 */
@ApplicationScoped
public class TheoryKnowledgeTestDao extends BaseRepository<TheoryKnowledgeTestEntity, String> {

  /**
   * 根据知识Id获取所属的测验-时间倒序
   *
   * @param knowledgeId
   * @return
   */
  public List<TheoryKnowledgeTestEntity> findAllByKnowledgeIdOrderByCreateTimeAsc(String knowledgeId) {
    return find("knowledgeId = ?1", Sort.by("createTime").ascending(), knowledgeId).list();
  }

  /**
   * 查询理论学习下所以章节随堂测试
   *
   * @param knowledgeId
   * @param ver
   * @return
   */
  public List<TheoryKnowledgeTestEntity> findAllByKnowledgeIdAndVersions(String knowledgeId, Integer ver) {
    return find("knowledgeId = ?1 and versions = ?2", knowledgeId, ver).list();
  }

  /**
   * 查询课件下使用的隋唐测验
   *
   * @param knowledgeId
   * @param ver
   * @return
   */
  public List<TheoryKnowledgeTestEntity> findAllByKnowledgeSwfIdAndVersions(String knowledgeId, Integer ver) {
    return find("knowledgeSwfId = ?1 and versions =?2", knowledgeId, ver).list();
  }

  /**
   * 根据教案ID获取所属的测验-时间倒序
   *
   * @param knowledgeSwfId
   * @return
   */
  public List<TheoryKnowledgeTestEntity> findAllByKnowledgeSwfIdOrderByCreateTimeAsc(String knowledgeSwfId) {
    return find("knowledgeSwfId = ?1", Sort.by("createTime").ascending(), knowledgeSwfId).list();
  }

  /**
   * 获取到当前教案被启用的测验
   *
   * @param knowledgeSwfId
   * @param versions
   * @return
   */
  public TheoryKnowledgeTestEntity findFirstByKnowledgeSwfIdAndVersions(String knowledgeSwfId, Integer versions) {
    return find("knowledgeSwfId = ?1 and versions=?2", knowledgeSwfId, versions).list()
        .stream()
        .findFirst()
        .orElse(new TheoryKnowledgeTestEntity());
  }

  //@Modifying(clearAutomatically = true)
  //@Query("update TheoryKnowledgeTestEntity t set t.versions=0 where t.knowledgeSwfId=?1")
  @Transactional
  public void updateVersions2CloseByKnowledgeSwfId(String knowledgeSwfId) {
    update("versions = 0 where knowledgeSwfId = ?1", knowledgeSwfId);
  }

  @Transactional
  public void deleteAllByKnowledgeIdAndKnowledgeSwfId(String id, String swfId) {
    delete("knowledgeId = ?1 and knowledgeSwfId = ?2 ", id, swfId);
  }
}
