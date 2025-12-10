package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.AllExamDto;
import com.nip.dto.sql.FindExamDto;
import com.nip.dto.sql.FindExamIdDto;
import com.nip.dto.sql.FindUserMonthAvgScoreDto;
import com.nip.dto.vo.TheoryKnowledgeExamUserSelfVO;
import com.nip.entity.TheoryKnowledgeExamUserEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static com.nip.common.constants.BaseConstants.EXAM_ID;
import static com.nip.common.constants.BaseConstants.USER_ID;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/2/23 14:55
 */
@ApplicationScoped
public class TheoryKnowledgeExamUserDao extends BaseRepository<TheoryKnowledgeExamUserEntity, String> {

  public List<FindExamIdDto> findAllByExamIdSql(String examId) {
    return entityManager.createNamedQuery("find_all_exam_id_sql", FindExamIdDto.class).setParameter(EXAM_ID, examId)
        .getResultList();
  }

  @Transactional
  public void deleteAllByExamId(String examId) {
    delete(EXAM_ID, examId);
  }

  public List<TheoryKnowledgeExamUserEntity> findAllByExamId(String examId) {
    return find(EXAM_ID, examId).list();
  }

  public TheoryKnowledgeExamUserEntity findAllByExamIdAndUserId(String examId, String userId) {
    return find("examId = ?1 and userId = ?2", examId, userId).firstResult();
  }

  public List<TheoryKnowledgeExamUserEntity> findAllByUserIdAndEndTimeLike(String userId, String time) {
    return find("userId = ?1 and endTime like ?2", userId, time).list();
  }

  public List<TheoryKnowledgeExamUserEntity> findAllByUserIdAndEndTimeLikeAndState(String userId, String time,
                                                                                   Integer state) {
    return find("userId = ?1 and endTime like ?2 and state = ?3", userId, time, state).list();
  }

  public List<FindExamDto> findAllExam(int type1, int type2, String userId) {
    return entityManager.createNamedQuery("find_all_exam_user", FindExamDto.class).setParameter("s1", type1)
        .setParameter("s2", type2).setParameter("u", userId).getResultList();
  }

  public List<FindExamDto> findAllExamTwo(int type1, String userId) {
    return entityManager.createNamedQuery("find_all_exam_user_two", FindExamDto.class).setParameter("s", type1)
        .setParameter("u", userId).getResultList();
  }

  /**
   * 根据用户id查询考试次数
   *
   * @param: userId
   */
  public Integer countByUserIdAndStateGreaterThanEqual(String userId, Integer state) {
    return Math.toIntExact(find("userId = ?1 and state >= ?2", userId, state).count());
  }

  /**
   * 根据用户id查询考试最高分
   *
   * @param: userId
   */
  public BigDecimal findByUserIdMaxScore(String userId) {
    Integer resultList = entityManager.createQuery("select max(score) from t_theory_knowledge_exam_user "
            + "where userId = :userId and state = 4 and startTime is not null and endTime is not null",
        Integer.class
    ).setParameter(USER_ID, userId).getResultList().getFirst();
    if (resultList == null) {
      return BigDecimal.ZERO;
    } else {
      return BigDecimal.valueOf(resultList);
    }
  }

  /**
   * 最低分
   *
   * @param: userId
   */
  public BigDecimal findByUserIdMinScore(String userId) {
    Integer resultList = entityManager.createQuery("select min(score) from t_theory_knowledge_exam_user "
            + "where userId = :userId and state = 4 and startTime is not null and endTime is not null",
        Integer.class
    ).setParameter(USER_ID, userId).getResultList().getFirst();
    if (resultList == null) {
      return BigDecimal.ZERO;
    } else {
      return BigDecimal.valueOf(resultList);
    }
  }

  /**
   * 平均分
   *
   * @param: userId
   */
  public BigDecimal findByUserIdAvgScore(String userId) {
    Double resultList = entityManager.createQuery("select avg(score) from t_theory_knowledge_exam_user "
                + "where userId = :userId and state = 4 and startTime is not null and endTime is not null",
            Double.class
        ).setParameter(USER_ID, userId)
        .getResultList().getFirst();
    if (resultList == null) {
      return BigDecimal.ZERO;
    } else {
      return BigDecimal.valueOf(resultList);
    }
  }

  /**
   * 查询用户每月平均分
   *
   * @param: userId
   */
  public List<FindUserMonthAvgScoreDto> findByUserMonthAvgScore(String userId, String year) {
    return entityManager.createNamedQuery("find_user_month_avg_score", FindUserMonthAvgScoreDto.class)
        .setParameter("ui", userId).setParameter("et", year + "%").getResultList();
  }

  public List<TheoryKnowledgeExamUserSelfVO> findAllIsSelfTesting(String userId) {
    return entityManager.createNamedQuery("find_all_is_self_testing", TheoryKnowledgeExamUserSelfVO.class)
        .setParameter(USER_ID, userId)
        .getResultList();
  }


  public TheoryKnowledgeExamUserEntity findByExamId(String examId) {
    return find("examId = ?1 ", examId).firstResult();
  }

  public List<TheoryKnowledgeExamUserEntity> findByUserId(String id) {
    return find("userId= ?1 and endTime is not null and startTime is not null and state = 4", id).list();
  }

  public TheoryKnowledgeExamUserEntity findByUserIdAndEndTimePrevious(String userId, String endTime) {
    return find("userId = ?1 and endTime < ?2 ", Sort.by("endTime").descending(), userId, endTime).firstResult();
  }


  /**
   * 根据t_theory_knowledge_exam_user id 查询已合适的考试
   *
   * @Query(value = "select count(*) from t_theory_knowledge_exam_user eu\n" +
   * "LEFT JOIN t_theory_knowledge_exam_test_paper tp on tp.exam_id = eu.exam_id\n" +
   * "where eu.id in (?1) and eu.score >= tp.pass_mark",nativeQuery = true)
   */

  public Integer countExamPass(List<String> examUserId) {
    //sql 待迁移
    List<Long> resultList = entityManager.createQuery("select count(*) " +
            "from t_theory_knowledge_exam_user eu " +
            "LEFT JOIN t_theory_knowledge_exam_test_paper tp on tp.examId = eu.examId " +
            "where eu.id in (?1) and eu.score >= tp.passMark", Long.class)
        .setParameter(1, examUserId)
        .getResultList();
    if (resultList == null || resultList.isEmpty()) {
      return 0;
    } else {
      Long firstElement = resultList.getFirst();
      return firstElement != null ? firstElement.intValue() : 0;
    }

  }

  public List<AllExamDto> fingAllExam(int state1, int state2, String userId) {
    return entityManager.createNamedQuery("find_all_exam_one", AllExamDto.class)
        .setParameter("state1", state1)
        .setParameter("state2", state2)
        .setParameter(USER_ID, userId)
        .getResultList();
  }

  public List<AllExamDto> fingAllExamTwo(int state, String userId) {
    return entityManager.createNamedQuery("find_all_exam_two", AllExamDto.class)
        .setParameter("state1", state)
        .setParameter(USER_ID, userId)
        .getResultList();
  }

}
