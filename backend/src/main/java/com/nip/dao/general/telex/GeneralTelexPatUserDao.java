package com.nip.dao.general.telex;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.GeneralTelexPatUserDto;
import com.nip.dto.GeneralTelexPatUserSimpleDto;
import com.nip.entity.simulation.telex.GeneralTelexPatUserEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class GeneralTelexPatUserDao extends BaseRepository<GeneralTelexPatUserEntity, String> {
  public GeneralTelexPatUserEntity findByUserIdAndTrainId(String userId, String trainId) {
    return find("userId =?1 and trainId =?2", userId, trainId).firstResult();
  }
  public List<GeneralTelexPatUserEntity> findByUserId(String userId) {
    return find("userId =?1", userId).list();
  }
  public List<GeneralTelexPatUserEntity> findRoleAdminByUserId(String trainId) {
    return find("trainId =?1 and role= 1", trainId).list();
  }
  public List<GeneralTelexPatUserDto> findByTrainIdToMap(String id) {
    return entityManager.createNamedQuery("find_general_telex_pat_user_dto", GeneralTelexPatUserDto.class)
        .setParameter(1, id)
        .getResultList();
  }
  public List<GeneralTelexPatUserSimpleDto> findByTrainIdToMapSimple(String id) {
    return entityManager.createNamedQuery("find_general_telex_pat_user_simple_dto", GeneralTelexPatUserSimpleDto.class)
        .setParameter(1, id)
        .getResultList();
  }
  public List<GeneralTelexPatUserEntity> findByTrainIdAndRole(String trainId, Integer role) {
    return find("trainId =?1 and role =?2", trainId, role).list();
  }
  public List<BigDecimal> findByFistTwoScore(String uid, LocalDateTime createTime) {
    return find("userId =?1 and createTime < ?2 ORDER BY finishTime DESC LIMIT 2", uid, createTime).list()
        .stream().map(GeneralTelexPatUserEntity::getScore).toList();
  }
}
