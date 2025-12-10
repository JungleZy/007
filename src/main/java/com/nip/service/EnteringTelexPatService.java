package com.nip.service;

import com.nip.common.utils.Assert;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.EnteringStatisticalDao;
import com.nip.dao.EnteringTelexPatDao;
import com.nip.dao.UserDao;
import com.nip.dto.vo.EnteringTelexPatVO;
import com.nip.dto.vo.param.EnteringTelexPatQueryParam;
import com.nip.dto.vo.param.EnteringTelexPatSaveParam;
import com.nip.entity.EnteringStatisticalEntity;
import com.nip.entity.EnteringTelexPatEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * @Author: wushilin
 * @Data: 2022-04-21 10:16
 * @Description:
 */
@ApplicationScoped
public class EnteringTelexPatService {

  private final EnteringTelexPatDao telexPatDao;
  private final UserDao userDao;
  private final EnteringStatisticalDao statisticalDao;

  @Inject
  public EnteringTelexPatService(EnteringTelexPatDao telexPatDao, UserDao userDao, EnteringStatisticalDao statisticalDao) {
    this.telexPatDao = telexPatDao;
    this.userDao = userDao;
    this.statisticalDao = statisticalDao;
  }

  @Transactional
  public EnteringTelexPatVO save(String token, EnteringTelexPatSaveParam param) {
    //从token中获取用户
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    //如果用户id为空，则需要校验数据库中同一用户是否存在同一类型的记录
    if (Objects.isNull(param.getId())) {
      EnteringTelexPatEntity check = telexPatDao.findByCreateUserIdAndType(userEntity.getId(), param.getType());
      Assert.notNull(check, "您已存在相同类型的训练，不能再添加同类的训练！");
    }
    //根据id和类型查询
    EnteringTelexPatEntity entity = telexPatDao.findByIdAndType(param.getId(), param.getType());
    if (entity == null) {
      entity = PojoUtils.convertOne(param, EnteringTelexPatEntity.class);
      entity.setCreateUserId(userEntity.getId());
    } else {
      entity.setTotalError(param.getTotalError());
      entity.setTotalNum(param.getTotalNum());
      entity.setTotalTime(param.getTotalTime());
      entity.setCreateUserId(userEntity.getId());
      entity.setMessageName(param.getMessageName());
    }
    EnteringTelexPatEntity save = telexPatDao.save(entity);
    //根据类型查询统计记录
    EnteringStatisticalEntity queryStatisticalEntity = statisticalDao.findByUserIdAndTypeAndChildType(
        userEntity.getId(), 1, save.getType());
    queryStatisticalEntity = Optional.ofNullable(queryStatisticalEntity).orElse(
        new EnteringStatisticalEntity()
            .setUserId(userEntity.getId())
            .setType(1)
            .setChildType(save.getType())
            .setAvgSpeed(new BigDecimal(0))
            .setTotalCount(0)
            .setTotalTime("0"));
    queryStatisticalEntity.setTotalTime(String.valueOf(save.getTotalTime()));
    queryStatisticalEntity.setTotalCount(queryStatisticalEntity.getTotalCount() + 1);
    //计算平均速率=拍发总次数/时长(秒)*60
    BigDecimal avgSpeed = new BigDecimal(save.getTotalNum()).divide(
        new BigDecimal(save.getTotalTime()), 10, RoundingMode.HALF_UP).multiply(new BigDecimal(60)).setScale(0, RoundingMode.HALF_UP);
    queryStatisticalEntity.setAvgSpeed(avgSpeed);
    statisticalDao.save(queryStatisticalEntity);
    return PojoUtils.convertOne(save, EnteringTelexPatVO.class);
  }

  public EnteringTelexPatVO findByUserIdAndType(String token, Integer type) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    EnteringTelexPatEntity entity = telexPatDao.findByCreateUserIdAndType(userEntity.getId(), type);
    return Optional.ofNullable(entity).map(e -> PojoUtils.convertOne(e, EnteringTelexPatVO.class))
        .orElse(new EnteringTelexPatVO()
            .setTotalError(0)
            .setTotalNum(0)
            .setTotalTime(0)
            .setType(type));
  }

  @Transactional
  public EnteringTelexPatVO clear(EnteringTelexPatQueryParam param, String token) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    EnteringTelexPatEntity entity = telexPatDao.findByCreateUserIdAndType(userEntity.getId(), param.getType());
    if (entity != null) {
      entity.setTotalTime(0);
      entity.setTotalError(0);
      entity.setTotalNum(0);
      EnteringTelexPatEntity save = telexPatDao.save(entity);
      return PojoUtils.convertOne(save, EnteringTelexPatVO.class);
    }
    return new EnteringTelexPatVO()
        .setTotalError(0)
        .setTotalNum(0)
        .setTotalTime(0)
        .setType(param.getType());
  }
}
