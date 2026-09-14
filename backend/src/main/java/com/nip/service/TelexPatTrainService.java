package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.exception.ForbiddenException;
import com.nip.common.exception.UnauthorizedException;
import com.nip.common.constants.PostTelexPatTrainStatusEnum;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.common.utils.GlobalMessageGeneratedUtil;
import com.nip.dao.TelexPatTrainDao;
import com.nip.dto.TelexPatTrainDto;
import com.nip.dto.vo.TelexPatTrainVO;
import com.nip.entity.TelexPatTrainEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.nip.common.constants.PostTelexPatTrainStatusEnum.FINISH;
import static com.nip.common.constants.PostTelexPatTrainStatusEnum.NOT_STARTED;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/29 9:56
 */
@ApplicationScoped
@Slf4j
public class TelexPatTrainService {
  private final TelexPatTrainDao telexPatTrainDao;
  private final UserService userService;
  private final TelexPatTrainStatisticalService statisticalService;
  private final ManagedExecutor managedExecutor;
  private final TransactionManager transactionManager;

  /** 属主判定的唯一口径（个人域 = 仅创建者）。 */
  @Inject TrainWriteAccess trainWriteAccess;

  @Inject
  public TelexPatTrainService(TelexPatTrainDao telexPatTrainDao, UserService userService,
      TelexPatTrainStatisticalService statisticalService, ManagedExecutor managedExecutor,
      TransactionManager transactionManager) {
    this.telexPatTrainDao = telexPatTrainDao;
    this.userService = userService;
    this.statisticalService = statisticalService;
    this.managedExecutor = managedExecutor;
    this.transactionManager = transactionManager;
  }

  /**
   * 保存训练记录
   *
   * @param token 用户令牌，用于识别和验证用户身份
   * @param dto   包含训练记录信息的数据传输对象
   * @return 返回保存结果的响应实体
   */
  private static final int MIN_TYPE = 0;
  private static final int MAX_TYPE = 2;

  private static void validateStatus(Integer status) {
    if (status == null || status < NOT_STARTED.getStatus() || status > FINISH.getStatus()) {
      throw new IllegalArgumentException("训练状态无效");
    }
  }

  private static void validateTransition(int current, int requested) {
    validateStatus(requested);
    if (current == requested) return;
    boolean allowed = (current == NOT_STARTED.getStatus() && requested == PostTelexPatTrainStatusEnum.UNDERWAY.getStatus())
        || (current == PostTelexPatTrainStatusEnum.UNDERWAY.getStatus()
        && (requested == PostTelexPatTrainStatusEnum.PAUSE.getStatus() || requested == FINISH.getStatus()))
        || (current == PostTelexPatTrainStatusEnum.PAUSE.getStatus()
        && (requested == PostTelexPatTrainStatusEnum.UNDERWAY.getStatus() || requested == FINISH.getStatus()));
    if (!allowed) throw new IllegalStateException("训练状态流转无效");
  }

  private static void validateType(Integer type) {
    if (type == null || type < MIN_TYPE || type > MAX_TYPE) {
      throw new IllegalArgumentException("训练类型无效");
    }
  }

  @Transactional
  public Response<TelexPatTrainEntity> saveTexPatTrain(String token, TelexPatTrainDto dto) {
    if (dto == null || dto.getStatus() == null || dto.getType() == null) {
      throw new IllegalArgumentException("训练状态和类型不能为空");
    }
    validateStatus(dto.getStatus());
    validateType(dto.getType());
    UserEntity userEntity = userService.getUserByToken(token);
    try {
      TelexPatTrainEntity incoming = PojoUtils.convertOne(dto, TelexPatTrainEntity.class);
      TelexPatTrainEntity entity;
      if (dto.getId() != null && !dto.getId().isBlank()) {
        entity = telexPatTrainDao.findForUpdate(dto.getId());
        if (entity == null) throw new IllegalArgumentException("未查询到训练");
        trainWriteAccess.requireTrainOwner(userEntity.getId(), entity.getCreateUserId(), "电传训练");
        if (FINISH.getStatus().equals(entity.getStatus())) {
          return ResponseResult.error(208, "训练已结束");
        }
        String source = entity.getContent();
        Long activeSince = entity.getActiveSince();
        if (entity.getProtocolVersion() == null || entity.getProtocolVersion() < 1) {
          throw new IllegalStateException("旧版训练记录不可继续提交");
        }
        validateTransition(entity.getStatus() == null ? NOT_STARTED.getStatus() : entity.getStatus(), dto.getStatus());
        Long accumulated = entity.getAccumulatedActiveMillis();
        validateAndStoreContent(entity, incoming.getContent(), source, dto.getExtendBy());
      } else {
        if (dto.getStatus() == FINISH.getStatus()) {
          throw new IllegalStateException("训练尚未开始，不能完成");
        }
        if (dto.getStatus() == NOT_STARTED.getStatus()) {
          TelexPatTrainEntity previous = telexPatTrainDao.lastTration(userEntity.getId(), dto.getType());
          if (previous != null && !Objects.equals(previous.getId(), incoming.getId())) {
            if (NOT_STARTED.getStatus().equals(previous.getStatus())) {
              telexPatTrainDao.deleteById(previous.getId());
            } else if (PostTelexPatTrainStatusEnum.PAUSE.getStatus().equals(previous.getStatus())) {
              previous.setStatus(FINISH.getStatus());
              telexPatTrainDao.save(previous);
              statisticalService.statistical(userEntity.getId(), previous.getType() + 1, previous);
            }
          }
        }
        entity = incoming;
        entity.setId(null);
        entity.setCreateUserId(userEntity.getId());
        entity.setStatus(NOT_STARTED.getStatus());
        entity.setProtocolVersion(1);
        entity.setAccumulatedActiveMillis(0L);
      }
      updateActiveClock(entity, dto.getStatus());
      TelexPatTrainEntity save = telexPatTrainDao.save(entity);
      if (FINISH.getStatus().equals(dto.getStatus())) {
        applyMetrics(save);
        telexPatTrainDao.save(save);
        statisticalService.statistical(userEntity.getId(), save.getType() + 1, save);
      }
      return ResponseResult.success(save);
    } catch (UnauthorizedException | ForbiddenException | IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      try {
        transactionManager.setRollbackOnly();
      } catch (SystemException rollbackFailure) {
        e.addSuppressed(rollbackFailure);
        throw new IllegalStateException("无法标记训练保存事务回滚", e);
      }
      log.error("保存训练记录失败", e);
      return ResponseResult.error();
    }
  }
  private void validateAndStoreContent(TelexPatTrainEntity entity, String submitted, String source, Integer extendBy) {
    if (submitted == null) return;
    List<Map<String, Object>> expected = source == null ? List.of()
        : JSONUtils.fromJson(source, new TypeToken<>() {});
    List<Map<String, Object>> actual = JSONUtils.fromJson(submitted, new TypeToken<>() {});
    if (actual == null || expected == null) throw new IllegalArgumentException("训练报文内容无效");
    int extension = extendBy == null ? 0 : extendBy;
    if (extension < 0 || extension > 100) throw new IllegalArgumentException("扩展数量无效");
    if (extension == 0 && actual.size() != expected.size()) throw new IllegalArgumentException("训练报文结构不一致");
    if (extension > 0 && actual.size() != expected.size()) throw new IllegalArgumentException("扩展请求必须基于完整原始报文");
    for (int i = 0; i < expected.size(); i++) {
      if (!Objects.equals(String.valueOf(expected.get(i).get("text")), String.valueOf(actual.get(i).get("text"))))
        throw new IllegalArgumentException("训练报文源内容不可修改");
    }
    if (extension > 0) {
      List<String> generated = switch (entity.getType()) {
        case 0 -> GlobalMessageGeneratedUtil.generatedNumber(extension, true, true);
        case 1 -> GlobalMessageGeneratedUtil.generatedWord(extension, true, true);
        case 2 -> GlobalMessageGeneratedUtil.generatedMingle(extension, true, true);
        default -> throw new IllegalArgumentException("训练类型无效");
      };
      for (String text : generated) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("text", text);
        row.put("value", "");
        actual.add(row);
      }
    }
    entity.setContent(JSONUtils.toJson(actual));
  }

  private void applyMetrics(TelexPatTrainEntity entity) {
    List<Map<String, Object>> rows = entity.getContent() == null ? List.of()
        : JSONUtils.fromJson(entity.getContent(), new TypeToken<>() {});
    if (rows == null) throw new IllegalArgumentException("训练报文内容无效");
    int total = 0;
    int errors = 0;
    for (Map<String, Object> row : rows) {
      Object raw = row.get("value");
      if (raw == null || String.valueOf(raw).isBlank()) continue;
      total++;
      if (!Objects.equals(String.valueOf(row.get("text")), String.valueOf(raw))) errors++;
    }
    long millis = entity.getAccumulatedActiveMillis() == null ? 0L : entity.getAccumulatedActiveMillis();
    entity.setTotalNumber(total);
    entity.setErrorNumber(errors);
    entity.setAccuracy(total == 0 ? 0 : (total - errors) * 100 / total);
    entity.setDuration(String.valueOf(millis / 1000L));
    entity.setSpeed(millis == 0 ? "0" : BigDecimal.valueOf(total * 60000L)
        .divide(BigDecimal.valueOf(millis), 2, java.math.RoundingMode.HALF_UP).toPlainString());
  }

  private void updateActiveClock(TelexPatTrainEntity entity, int status) {
    long now = System.currentTimeMillis();
    int old = entity.getStatus() == null ? NOT_STARTED.getStatus() : entity.getStatus();
    if (old != PostTelexPatTrainStatusEnum.UNDERWAY.getStatus() && status == PostTelexPatTrainStatusEnum.UNDERWAY.getStatus()) {
      entity.setActiveSince(now);
    } else if (old == PostTelexPatTrainStatusEnum.UNDERWAY.getStatus() && status != PostTelexPatTrainStatusEnum.UNDERWAY.getStatus()) {
      long start = entity.getActiveSince() == null ? now : entity.getActiveSince();
      long total = entity.getAccumulatedActiveMillis() == null ? 0L : entity.getAccumulatedActiveMillis();
      entity.setAccumulatedActiveMillis(total + Math.max(0L, now - start));
      entity.setActiveSince(null);
    }
    entity.setStatus(status);
    if (status == FINISH.getStatus()) {
      long seconds = Math.max(0L, (entity.getAccumulatedActiveMillis() == null ? 0L : entity.getAccumulatedActiveMillis()) / 1000L);
      int total = entity.getTotalNumber() == null ? 0 : Math.max(0, entity.getTotalNumber());
      int errors = entity.getErrorNumber() == null ? 0 : Math.max(0, Math.min(total, entity.getErrorNumber()));
      entity.setTotalNumber(total);
      entity.setErrorNumber(errors);
      entity.setAccuracy(total == 0 ? 0 : (total - errors) * 100 / total);
      entity.setDuration(String.valueOf(seconds));
    }
  }

  public Response<List<TelexPatTrainEntity>> findTexPatTrainByToken(String token) {
    try {
      UserEntity userEntity = userService.getUserByToken(token);
      return ResponseResult.success(telexPatTrainDao.findAllByCreateUserId(userEntity.getId()));
    } catch (UnauthorizedException e) {
      throw e;
    } catch (Exception e) {
      return ResponseResult.error();
    }
  }

  public Response<TelexPatTrainEntity> findTexPatTrainById(String id, String token) {
    UserEntity user = userService.getUserByToken(token);
    TelexPatTrainEntity entity = telexPatTrainDao.findById(id);
    if (entity == null) throw new IllegalArgumentException("未查询到训练");
    trainWriteAccess.requireTrainOwner(user.getId(), entity.getCreateUserId(), "数据报训练");
    return ResponseResult.success(entity);
  }

  /**
   * 根据token 和type查询最后一次训练记录
   *
   * @param: token
   * @param: type
   */
  public TelexPatTrainVO lastPatTrain(String token, Integer type) {
    UserEntity userEntity = userService.getUserByToken(token);
    TelexPatTrainEntity telexPatTrainEntity = telexPatTrainDao.lastTration(userEntity.getId(), type);
    if (telexPatTrainEntity == null) {
      return null;
    }
    return PojoUtils.convertOne(telexPatTrainEntity, TelexPatTrainVO.class);
  }

  /**
   * 根据id删除训练。属主字段是 {@code createUserId}（各域字段名不同，这里显式传入）。
   */
  @Transactional
  public void deleteById(String id, String token) {
    TelexPatTrainEntity entity = telexPatTrainDao.findByIdOptional(id)
        .orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    trainWriteAccess.requireTrainOwner(userService.getUserByToken(token).getId(), entity.getCreateUserId(),
        "个人电传练习 " + id);
    telexPatTrainDao.deleteById(id);
  }
}
