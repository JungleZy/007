package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.sql.TheoryKnowledgeSwfRecordContStudyTimeDto;
import com.nip.entity.TheoryKnowledgeSwfRecordEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TheoryKnowledgeSwfRecordDao
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-01-03 18:13:40
 */
@ApplicationScoped
public class TheoryKnowledgeSwfRecordDao extends BaseRepository<TheoryKnowledgeSwfRecordEntity, String> {

  public List<TheoryKnowledgeSwfRecordEntity> findAllByUserIdAndKnowledgeSwfId(String userId, String swfId) {
    return find("userId = ?1 and knowledgeSwfId = ?2", userId, swfId).list();
  }

  public List<TheoryKnowledgeSwfRecordEntity> findAllByUserIdAndJoinTimeLikeAndType(String userId, String time, int type) {
    return find("userId =?1 and joinTime like ?2 and type =?3", userId, time, type).list();
  }

  /**
   * 根据userid查询已学课件
   *
   * @param userId
   * @return
   */
  public Integer countByUserId(String userId) {
    return find("userId = ?1", userId).list().size();
  }

  /**
   * 查询用户已学时长
   *
   * @param userId
   * @return
   */
  //@Query(value = "select Round(sum(TIME_TO_SEC(TIMEDIFF(exit_time,join_time)))/3600,2) t  from t_theory_knowledge_swf_record where user_id = ?1",nativeQuery = true)
  public BigDecimal countStudyTimeByUserId(String userId) {
    List<BigDecimal> resultList = entityManager.createQuery("select IFNULL(Round(sum(TIME_TO_SEC(TIMEDIFF(exitTime,joinTime)))/3600,2),0) t   " +
            "from t_theory_knowledge_swf_record where userId = ?1", BigDecimal.class)
        .setParameter(1, userId)
        .getResultList();

    if (resultList == null || resultList.isEmpty()) {
      return BigDecimal.ZERO;
    } else {
      BigDecimal firstElement = resultList.getFirst();
      return firstElement != null ? firstElement : BigDecimal.ZERO;
    }
  }

  /**
   * 查询该用户每月学习时长和学习课件数量
   *
   * @param userId
   * @return
   */
 /* @Query(value = "select t1.*,t2.swf from (SELECT t.m,ROUND(sum(t.time)/3600,2) time from \n" +
          "(select id,user_id, DATE_FORMAT(exit_time,'%c') m ,TIME_TO_SEC(TIMEDIFF(exit_time,join_time)) time from t_theory_knowledge_swf_record where user_id = ?1 and exit_time like ?2% ) t GROUP BY t.m ) t1 LEFT JOIN (\n" +
          "select tt.time m,count(tt.knowledge_swf_id) swf from (select t.time,knowledge_swf_id from (\n" +
          "SELECT  knowledge_swf_id,DATE_FORMAT(exit_time,'%c') time from t_theory_knowledge_swf_record where user_id =  ?1 and exit_time like ?2% \n" +
          ") t GROUP BY t.time,t.knowledge_swf_id) tt GROUP BY tt.time) t2 on t1.m=t2.m",nativeQuery = true)*/
  public List<Map<String, Object>> countMonthStudyTimeAndSwfNum(String userId, String yaer) {
    return null;
  }

  //@Query(value = "select t.date,Round(sum(TIMESTAMPDIFF(SECOND,t.join_time,t.exit_time))/3600,2) time from (select *,DATE_FORMAT(join_time,'%Y-%m') date from t_theory_knowledge_swf_record where user_id = ?1 and join_time like ?2) t group by t.date;",nativeQuery = true)
  public List<Map<String, Object>> countStudyTime(String id, String year) {
    List<TheoryKnowledgeSwfRecordContStudyTimeDto> countStudyTime = entityManager.createNamedQuery("count_study_time", TheoryKnowledgeSwfRecordContStudyTimeDto.class)
        .setParameter(1, id)
        .setParameter(2, year + "%")
        .getResultList();
    List<Map<String, Object>> ret = new ArrayList<>();
    for (TheoryKnowledgeSwfRecordContStudyTimeDto dto : countStudyTime) {
      Map<String, Object> item = new HashMap<>();
      item.put("date", dto.getDate());
      item.put("time", dto.getTime());
      ret.add(item);
    }
    return ret;
  }
}
