package com.nip.service;

import com.nip.common.constants.EnteringExerciseStatusEnum;
import com.nip.common.constants.EnteringExerciseTypeEnum;
import com.nip.common.utils.Assert;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.EnteringExerciseDao;
import com.nip.dao.EnteringStatisticalDao;
import com.nip.dao.UserDao;
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

  private final UserDao userDao;
  private final EnteringExerciseDao exerciseDao;
  private final EnteringStatisticalDao statisticalDao;
  private final EnteringExerciseWordStockService wordStockService;

  @Inject
  public EnteringExerciseService(EnteringExerciseDao exerciseDao, EnteringStatisticalDao statisticalDao, UserDao userDao, EnteringExerciseWordStockService wordStockService) {
    this.exerciseDao = exerciseDao;
    this.statisticalDao = statisticalDao;
    this.userDao = userDao;
    this.wordStockService = wordStockService;
  }

  @Transactional
  public EnteringExerciseVO add(EnteringExerciseAddParam addParam, String token) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
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
    //根据Type查询训练内容 content
    EnteringExerciseWordStockVO stockVO = wordStockService.findByType(entity.getType());
    entity.setContent(Optional.ofNullable(stockVO.getContent()).orElse("[]"));
    //查询上一次训练状态
    EnteringExerciseEntity lastTrain = exerciseDao.lastTrain(userEntity.getId(), addParam.getType());
    //如果是未开始则，删除
    if (lastTrain != null && lastTrain.getStatus().compareTo(EnteringExerciseStatusEnum.NOT_STARTED.getStatus()) == 0) {
      exerciseDao.deleteById(lastTrain.getId());
    } else if (lastTrain != null
        && lastTrain.getStatus().compareTo(EnteringExerciseStatusEnum.PAUSE.getStatus()) == 0) {
      lastTrain.setStatus(EnteringExerciseStatusEnum.FINISH.getStatus());
      EnteringExerciseEntity save = exerciseDao.save(lastTrain);
      finishStatistical(save);
    }
    EnteringExerciseEntity save = exerciseDao.save(entity);
    return PojoUtils.convertOne(save, EnteringExerciseVO.class);
  }

  public List<EnteringExerciseVO> listPage(EnteringExercisePageParam param, String token) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
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
  public void begin(EnteringExerciseUpdateParam param) {
    exerciseDao.begin(param.getId(), EnteringExerciseStatusEnum.UNDERWAY.getStatus());
  }

  @Transactional
  public void finish(EnteringExerciseFinishParam param) {
    EnteringExerciseEntity entity = PojoUtils.convertOne(exerciseDao.findById(param.getId()), EnteringExerciseEntity.class);
    entity.setStatus(EnteringExerciseStatusEnum.FINISH.getStatus());
    entity.setEndTime(LocalDateTime.now());
    EnteringExerciseEntity save = exerciseDao.save(entity);
    Assert.notNull(save, "未查询到该训练");
    finishStatistical(save);
  }

  public EnteringExerciseVO getById(String id) {
    EnteringExerciseEntity entity = exerciseDao.findById(id);
    return PojoUtils.convertOne(entity, EnteringExerciseVO.class);
  }

  public void goTo(EnteringExerciseUpdateParam param) {
    exerciseDao.goTo(param.getId(), EnteringExerciseStatusEnum.UNDERWAY.getStatus());
  }

  public void pause(EnteringExerciseFinishParam param) {
    EnteringExerciseEntity entity = PojoUtils.convertOne(exerciseDao.findById(param.getId()), EnteringExerciseEntity.class);
    entity.setStatus(EnteringExerciseStatusEnum.PAUSE.getStatus());
    exerciseDao.save(entity);
  }

  @Transactional
  public List<EnteringStatisticalVO> statisticalPage(String token, Integer type) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
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
    UserEntity userEntity = userDao.findUserEntityByToken(token);
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
