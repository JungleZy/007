package com.nip.service;

import com.nip.common.constants.TelegraphKeyPatSyntheticalEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.TelegraphKeyPatSyntheticalDao;
import com.nip.dao.TelegraphKeyTrainStatisticalDao;
import com.nip.dto.TelegraphKeyPatSyntheticalDto;
import com.nip.dto.vo.TelegraphKeyPatSyntheticalVO;
import com.nip.entity.TelegraphKeyPatSyntheticalEntity;
import com.nip.entity.TelegraphKeyTrainStatisticalEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.nip.common.constants.BaseConstants.TRAINING_NOT_FOUND;
import static com.nip.common.constants.TelegraphKeyPatSyntheticalEnum.*;

/**
 * @Author: wushilin
 * @Data: 2022-06-09 10:25
 * @Description:
 */
@ApplicationScoped
public class TelegraphKeyPatSyntheticalService {

  private final TelegraphKeyPatSyntheticalDao syntheticalDao;
  private final UserService userService;
  private final TelegraphKeyTrainStatisticalDao statisticalDao;

  @Inject
  public TelegraphKeyPatSyntheticalService(TelegraphKeyPatSyntheticalDao syntheticalDao, UserService userService, TelegraphKeyTrainStatisticalDao statisticalDao) {
    this.syntheticalDao = syntheticalDao;
    this.userService = userService;
    this.statisticalDao = statisticalDao;
  }

  @Transactional
  public TelegraphKeyPatSyntheticalVO save(String token, TelegraphKeyPatSyntheticalDto dto) {
    UserEntity userEntity = userService.getUserByToken(token);
    TelegraphKeyPatSyntheticalEntity entity = PojoUtils.convertOne(dto, TelegraphKeyPatSyntheticalEntity.class);
    entity.setCreateUserId(userEntity.getId());
    entity.setSpeed("0");
    entity.setAccuracy(0.0);
    entity.setDuration("0");
    entity.setErrorNumber(0);
    entity.setStatus(NOT_STARTED.getStatus());
    //查询最后一次训练记录
    TelegraphKeyPatSyntheticalEntity lastTrain = syntheticalDao.findLastTrain(userEntity.getId());
    if (lastTrain != null) {
      if (lastTrain.getStatus().compareTo(NOT_STARTED.getStatus()) == 0) {
        syntheticalDao.deleteById(lastTrain.getId());
      } else if (lastTrain.getStatus().compareTo(PAUSE.getStatus()) == 0) {
        lastTrain.setStatus(FINISH.getStatus());
        TelegraphKeyPatSyntheticalEntity save = syntheticalDao.save(lastTrain);
        finishStatistical(save);
      }
    }
    TelegraphKeyPatSyntheticalEntity save = syntheticalDao.save(entity);
    return PojoUtils.convertOne(save, TelegraphKeyPatSyntheticalVO.class);
  }

  public TelegraphKeyPatSyntheticalVO begin(String id) {
    TelegraphKeyPatSyntheticalEntity entity = Optional.ofNullable(syntheticalDao.findById(id))
        .orElseThrow(() -> new IllegalArgumentException(TRAINING_NOT_FOUND));
    entity.setStatus(UNDERWAY.getStatus());
    TelegraphKeyPatSyntheticalEntity save = syntheticalDao.save(entity);
    return PojoUtils.convertOne(save, TelegraphKeyPatSyntheticalVO.class);
  }

  public TelegraphKeyPatSyntheticalVO stop(TelegraphKeyPatSyntheticalDto dto) {
    TelegraphKeyPatSyntheticalEntity entity = Optional.ofNullable(syntheticalDao.findById(dto.getId()))
        .orElseThrow(() -> new IllegalArgumentException(TRAINING_NOT_FOUND));
    entity.setErrorNumber(dto.getErrorNumber());
    entity.setDuration(dto.getDuration());
    entity.setContent(dto.getContent());
    entity.setAccuracy(dto.getAccuracy());
    entity.setSpeed(dto.getSpeed());
    entity.setStatus(TelegraphKeyPatSyntheticalEnum.PAUSE.getStatus());
    TelegraphKeyPatSyntheticalEntity save = syntheticalDao.save(entity);
    return PojoUtils.convertOne(save, TelegraphKeyPatSyntheticalVO.class);
  }


  public TelegraphKeyPatSyntheticalVO goTo(String id) {
    TelegraphKeyPatSyntheticalEntity entity = Optional.ofNullable(syntheticalDao.findById(id))
        .orElseThrow(() -> new IllegalArgumentException(TRAINING_NOT_FOUND));
    entity.setStatus(UNDERWAY.getStatus());
    TelegraphKeyPatSyntheticalEntity save = syntheticalDao.save(entity);
    return PojoUtils.convertOne(save, TelegraphKeyPatSyntheticalVO.class);
  }


  @Transactional
  public TelegraphKeyPatSyntheticalVO finish(TelegraphKeyPatSyntheticalDto dto) {
    TelegraphKeyPatSyntheticalEntity entity = Optional.ofNullable(syntheticalDao.findById(dto.getId()))
        .orElseThrow(() -> new IllegalArgumentException(TRAINING_NOT_FOUND));
    entity.setStatus(FINISH.getStatus());
    entity.setTotalNumber(dto.getTotalNumber());
    entity.setSpeed(dto.getSpeed());
    //存储每次时长
    entity.setDuration(dto.getDuration());
    TelegraphKeyPatSyntheticalEntity save = syntheticalDao.save(entity);
    finishStatistical(save);
    return PojoUtils.convertOne(entity, TelegraphKeyPatSyntheticalVO.class);
  }

  /**
   * 完成训练后的统计
   *
   * @param: save
   */
  private void finishStatistical(TelegraphKeyPatSyntheticalEntity save) {
    //统计
    Map<String, Object> patSyntheticalEntity = syntheticalDao.finishStatistical(save.getCreateUserId());
    TelegraphKeyTrainStatisticalEntity statisticalEntity = JSONUtils.fromJson(
        JSONUtils.toJson(patSyntheticalEntity), TelegraphKeyTrainStatisticalEntity.class);
    TelegraphKeyTrainStatisticalEntity trainStatisticalEntity = statisticalDao.findByUserIdAndType(
        save.getCreateUserId(), 2);
    trainStatisticalEntity = Optional.ofNullable(trainStatisticalEntity).map(
            temp -> temp.setAvgSpeed(statisticalEntity.getAvgSpeed())
                .setTotalCount(statisticalEntity.getTotalCount())
                .setTotalTime(statisticalEntity.getTotalTime()))
        .orElse(new TelegraphKeyTrainStatisticalEntity()
            .setUserId(save.getCreateUserId())
            .setType(2)
            .setAvgSpeed(statisticalEntity.getAvgSpeed())
            .setTotalCount(statisticalEntity.getTotalCount())
            .setTotalTime(statisticalEntity.getTotalTime()));
    statisticalDao.save(trainStatisticalEntity);
  }

  public TelegraphKeyPatSyntheticalVO findById(TelegraphKeyPatSyntheticalDto dto) {
    TelegraphKeyPatSyntheticalEntity entity = Optional.ofNullable(syntheticalDao.findById(dto.getId()))
        .orElseThrow(() -> new IllegalArgumentException(TRAINING_NOT_FOUND));
    return PojoUtils.convertOne(entity, TelegraphKeyPatSyntheticalVO.class);
  }

  public List<TelegraphKeyPatSyntheticalVO> findAll(String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    List<TelegraphKeyPatSyntheticalEntity> entityList = syntheticalDao.findAllByCreateUserIdOrderByCreateTimeDesc(
        userEntity.getId());
    return PojoUtils.convert(entityList, TelegraphKeyPatSyntheticalVO.class);
  }

  public TelegraphKeyPatSyntheticalVO lastTrain(String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    TelegraphKeyPatSyntheticalEntity lastTrain = syntheticalDao.findLastTrain(userEntity.getId());

    return PojoUtils.convertOne(lastTrain, TelegraphKeyPatSyntheticalVO.class);
  }
}
