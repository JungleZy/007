package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.RadiotelephoneEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;

import static com.nip.common.constants.BaseConstants.USER_ID;

/** Persistence access for the per-user radio aggregate. */
@ApplicationScoped
public class RadiotelephoneDao extends BaseRepository<RadiotelephoneEntity, String> {

  public List<RadiotelephoneEntity> findAllByUserId(String userId) {
    return find(USER_ID, userId).list();
  }

  /** A plain read for reporting and foreign-session lookup. */
  public RadiotelephoneEntity findByUserIdAndType(String userId, Integer type) {
    return find("userId = ?1 and type = ?2", userId, type).firstResult();
  }

  /** Must only be called from a transaction that already locks the owning user row. */
  public RadiotelephoneEntity findByUserIdAndTypeForUpdate(String userId, Integer type) {
    return find("userId = ?1 and type = ?2", userId, type)
        .withLock(LockModeType.PESSIMISTIC_WRITE)
        .firstResult();
  }

  public RadiotelephoneEntity findByActiveSessionId(String sessionId) {
    return find("activeSessionId", sessionId).firstResult();
  }

  public RadiotelephoneEntity findByFinalizedSessionId(String sessionId) {
    return find("finalizedSessionId", sessionId).firstResult();
  }
}
