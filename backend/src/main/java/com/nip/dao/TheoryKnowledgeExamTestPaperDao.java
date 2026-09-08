package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TheoryKnowledgeExamTestPaperEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static com.nip.common.constants.BaseConstants.EXAM_ID;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/2/22 16:50
 */
@ApplicationScoped
public class TheoryKnowledgeExamTestPaperDao extends BaseRepository<TheoryKnowledgeExamTestPaperEntity, String> {

  public TheoryKnowledgeExamTestPaperEntity findAllByExamId(String examId) {
    return find(EXAM_ID, examId).firstResult();
  }

  /**
   * 根据用户ID查询考试通过次数
   *
   * @param: uid
   */
  public Long getByUserIdPassNum(String uid) {
    List<Long> resultList = entityManager.createQuery("select count(eu.score) from t_theory_knowledge_exam_user eu "
            + "LEFT JOIN t_theory_knowledge_exam e on e.id = eu.examId "
            + "LEFT JOIN t_theory_knowledge_exam_test_paper tp on e.id = tp.examId "
            + "where eu.userId=:uid and eu.state =4 and eu.score >= tp.passMark",
        Long.class
    ).setParameter("uid", uid).getResultList();
    if (resultList == null || resultList.isEmpty()) {
      return 0L;
    } else {
      Long firstElement = resultList.getFirst();
      return firstElement != null ? firstElement : 0L;
    }
  }
}
