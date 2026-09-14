package com.nip.service;

import com.nip.common.exception.ForbiddenException;
import com.nip.common.exception.TerminalStateException;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.RadiotelephoneDao;
import com.nip.dao.UserDao;
import com.nip.dto.RadiotelephoneDto;
import com.nip.dto.vo.RadiotelephoneVO;
import com.nip.entity.RadiotelephoneEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Persists the pre-job radio study clock and its historical aggregate.
 *
 * <p>The user row is the serialization point.  It exists before a radio aggregate
 * does, so locking it makes first-use creation and every later state transition
 * one transaction without a detached-row or nested-transaction retry path.</p>
 */
@ApplicationScoped
@Slf4j
public class RadiotelephoneService {

  private final UserService userService;
  private final UserDao userDao;
  private final RadiotelephoneDao radiotelephoneDao;

  @Inject
  public RadiotelephoneService(UserService userService, UserDao userDao,
                               RadiotelephoneDao radiotelephoneDao) {
    this.userService = userService;
    this.userDao = userDao;
    this.radiotelephoneDao = radiotelephoneDao;
  }

  @Transactional(rollbackOn = Exception.class)
  public List<RadiotelephoneVO> listPage(String token, RadiotelephoneDto dto) {
    UserEntity user = authenticatedUserWithLock(token);
    if (dto != null) {
      validate(dto);
      findOrCreateLocked(user.getId(), dto.getType());
    }
    return radiotelephoneDao.find("userId", user.getId()).withLock(LockModeType.PESSIMISTIC_WRITE)
        .list().stream().map(row -> view(row, row.getActiveSessionId()))
        .sorted(Comparator.comparing(RadiotelephoneVO::getType)).toList();
  }

  @Transactional(rollbackOn = Exception.class)
  public RadiotelephoneVO begin(RadiotelephoneDto dto, String token) {
    validate(dto);
    UserEntity user = authenticatedUserWithLock(token);
    RadiotelephoneEntity row = findOrCreateLocked(user.getId(), dto.getType());

    // Begin is deliberately idempotent.  A browser retry must not replace the
    // session or its start time, including while the session is paused.
    if (row.getActiveSessionId() == null) {
      row.setActiveSessionId(UUID.randomUUID().toString());
      row.setSessionStartedAt(Instant.now());
      if (row.getActiveMillis() == null) {
        row.setActiveMillis(0L);
      }
      radiotelephoneDao.saveAndFlush(row);
    }
    return view(row, row.getActiveSessionId());
  }

  @Transactional(rollbackOn = Exception.class)
  public RadiotelephoneVO pause(RadiotelephoneDto dto, String token) {
    validate(dto);
    String sessionId = requiredSessionId(dto);
    UserEntity user = authenticatedUserWithLock(token);
    RadiotelephoneEntity row = existingLocked(user.getId(), dto.getType(), sessionId);
    requireActiveSession(row, sessionId, user.getId());

    if (row.getSessionStartedAt() != null) {
      row.setActiveMillis(addElapsed(row.getActiveMillis(), row.getSessionStartedAt(), Instant.now()));
      row.setSessionStartedAt(null);
      radiotelephoneDao.saveAndFlush(row);
    }
    return view(row, sessionId);
  }

  @Transactional(rollbackOn = Exception.class)
  public RadiotelephoneVO resume(RadiotelephoneDto dto, String token) {
    validate(dto);
    String sessionId = requiredSessionId(dto);
    UserEntity user = authenticatedUserWithLock(token);
    RadiotelephoneEntity row = existingLocked(user.getId(), dto.getType(), sessionId);
    requireActiveSession(row, sessionId, user.getId());

    // Resuming an already running session is also idempotent: never reset the
    // authoritative start time on duplicate clicks or request retries.
    if (row.getSessionStartedAt() == null) {
      row.setSessionStartedAt(Instant.now());
      radiotelephoneDao.saveAndFlush(row);
    }
    return view(row, sessionId);
  }

  @Transactional(rollbackOn = Exception.class)
  public RadiotelephoneVO finish(RadiotelephoneDto dto, String token) {
    validate(dto);
    String sessionId = requiredSessionId(dto);
    UserEntity user = authenticatedUserWithLock(token);
    RadiotelephoneEntity row = existingLocked(user.getId(), dto.getType(), sessionId);

    // The last finalized session is retained solely as an idempotency marker.
    // A delayed browser request therefore cannot increment a newer session.
    if (sessionId.equals(row.getFinalizedSessionId())) {
      return view(row, sessionId);
    }
    requireActiveSession(row, sessionId, user.getId());

    long elapsedMillis = addElapsed(row.getActiveMillis(), row.getSessionStartedAt(), Instant.now());
    long elapsedSeconds = Math.min(Integer.MAX_VALUE, elapsedMillis / 1_000L);
    int priorCount = row.getTotalCount() == null ? 0 : row.getTotalCount();
    row.setTotalCount(Math.addExact(priorCount, 1));
    row.setTotalTime(String.valueOf((long) parseTotalTime(row.getTotalTime()) + elapsedSeconds));
    row.setFinalizedSessionId(sessionId);
    row.setActiveSessionId(null);
    row.setSessionStartedAt(null);
    row.setActiveMillis(null);
    return view(radiotelephoneDao.saveAndFlush(row), sessionId);
  }

  private UserEntity authenticatedUserWithLock(String token) {
    UserEntity authenticated = userService.getUserByToken(token);
    UserEntity locked = userDao.find("id", authenticated.getId())
        .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
    if (locked == null) {
      throw new IllegalArgumentException("用户不存在");
    }
    return locked;
  }

  private RadiotelephoneEntity findOrCreateLocked(String userId, Integer type) {
    RadiotelephoneEntity row = radiotelephoneDao.findByUserIdAndTypeForUpdate(userId, type);
    if (row != null) {
      return row;
    }

    RadiotelephoneEntity created = new RadiotelephoneEntity();
    created.setUserId(userId);
    created.setType(type);
    created.setTotalCount(0);
    created.setTotalTime("0");
    return radiotelephoneDao.saveAndFlush(created);
  }

  private RadiotelephoneEntity existingLocked(String userId, Integer type, String sessionId) {
    RadiotelephoneEntity row = radiotelephoneDao.findByUserIdAndTypeForUpdate(userId, type);
    if (row == null) {
      rejectForeignSession(sessionId, userId);
      throw new TerminalStateException("训练会话已失效");
    }
    return row;
  }

  private void rejectForeignSession(String sessionId, String userId) {
    RadiotelephoneEntity foreign = radiotelephoneDao.findByActiveSessionId(sessionId);
    if (foreign != null && !userId.equals(foreign.getUserId())) {
      throw new ForbiddenException("话报训练会话不属于当前用户");
    }
  }

  private void validate(RadiotelephoneDto dto) {
    if (dto == null || dto.getType() == null) {
      throw new IllegalArgumentException("话报训练参数无效");
    }
  }

  private String requiredSessionId(RadiotelephoneDto dto) {
    String sessionId = dto.getSessionId();
    if (sessionId == null || sessionId.isBlank()) {
      throw new IllegalArgumentException("话报训练缺少训练会话");
    }
    return sessionId.trim();
  }

  private void requireActiveSession(RadiotelephoneEntity row, String sessionId, String userId) {
    if (sessionId.equals(row.getActiveSessionId())) {
      return;
    }
    rejectForeignSession(sessionId, userId);
    throw new TerminalStateException("训练会话已失效");
  }

  private RadiotelephoneVO view(RadiotelephoneEntity row, String sessionId) {
    RadiotelephoneVO result = PojoUtils.convertOne(row, RadiotelephoneVO.class);
    result.setSessionId(sessionId);
    return result;
  }

  private long addElapsed(Long storedMillis, Instant startedAt, Instant now) {
    long stored = storedMillis == null ? 0L : Math.max(0L, storedMillis);
    if (startedAt == null) {
      return stored;
    }
    long elapsed = Math.max(0L, Duration.between(startedAt, now).toMillis());
    if (Long.MAX_VALUE - stored < elapsed) {
      return Long.MAX_VALUE;
    }
    return stored + elapsed;
  }

  /** Historical rows can contain non-numeric totalTime; preserve them by treating them as zero. */
  private int parseTotalTime(String totalTime) {
    if (totalTime == null || totalTime.isBlank()) {
      return 0;
    }
    try {
      return Math.max(0, Integer.parseInt(totalTime));
    } catch (NumberFormatException exception) {
      log.warn("话报训练累计时长不是数字，按 0 计算:{}", totalTime);
      return 0;
    }
  }
}
