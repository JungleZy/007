package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.TelegraphKeyPatTrainDao;
import com.nip.dao.TelegraphKeyTrainStatisticalDao;
import com.nip.dto.TelegraphKeyPatTrainDto;
import com.nip.dto.vo.TelegraphKeyPatTrainVO;
import com.nip.entity.TelegraphKeyPatTrainEntity;
import com.nip.entity.TelegraphKeyTrainStatisticalEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * @Author: wushilin
 * @Data: 2022-06-09 09:19
 * @Description:
 */
@ApplicationScoped
public class TelegraphKeyPatTrainService {
  private final TelegraphKeyPatTrainDao patTrainDao;
  private final TelegraphKeyTrainStatisticalDao statisticalDao;
  private final UserService userService;

  @Inject
  public TelegraphKeyPatTrainService(TelegraphKeyPatTrainDao patTrainDao, TelegraphKeyTrainStatisticalDao statisticalDao, UserService userService) {
    this.patTrainDao = patTrainDao;
    this.statisticalDao = statisticalDao;
    this.userService = userService;
  }

  /**
   * 保存电报键位训练信息
   *
   * @param token 用户令牌，用于获取当前操作用户
   * @param dto   包含训练信息的数据传输对象
   *              <p>
   *              此方法用于处理电报键位训练信息的保存操作，包括新训练的创建和已有训练的更新
   *              它首先根据用户令牌获取用户实体，然后根据提供的训练信息进行保存操作
   *              如果是新的训练，会检查是否存在相同类型的训练，如果存在，则抛出异常
   *              否则，将训练信息转换为实体并保存如果是要更新的训练，则获取训练实体并更新相关信息
   *              在保存训练实体后，还会调用另一个方法来保存相关的统计信息
   */
  @Transactional
  public void save(String token, TelegraphKeyPatTrainDto dto) {
    UserEntity userEntity = userService.getUserByToken(token);
    if (StringUtils.isEmpty(dto.getId())) {
      TelegraphKeyPatTrainEntity patTrainEntity = patTrainDao.findByCreateUserIdAndType(userEntity.getId(), dto.getType());
      if (patTrainEntity != null) {
        throw new IllegalArgumentException("相同类型训练已存在");
      }
      TelegraphKeyPatTrainEntity telegraphKeyPatTrainEntity = PojoUtils.convertOne(dto, TelegraphKeyPatTrainEntity.class, (d, e) -> e.setCreateUserId(userEntity.getId()));
      TelegraphKeyPatTrainEntity save = patTrainDao.save(telegraphKeyPatTrainEntity);
      saveStsatistical(dto, userEntity, save);
      return;
    }
    TelegraphKeyPatTrainEntity entity = Optional.ofNullable(patTrainDao.findById(dto.getId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    entity.setTotalTime(dto.getTotalTime())
        .setTotalNum(dto.getTotalNum())
        .setTotalError(dto.getTotalError());
    patTrainDao.save(entity);
    saveStsatistical(dto, userEntity, entity);
  }

  /**
   * 保存电报训练的统计信息
   * 此方法根据用户ID和训练类型来更新或创建电报训练的统计实体
   * 它计算平均速度，更新总训练次数和总训练时间
   *
   * @param dto        包含用户选择和训练信息的DTO
   * @param userEntity 用户实体，用于关联统计信息
   * @param entity     训练记录实体，从中提取统计数据
   */
  private void saveStsatistical(TelegraphKeyPatTrainDto dto, UserEntity userEntity, TelegraphKeyPatTrainEntity entity) {
    TelegraphKeyTrainStatisticalEntity statisticalEntity = statisticalDao.findByUserIdAndType(userEntity.getId(), dto.getType());
    statisticalEntity = Optional.ofNullable(statisticalEntity)
        .map(temp -> temp.setAvgSpeed(new BigDecimal(entity.getTotalNum())
                .divide(new BigDecimal(entity.getTotalTime()).divide(new BigDecimal(1000), 10, RoundingMode.HALF_UP), 10, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(60)).setScale(0, RoundingMode.HALF_UP))
            .setTotalCount(temp.getTotalCount() + 1)
            .setTotalTime(String.valueOf(entity.getTotalTime()))
        )
        .orElse(new TelegraphKeyTrainStatisticalEntity()
            .setUserId(entity.getCreateUserId())
            .setType(entity.getType())
            .setAvgSpeed(
                new BigDecimal(entity.getTotalNum())
                    .divide(new BigDecimal(entity.getTotalTime()), 10, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(60)).setScale(0, RoundingMode.HALF_UP))
            .setTotalCount(1)
            .setTotalTime(String.valueOf(entity.getTotalTime())));
    statisticalDao.save(statisticalEntity);
  }

  @Transactional
  public TelegraphKeyPatTrainVO findByUserIdAndType(String token, Integer type) {
    UserEntity userEntity = userService.getUserByToken(token);
    TelegraphKeyPatTrainEntity entity = patTrainDao.findByCreateUserIdAndType(userEntity.getId(), type);
    entity = Optional.ofNullable(entity)
        .orElse(new TelegraphKeyPatTrainEntity()
            .setTotalError(0)
            .setTotalNum(0)
            .setTotalTime(0)
            .setType(type));
    return PojoUtils.convertOne(entity, TelegraphKeyPatTrainVO.class);
  }

  @Transactional
  public TelegraphKeyPatTrainVO clear(String token, Integer type) {
    UserEntity userEntity = userService.getUserByToken(token);
    TelegraphKeyPatTrainEntity entity = Optional.ofNullable(patTrainDao.findByCreateUserIdAndType(userEntity.getId(), type))
        .orElseGet(TelegraphKeyPatTrainEntity::new);
    entity.setTotalError(0)
        .setTotalNum(0)
        .setTotalTime(0);

    TelegraphKeyPatTrainEntity save = patTrainDao.save(entity);
    //清除训练次数
    TelegraphKeyTrainStatisticalEntity statisticalEntity = statisticalDao.findByUserIdAndType(userEntity.getId(), type);
    if (statisticalEntity != null) {
      statisticalEntity.setTotalCount(0);
    }
    statisticalDao.save(statisticalEntity);
    return PojoUtils.convertOne(save, TelegraphKeyPatTrainVO.class);
  }
}
