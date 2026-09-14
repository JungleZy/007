package com.nip.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.nip.common.exception.ForbiddenException;
import com.nip.common.exception.TerminalStateException;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.RoleDao;
import com.nip.dao.TelegraphKeyPatSyntheticalDao;
import com.nip.dao.TelegraphKeyTrainStatisticalDao;
import com.nip.dao.UserDao;
import com.nip.dto.TelegraphKeyPatSyntheticalDto;
import com.nip.dto.vo.TelegraphKeyPatSyntheticalVO;
import com.nip.entity.TelegraphKeyPatSyntheticalEntity;
import com.nip.entity.TelegraphKeyTrainStatisticalEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Map;

import static com.nip.common.constants.BaseConstants.TRAINING_NOT_FOUND;
import static com.nip.common.constants.TelegraphKeyPatSyntheticalEnum.*;

@ApplicationScoped
public class TelegraphKeyPatSyntheticalService {
  private final TelegraphKeyPatSyntheticalDao syntheticalDao;
  private final UserService userService;
  private final TelegraphKeyTrainStatisticalDao statisticalDao;
  private final RoleDao roleDao;
  @Inject UserDao userDao;

  @Inject
  public TelegraphKeyPatSyntheticalService(TelegraphKeyPatSyntheticalDao syntheticalDao, UserService userService,
      TelegraphKeyTrainStatisticalDao statisticalDao, RoleDao roleDao) {
    this.syntheticalDao = syntheticalDao;
    this.userService = userService;
    this.statisticalDao = statisticalDao;
    this.roleDao = roleDao;
  }

  private TelegraphKeyPatSyntheticalEntity owned(String token, String id, boolean lock) {
    String actor = userService.getUserByToken(token).getId();
    if (id == null || id.isBlank()) throw new IllegalArgumentException(TRAINING_NOT_FOUND);
    TelegraphKeyPatSyntheticalEntity entity = syntheticalDao.findByIdOptional(id)
        .orElseThrow(() -> new IllegalArgumentException(TRAINING_NOT_FOUND));
    if (!Objects.equals(entity.getCreateUserId(), actor) && !roleDao.existsAdminRoleByUserId(actor)) {
      throw new ForbiddenException("非创建者访问电子键综合训练 " + id);
    }
    if (lock) {
      // 同一属主的新建、结算和统计重算按相同顺序串行；避免两场训练并发覆盖累计统计。
      lockOwner(entity.getCreateUserId());
      syntheticalDao.getEntityManager().refresh(entity, LockModeType.PESSIMISTIC_WRITE);
    }
    return entity;
  }

  private void lockOwner(String ownerId) {
    userDao.findByIdOptional(ownerId, LockModeType.PESSIMISTIC_WRITE)
        .orElseThrow(() -> new IllegalArgumentException("训练属主不存在"));
  }

  @Transactional
  public TelegraphKeyPatSyntheticalVO save(String token, TelegraphKeyPatSyntheticalDto dto) {
    UserEntity user = userService.getUserByToken(token);
    if (dto == null) throw new IllegalArgumentException("训练参数不能为空");
    if (dto.getId() != null && !dto.getId().isBlank()) {
      TelegraphKeyPatSyntheticalEntity existing = owned(token, dto.getId(), true);
      requireProtocol(existing);
      if (!Objects.equals(existing.getStatus(), NOT_STARTED.getStatus())) {
        throw new TerminalStateException("训练已开始，不能修改配置");
      }
      if (dto.getTitle() != null) existing.setTitle(dto.getTitle());
      return view(existing);
    }
    if (dto.getMessageType() == null || dto.getMessageType() < 0 || dto.getMessageType() > 2) {
      throw new IllegalArgumentException("训练报文类型无效");
    }
    JsonArray source = array(dto.getContent());
    if (source.isEmpty()) throw new IllegalArgumentException("训练报文不能为空");
    JsonArray initial = new JsonArray();
    for (JsonElement item : source) {
      String text = text(object(item).get("text"));
      if (text.isEmpty()) throw new IllegalArgumentException("训练报文不能为空");
      initial.add(row(text, "", false));
    }
    lockOwner(user.getId());
    TelegraphKeyPatSyntheticalEntity last = syntheticalDao.find("createUserId=?1 order by createTime desc,id desc", user.getId())
        .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
    if (last != null && Objects.equals(last.getStatus(), PAUSE.getStatus())) {
      if (Objects.equals(last.getProtocolVersion(), 1) && last.getSourceContent() != null && last.getAccumulatedActiveMillis() != null) {
        last.setStatus(FINISH.getStatus());
        syntheticalDao.flush();
        finishStatistical(last);
      } else {
        // Legacy paused rows have no authoritative capture timeline; supersede without recomputing history.
        last.setStatus(FINISH.getStatus());
        syntheticalDao.flush();
      }
    } else if (last != null && Objects.equals(last.getStatus(), NOT_STARTED.getStatus())) {
      syntheticalDao.delete(last);
    }
    TelegraphKeyPatSyntheticalEntity entity = new TelegraphKeyPatSyntheticalEntity()
        .setTitle(dto.getTitle()).setMessageType(dto.getMessageType()).setCreateUserId(user.getId())
        .setSpeed("0").setAccuracy(0.0).setDuration("0").setErrorNumber(0).setTotalNumber(initial.size())
        .setStatus(NOT_STARTED.getStatus()).setProtocolVersion(1).setAccumulatedActiveMillis(0L)
        .setContent(initial.toString()).setSourceContent(initial.toString());
    return view(syntheticalDao.save(entity));
  }

  @Transactional
  public TelegraphKeyPatSyntheticalVO begin(String token, String id) {
    TelegraphKeyPatSyntheticalEntity entity = owned(token, id, true);
    requireProtocol(entity);
    if (Objects.equals(entity.getStatus(), UNDERWAY.getStatus())) return view(entity);
    if (!Objects.equals(entity.getStatus(), NOT_STARTED.getStatus())) {
      throw new TerminalStateException("训练已开始或已完成，请读取当前状态");
    }
    entity.setStatus(UNDERWAY.getStatus()).setStartedAt(LocalDateTime.now());
    return view(entity);
  }

  @Transactional
  public TelegraphKeyPatSyntheticalVO stop(String token, TelegraphKeyPatSyntheticalDto dto) {
    TelegraphKeyPatSyntheticalEntity entity = owned(token, dto == null ? null : dto.getId(), true);
    requireProtocol(entity);
    JsonArray answers = answers(entity, dto.getContent());
    if (Objects.equals(entity.getStatus(), PAUSE.getStatus())) {
      requireSameAnswers(entity, answers);
      return view(entity);
    }
    requireRunning(entity);
    closeClock(entity);
    applyMetrics(entity, answers);
    entity.setStatus(PAUSE.getStatus());
    return view(entity);
  }

  @Transactional
  public TelegraphKeyPatSyntheticalVO goTo(String token, String id) {
    TelegraphKeyPatSyntheticalEntity entity = owned(token, id, true);
    requireProtocol(entity);
    if (Objects.equals(entity.getStatus(), UNDERWAY.getStatus())) return view(entity);
    if (!Objects.equals(entity.getStatus(), PAUSE.getStatus())) {
      throw new TerminalStateException("训练未暂停或已完成，不能继续");
    }
    entity.setStatus(UNDERWAY.getStatus()).setStartedAt(LocalDateTime.now());
    return view(entity);
  }

  @Transactional
  public TelegraphKeyPatSyntheticalVO finish(String token, TelegraphKeyPatSyntheticalDto dto) {
    TelegraphKeyPatSyntheticalEntity entity = owned(token, dto == null ? null : dto.getId(), true);
    requireProtocol(entity);
    JsonArray answers = answers(entity, dto.getContent());
    if (Objects.equals(entity.getStatus(), FINISH.getStatus())) {
      requireSameAnswers(entity, answers);
      return view(entity);
    }
    if (Objects.equals(entity.getStatus(), PAUSE.getStatus())) {
      requireSameAnswers(entity, answers);
    } else {
      requireRunning(entity);
      closeClock(entity);
      applyMetrics(entity, answers);
    }
    entity.setStatus(FINISH.getStatus());
    syntheticalDao.flush();
    finishStatistical(entity);
    return view(entity);
  }

  private void requireProtocol(TelegraphKeyPatSyntheticalEntity entity) {
    if (!Objects.equals(entity.getProtocolVersion(), 1) || entity.getSourceContent() == null
        || entity.getAccumulatedActiveMillis() == null) {
      throw new TerminalStateException("历史训练缺少权威时间轴，请新建训练；历史成绩保持不变");
    }
  }

  private void requireRunning(TelegraphKeyPatSyntheticalEntity entity) {
    if (Objects.equals(entity.getStatus(), FINISH.getStatus())) throw new TerminalStateException("训练已完成");
    if (!Objects.equals(entity.getStatus(), UNDERWAY.getStatus()) || entity.getStartedAt() == null) {
      throw new IllegalArgumentException("训练尚未开始或已暂停");
    }
  }

  private long elapsedMillis(TelegraphKeyPatSyntheticalEntity entity) {
    long elapsed = entity.getStartedAt() == null ? 0 : Duration.between(entity.getStartedAt(), LocalDateTime.now()).toMillis();
    if (elapsed < 0 || entity.getAccumulatedActiveMillis() < 0) throw new IllegalStateException("训练时间轴异常");
    return Math.addExact(entity.getAccumulatedActiveMillis(), elapsed);
  }

  private void closeClock(TelegraphKeyPatSyntheticalEntity entity) {
    entity.setAccumulatedActiveMillis(elapsedMillis(entity)).setStartedAt(null);
    entity.setDuration(Long.toString(entity.getAccumulatedActiveMillis() / 1000));
  }

  private JsonArray answers(TelegraphKeyPatSyntheticalEntity entity, String content) {
    JsonArray source = array(entity.getSourceContent());
    JsonArray submitted = array(content);
    if (source.size() != submitted.size()) throw new IllegalArgumentException("答案数量与报文不一致");
    JsonArray normalized = new JsonArray();
    for (int i = 0; i < source.size(); i++) {
      String expected = text(object(source.get(i)).get("text"));
      JsonObject answer = object(submitted.get(i));
      if (!expected.equals(text(answer.get("text")))) throw new IllegalArgumentException("报文源文本不匹配");
      String value = text(answer.get("value"));
      JsonElement focus = answer.get("isFocus");
      if (focus != null && (!focus.isJsonPrimitive() || !focus.getAsJsonPrimitive().isBoolean())) {
        throw new IllegalArgumentException("报文完成标记无效");
      }
      normalized.add(row(expected, value, !value.isEmpty() || focus != null && focus.getAsBoolean()));
    }
    return normalized;
  }

  private void requireSameAnswers(TelegraphKeyPatSyntheticalEntity entity, JsonArray answers) {
    if (!array(entity.getContent()).equals(answers)) throw new TerminalStateException("当前结果已保存，不能修改");
  }

  private void applyMetrics(TelegraphKeyPatSyntheticalEntity entity, JsonArray answers) {
    int errors = 0;
    int answered = 0;
    long characters = 0;
    for (JsonElement element : answers) {
      JsonObject answer = element.getAsJsonObject();
      String expected = answer.get("text").getAsString();
      String value = answer.get("value").getAsString();
      characters += Math.min(value.length(), expected.length());
      if (answer.get("isFocus").getAsBoolean()) {
        answered++;
        if (!expected.equals(value)) errors++;
      }
    }
    long millis = entity.getAccumulatedActiveMillis();
    BigDecimal speed = millis == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(characters).multiply(BigDecimal.valueOf(60000))
        .divide(BigDecimal.valueOf(millis), 2, RoundingMode.HALF_UP);
    entity.setContent(answers.toString()).setTotalNumber(answers.size()).setErrorNumber(errors)
        .setAccuracy(answered == 0 ? 0d : BigDecimal.valueOf((answered - errors) * 100L)
            .divide(BigDecimal.valueOf(answered), 2, RoundingMode.HALF_UP).doubleValue())
        .setSpeed(speed.toPlainString());
  }

  private JsonArray array(String json) {
    try {
      JsonArray parsed = JSONUtils.fromJson(json, JsonArray.class);
      if (parsed == null) throw new IllegalArgumentException("报文内容不能为空");
      return parsed;
    } catch (JsonParseException | IllegalStateException e) {
      throw new IllegalArgumentException("报文必须为有效的数组", e);
    }
  }

  private JsonObject object(JsonElement value) {
    if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("报文行必须为对象");
    return value.getAsJsonObject();
  }

  private String text(JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("报文和答案必须为文本");
    }
    return value.getAsString();
  }

  private JsonObject row(String text, String value, boolean focused) {
    JsonObject row = new JsonObject();
    row.addProperty("text", text);
    row.addProperty("value", value);
    row.addProperty("type", !focused || text.equals(value));
    row.addProperty("isFocus", focused);
    return row;
  }

  private void finishStatistical(TelegraphKeyPatSyntheticalEntity save) {
    Map<String, Object> totals = syntheticalDao.finishStatistical(save.getCreateUserId());
    TelegraphKeyTrainStatisticalEntity statistic = statisticalDao.find("userId=?1 and type=?2", save.getCreateUserId(), 2)
        .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
    if (statistic == null) statistic = new TelegraphKeyTrainStatisticalEntity().setUserId(save.getCreateUserId()).setType(2);
    statistic.setAvgSpeed((BigDecimal) totals.get("avgSpeed")).setTotalCount((Integer) totals.get("totalCount"))
        .setTotalTime((String) totals.get("totalTime"));
    statisticalDao.save(statistic);
  }

  private TelegraphKeyPatSyntheticalVO view(TelegraphKeyPatSyntheticalEntity entity) {
    if (entity == null) return null;
    TelegraphKeyPatSyntheticalVO result = PojoUtils.convertOne(entity, TelegraphKeyPatSyntheticalVO.class);
    if (result != null && Objects.equals(entity.getProtocolVersion(), 1) && entity.getAccumulatedActiveMillis() != null) {
      result.setDuration(Long.toString(elapsedMillis(entity) / 1000));
    }
    return result;
  }

  public TelegraphKeyPatSyntheticalVO findById(String token, TelegraphKeyPatSyntheticalDto dto) {
    return view(owned(token, dto == null ? null : dto.getId(), false));
  }

  public List<TelegraphKeyPatSyntheticalVO> findAll(String token) {
    String actor = userService.getUserByToken(token).getId();
    return syntheticalDao.findAllByCreateUserIdOrderByCreateTimeDesc(actor).stream().map(this::view).toList();
  }

  public TelegraphKeyPatSyntheticalVO lastTrain(String token) {
    return view(syntheticalDao.findLastTrain(userService.getUserByToken(token).getId()));
  }
}
