package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.sql.TheoryKnowledgeTestUserCountSwfNumDto;
import com.nip.entity.TheoryKnowledgeTestUserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TheoryKnowledgeTestUserDao
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-01-03 14:32:07
 */
@ApplicationScoped
public class TheoryKnowledgeTestUserDao extends BaseRepository<TheoryKnowledgeTestUserEntity, String> {
  /**
   * 根据用户ID和教案ID获取测验答案
   *
   * @param userId
   * @param knowledgeSwfId
   * @return
   */
  public TheoryKnowledgeTestUserEntity findFirstByUserIdAndKnowledgeSwfId(String userId, String knowledgeSwfId) {
    return find("userId = ?1 and knowledgeSwfId = ?2", userId, knowledgeSwfId).firstResult();
  }

  /**
   * 根据用户ID和知识ID获取全部测验答案
   *
   * @param userId
   * @param knowledgeId
   * @return
   */
  public List<TheoryKnowledgeTestUserEntity> findAllByUserIdAndKnowledgeId(String userId, String knowledgeId) {
    return find("userId = ?1 and knowledgeId = ?2", userId, knowledgeId).list();
  }

  public List<TheoryKnowledgeTestUserEntity> findAllByUserIdAndKnowledgeIdAndScore(String userId, String knowledgeId, int s) {
    return find("userId = ?1 and knowledgeId = ?2 and score = ?3", userId, knowledgeId, s).list();
  }


  /**
   * 根据知识ID获取全部测验答案
   *
   * @param knowledgeId
   * @return
   */
  public List<TheoryKnowledgeTestUserEntity> findAllByKnowledgeId(String knowledgeId) {
    return find("knowledgeId = ?1 ", knowledgeId).list();
  }

  /**
   * 根据教案ID获取全部测验答案
   *
   * @param knowledgeSwfId
   * @return
   */
  public List<TheoryKnowledgeTestUserEntity> findFirstByKnowledgeSwfId(String knowledgeSwfId) {
    return find("knowledgeSwfId = ?1", knowledgeSwfId).list();
  }


  /**
   * 获得用户所有提交的答案
   *
   * @param userId
   * @return
   */
  public List<TheoryKnowledgeTestUserEntity> findAllByUserIdAndKnowledgeSwfIdIn(String userId, List<String> swfIds) {
    return find("userId= ?1 and knowledgeSwfId in (?2)", userId, swfIds).list();
  }


  /**
   * 查询已学课件
   *
   * @param id
   * @return
   */
  public Integer countByUserId(String id) {
    return find("userId= ?1", id).list().size();
  }

  //@Query(value = "select t.time,count(t.time) count from (select FROM_UNIXTIME(create_time/1000,'%Y-%m') time  from t_theory_knowledge_test_user where user_id  =?1 ) t where t.time like ?2  GROUP BY t.time",nativeQuery = true)
  public List<Map<String, Object>> countSwfNum(String id, String year) {
    List<TheoryKnowledgeTestUserCountSwfNumDto> countSwfNum = entityManager.createNamedQuery("count_swf_num", TheoryKnowledgeTestUserCountSwfNumDto.class).setParameter(1, id)
        .setParameter(2, year)
        .getResultList();
    List<Map<String, Object>> ret = new ArrayList<>();
    for (TheoryKnowledgeTestUserCountSwfNumDto dto : countSwfNum) {
      Map<String, Object> item = new HashMap<>();
      item.put("time", dto.getTime());
      item.put("count", dto.getCount());
      ret.add(item);
    }
    return ret;
  }

  @Transactional
  public void deleteByKnowledgeIdAndKnowledgeSwfId(String knowledgeId, String socre) {
    delete("knowledgeId = ?1 and knowledgeSwfId = ?2", knowledgeId, socre);
  }
}
