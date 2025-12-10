package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.sql.FindTheoryKnowledgeDto;
import com.nip.dto.sql.FindTheoryKnowledgeSwfTestDto;
import com.nip.entity.TheoryKnowledgeEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TYPE;
import static com.nip.common.constants.BaseConstants.USER_ID;

/**
 * TheoryKnowledgeDao
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-20 11:15
 */
@ApplicationScoped
public class TheoryKnowledgeDao extends BaseRepository<TheoryKnowledgeEntity, String> {

  public List<FindTheoryKnowledgeDto> findTheoryKnowledgeDtoAllSql(int type, List<String> difficulty,
                                                                   List<String> specialty) {
    return entityManager.createNamedQuery("find_theory_knowledge_dto_all_sql", FindTheoryKnowledgeDto.class)
        .setParameter(TYPE, type).setParameter("dids", difficulty).setParameter("sids", specialty)
        .getResultList();
  }

  public List<FindTheoryKnowledgeDto> findTheoryKnowledgeDtoAllSqlOpen(int type, int status, List<String> difficulty,
                                                                       List<String> specialty) {
    return entityManager.createNamedQuery("find_theory_knowledge_dto_all_sql_open", FindTheoryKnowledgeDto.class)
        .setParameter(TYPE, type).setParameter("status", status).setParameter("dids", difficulty)
        .setParameter("sids", specialty).getResultList();
  }

  /**
   * 根据knowledge id关联查询knowledge_swf 、knowledge_test_user表
   */
  public List<FindTheoryKnowledgeSwfTestDto> findKnowledgeAndSwfAndTest(String userId, String year) {
    return entityManager.createNamedQuery("find_theory_knowledge_swf_test", FindTheoryKnowledgeSwfTestDto.class)
        .setParameter(USER_ID, userId).setParameter("year", year).getResultList();
  }

}
