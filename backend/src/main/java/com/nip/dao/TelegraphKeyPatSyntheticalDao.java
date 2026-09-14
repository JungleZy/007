package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.TelegraphKeyPatSyntheticalEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * @Author: wushilin
 * @Data: 2022-06-09 09:59
 * @Description:
 */
@ApplicationScoped
public class TelegraphKeyPatSyntheticalDao extends BaseRepository<TelegraphKeyPatSyntheticalEntity, String> {

  public List<TelegraphKeyPatSyntheticalEntity> findAllByCreateUserIdOrderByCreateTimeDesc(String userId) {
    return find("createUserId = ?1 order by createTime desc", userId).list();
  }

  public Map<String, Object> finishStatistical(String userId) {
    // 当前读：等待属主行锁后不能复用 token 查询建立的旧 RR 快照；只载入统计所需标量。
    List<Object[]> rows = entityManager.createNativeQuery(
        "SELECT duration,speed FROM t_telegraph_key_pat_synthetical_train "
            + "WHERE create_user_id=:userId AND status=3 FOR UPDATE", Object[].class)
        .setParameter("userId", userId).getResultList();
    BigDecimal totalTime = BigDecimal.ZERO;
    BigDecimal totalSpeed = BigDecimal.ZERO;
    for (Object[] row : rows) {
      totalTime = totalTime.add(new BigDecimal(Objects.toString(row[0], "0")));
      totalSpeed = totalSpeed.add(new BigDecimal(Objects.toString(row[1], "0")));
    }
    BigDecimal average = rows.isEmpty() ? BigDecimal.ZERO : totalSpeed.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP);
    return Map.of("totalTime", totalTime.toPlainString(), "totalCount", rows.size(), "avgSpeed", average);
  }

  public TelegraphKeyPatSyntheticalEntity findLastTrain(String id) {
    return find("createUserId = ?1 order by createTime desc", id).firstResult();
  }
}
