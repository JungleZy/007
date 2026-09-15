package com.nip.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nip.common.exception.TerminalStateException;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.util.Objects;
import com.nip.common.constants.EnteringExerciseStatusEnum;
import com.nip.common.constants.EnteringExerciseTypeEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.EnteringExerciseDao;
import com.nip.dao.EnteringStatisticalDao;
import com.nip.dto.vo.EnteringExerciseVO;
import com.nip.dto.vo.EnteringExerciseWordStockVO;
import com.nip.dto.vo.EnteringStatisticalVO;
import com.nip.dto.vo.param.EnteringExerciseAddParam;
import com.nip.dto.vo.param.EnteringExerciseFinishParam;
import com.nip.dto.vo.param.EnteringExercisePageParam;
import com.nip.dto.vo.param.EnteringExerciseUpdateParam;
import com.nip.entity.EnteringExerciseEntity;
import com.nip.entity.EnteringStatisticalEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 09:46
 * @Description:
 */
@ApplicationScoped
public class EnteringExerciseService {

  private final UserService userService;
  private final EnteringExerciseDao exerciseDao;
  private final EnteringStatisticalDao statisticalDao;
  private final EnteringExerciseWordStockService wordStockService;
  private final TrainWriteAccess trainWriteAccess;

  @Inject
  public EnteringExerciseService(EnteringExerciseDao exerciseDao, EnteringStatisticalDao statisticalDao, UserService userService, EnteringExerciseWordStockService wordStockService, TrainWriteAccess trainWriteAccess) {
    this.exerciseDao = exerciseDao;
    this.statisticalDao = statisticalDao;
    this.userService = userService;
    this.wordStockService = wordStockService;
    this.trainWriteAccess = trainWriteAccess;
  }

  @Transactional
  public EnteringExerciseVO add(EnteringExerciseAddParam addParam, String token) {
    // DATA-03：走 userService.getUserByToken，token 失效时抛 UnauthorizedException（200+code203），不再裸解引用 NPE
    UserEntity userEntity = userService.getUserByToken(token);
    if (addParam.getType() == null) throw new IllegalArgumentException("训练类型不能为空");
    EnteringExerciseEntity entity = new EnteringExerciseEntity();
    entity.setCreateUserId(userEntity.getId());
    entity.setName(addParam.getName());
    entity.setType(addParam.getType());
    entity.setStatus(EnteringExerciseStatusEnum.NOT_STARTED.getStatus());
    entity.setSpeed(0);
    entity.setAccuracy(0.0);
    entity.setDuration(0);
    entity.setCorrectNum(0);
    entity.setErrorNum(0);
    EnteringExerciseWordStockVO stockVO = wordStockService.findByType(entity.getType());
    JsonArray source = EnteringCodebook.freeze(entity.getType(), stockVO.getContent());
    entity.setProtocolVersion(1);
    entity.setSourceContent(source.toString());
    JsonArray empty = new JsonArray();
    for (int i = 0; i < source.size(); i++) {
      JsonObject row = new JsonObject();
      row.addProperty("value", "");
      empty.add(row);
    }
    entity.setContent(EnteringCodebook.capture(source, empty).toString());
    EnteringExerciseEntity save = exerciseDao.save(entity);
    return PojoUtils.convertOne(save, EnteringExerciseVO.class);
  }

  public List<EnteringExerciseVO> listPage(EnteringExercisePageParam param, String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    String sql;
    if (param.getType() == 0) {
      sql = "type <= ?1 and createUserId = ?2 order by createTime desc";
    } else {
      sql = "type > ?1 and createUserId = ?2 order by createTime desc";
    }
    List<EnteringExerciseEntity> entityPage = exerciseDao.find(sql, param.getType(), userEntity.getId()).list();

    return PojoUtils.convert(entityPage, EnteringExerciseVO.class);
  }

  @Transactional(rollbackOn = Exception.class)
  public void begin(EnteringExerciseUpdateParam param, String token) {
    start(param.getId(), token, false);
  }

  @Transactional(rollbackOn = Exception.class)
  public void goTo(EnteringExerciseUpdateParam param, String token) {
    start(param.getId(), token, true);
  }

  private void start(String id, String token, boolean resume) {
    EnteringExerciseEntity entity = owned(id, token, true);
    requireProtocol(entity);
    if (Objects.equals(entity.getStatus(), 2)) throw new TerminalStateException("训练已结束");
    if (Objects.equals(entity.getStatus(), 1)) return;
    if (!Objects.equals(entity.getStatus(), resume ? 3 : 0)) {
      throw new IllegalArgumentException(resume ? "训练不处于暂停状态" : "请继续已开始的训练");
    }
    LocalDateTime now = LocalDateTime.now();
    if (entity.getStartTime() == null) entity.setStartTime(now);
    entity.setActiveStartedAt(now);
    entity.setStatus(1);
  }

  @Transactional(rollbackOn = Exception.class)
  public EnteringExerciseVO finish(EnteringExerciseFinishParam param, String token) {
    return saveAnswers(param, token, true);
  }

  @Transactional(rollbackOn = Exception.class)
  public EnteringExerciseVO pause(EnteringExerciseFinishParam param, String token) {
    return saveAnswers(param, token, false);
  }

  private EnteringExerciseVO saveAnswers(EnteringExerciseFinishParam param, String token, boolean finish) {
    EnteringExerciseEntity entity = owned(param.getId(), token, true);
    requireProtocol(entity);
    if (Objects.equals(entity.getStatus(), 2)) {
      if (finish) {
        try {
          JsonArray retry = EnteringCodebook.capture(EnteringCodebook.array(entity.getSourceContent()), EnteringCodebook.array(param.getContent()));
          if (retry.toString().equals(entity.getContent())) return PojoUtils.convertOne(entity, EnteringExerciseVO.class);
        } catch (IllegalArgumentException ignored) {
          throw new TerminalStateException("训练已结束，不能修改结果");
        }
      }
      throw new TerminalStateException("训练已结束，不能修改结果");
    }
    JsonArray captured = EnteringCodebook.capture(EnteringCodebook.array(entity.getSourceContent()),
        EnteringCodebook.array(param.getContent()));
    if (!Objects.equals(entity.getStatus(), 1) && !Objects.equals(entity.getStatus(), 3)) {
      throw new IllegalArgumentException("训练尚未开始");
    }
    if (Objects.equals(entity.getStatus(), 3) && !captured.toString().equals(entity.getContent())) {
      throw new IllegalArgumentException("暂停期间不能修改录入内容，请先继续训练");
    }
    LocalDateTime now = LocalDateTime.now();
    long elapsed = elapsedMillis(entity, now);
    int correct = 0;
    int errors = 0;
    for (var item : captured) {
      JsonObject row = item.getAsJsonObject();
      if (row.get("isFocus").getAsBoolean()) {
        if (row.get("trueOrfalse").getAsBoolean()) correct++; else errors++;
      }
    }
    entity.setElapsedMillis(elapsed);
    entity.setActiveStartedAt(null);
    entity.setDuration((int) Math.min(Integer.MAX_VALUE, elapsed / 1000));
    entity.setCorrectNum(correct);
    entity.setErrorNum(errors);
    entity.setAccuracy(correct + errors == 0 ? 0.0 : Math.round(correct * 10000.0 / (correct + errors)) / 100.0);
    entity.setSpeed((int) Math.min(Integer.MAX_VALUE, (correct + errors) * 60000L / Math.max(1, elapsed)));
    entity.setContent(captured.toString());
    entity.setStatus(finish ? 2 : 3);
    if (finish) {
      entity.setEndTime(now);
      exerciseDao.save(entity);
      exerciseDao.flush();
      finishStatistical(entity);
    }
    return PojoUtils.convertOne(entity, EnteringExerciseVO.class);
  }

  public EnteringExerciseVO getById(String id, String token) {
    EnteringExerciseEntity entity = owned(id, token, false);
    if (!Objects.equals(entity.getStatus(), 2)) requireProtocol(entity);
    EnteringExerciseVO result = PojoUtils.convertOne(entity, EnteringExerciseVO.class);
    if (Objects.equals(entity.getProtocolVersion(), 1)) {
      result.setDuration((int) Math.min(Integer.MAX_VALUE, elapsedMillis(entity, LocalDateTime.now()) / 1000));
    }
    return result;
  }

  private long elapsedMillis(EnteringExerciseEntity entity, LocalDateTime now) {
    long elapsed = Optional.ofNullable(entity.getElapsedMillis()).orElse(0L);
    if (Objects.equals(entity.getStatus(), 1)) {
      if (entity.getActiveStartedAt() == null) throw new IllegalStateException("活动训练缺少计时起点");
      elapsed = Math.addExact(elapsed, Math.max(0, Duration.between(entity.getActiveStartedAt(), now).toMillis()));
    }
    return elapsed;
  }

  private void requireProtocol(EnteringExerciseEntity entity) {
    if (!Objects.equals(entity.getProtocolVersion(), 1)) {
      throw new TerminalStateException("旧版练习仅可查看已完成记录，请重新创建训练");
    }
  }

  private EnteringExerciseEntity owned(String id, String token, boolean lock) {
    UserEntity actor = userService.getUserByToken(token);
    EnteringExerciseEntity entity = lock ? exerciseDao.findById(id, LockModeType.PESSIMISTIC_WRITE) : exerciseDao.findById(id);
    if (entity == null) throw new IllegalArgumentException("未查询到该训练");
    trainWriteAccess.requireTrainOwner(actor.getId(), entity.getCreateUserId(), "汉字录入训练 " + id);
    return entity;
  }

  @Transactional
  public List<EnteringStatisticalVO> statisticalPage(String token, Integer type) {
    UserEntity userEntity = userService.getUserByToken(token);
    List<EnteringStatisticalEntity> entities = statisticalDao.findByUserIdAndType(userEntity.getId(), type);
    Map<Integer, List<EnteringStatisticalEntity>> collect = entities.stream().collect(
        Collectors.groupingBy(EnteringStatisticalEntity::getChildType));
    int mark = 3;
    if (type != 0) {
      mark = 4;
    }
    for (int i = 0; i < mark; i++) {
      if (collect.get(i) == null) {
        EnteringStatisticalEntity entity = new EnteringStatisticalEntity()
            .setUserId(userEntity.getId())
            .setType(type)
            .setChildType(i)
            .setTotalCount(0)
            .setTotalTime("0")
            .setAvgSpeed(new BigDecimal(0));
        EnteringStatisticalEntity save = statisticalDao.save(entity);
        entities.add(save);
      }
    }
    List<EnteringStatisticalVO> convert = PojoUtils.convert(entities, EnteringStatisticalVO.class, (t, v) -> v.setType(t.getChildType()));
    convert.sort(Comparator.comparingInt(EnteringStatisticalVO::getType));
    return convert;
  }

  public EnteringExerciseVO lastTrain(String token, Integer type) {
    UserEntity userEntity = userService.getUserByToken(token);
    EnteringExerciseEntity entity = exerciseDao.lastTrain(userEntity.getId(), type);
    if (entity == null) {
      return null;
    }
    return PojoUtils.convertOne(entity, EnteringExerciseVO.class);
  }

  /**
   * 完成训练的数据统计
   *
   * @param: entity
   */
  @Transactional
  public void finishStatistical(EnteringExerciseEntity entity) {
    //统计
    Map<String, Object> map = exerciseDao.finishStatistical(entity.getCreateUserId(), entity.getType());
    //先查询根据用户id和类型查询数据库中是否有数据
    EnteringStatisticalEntity statisticalEntity = JSONUtils.fromJson(
        JSONUtils.toJson(map), EnteringStatisticalEntity.class);
    int type = 0;
    Integer childType = entity.getType();
    if (entity.getType().compareTo(EnteringExerciseTypeEnum.WBLYCZ.getCode()) == 0) {
      type = 1;
      childType = 3;
    }
    EnteringStatisticalEntity queryStatisticalEntity = statisticalDao.findByUserIdAndTypeAndChildType(
        entity.getCreateUserId(), type, childType);

    queryStatisticalEntity = Optional.ofNullable(queryStatisticalEntity).map(
        temp -> temp.setAvgSpeed(statisticalEntity.getAvgSpeed()).setTotalCount(statisticalEntity.getTotalCount())
            .setTotalTime(statisticalEntity.getTotalTime())).orElse(
        new EnteringStatisticalEntity()
            .setUserId(entity.getCreateUserId())
            .setType(type)
            .setChildType(childType)
            .setAvgSpeed(statisticalEntity.getAvgSpeed())
            .setTotalCount(statisticalEntity.getTotalCount())
            .setTotalTime(statisticalEntity.getTotalTime()));
    statisticalDao.save(queryStatisticalEntity);
  }
}
