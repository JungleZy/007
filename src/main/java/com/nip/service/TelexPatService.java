package com.nip.service;

import com.nip.common.constants.TelexPatTrainStatisticalTypeEnum;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.TelexPatDao;
import com.nip.dao.TelexPatTrainStatisticalDao;
import com.nip.entity.TelexPatEntity;
import com.nip.entity.TelexPatTrainEntity;
import com.nip.entity.TelexPatTrainStatisticalEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Objects;

import static com.nip.common.constants.TelexPatTrainStatisticalTypeEnum.WORD;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/3/21 17:48
 */
@Slf4j
@ApplicationScoped
public class TelexPatService {
  private final TelexPatDao telexPatDao;
  private final UserService userService;
  private final TelexPatTrainStatisticalDao statisticalDao;
  private final TelexPatTrainStatisticalService statisticalService;

  @Inject
  public TelexPatService(TelexPatDao telexPatDao, UserService userService, TelexPatTrainStatisticalDao statisticalDao, TelexPatTrainStatisticalService statisticalService) {
    this.telexPatDao = telexPatDao;
    this.userService = userService;
    this.statisticalDao = statisticalDao;
    this.statisticalService = statisticalService;
  }

  @Transactional
  public Response<TelexPatEntity> saveTelexPat(String token, int count, int mistake, int type, String dur) {
    try {
      UserEntity userEntity = userService.getUserByToken(token);
      TelexPatEntity allByUserIdAndType = telexPatDao.findAllByUserIdAndType(userEntity.getId(), type);
      if (Objects.isNull(allByUserIdAndType)) {
        allByUserIdAndType = new TelexPatEntity();
        allByUserIdAndType.setType(type);
        allByUserIdAndType.setUserId(userEntity.getId());
      }
      allByUserIdAndType.setCount(count);
      allByUserIdAndType.setMistake(mistake);
      allByUserIdAndType.setDuration(Long.valueOf(dur));
      TelexPatEntity save = telexPatDao.save(allByUserIdAndType);
      statisticalService.statistical(userEntity.getId(), WORD.getType(),
          PojoUtils.convertOne(save, TelexPatTrainEntity.class)
      );
      return ResponseResult.success(save);
    } catch (Exception e) {
      log.error("saveTelexPat：{}", e.getMessage());
      return ResponseResult.error();
    }
  }

  public Response<TelexPatEntity> findById(String token, int type) {
    try {
      UserEntity userEntity = userService.getUserByToken(token);
      return ResponseResult.success(telexPatDao.findAllByUserIdAndType(userEntity.getId(), type));
    } catch (Exception e) {
      log.error("findById：{}", e.getMessage());
      return ResponseResult.error();
    }
  }

  @Transactional
  public Response<Void> deleteTexPatByToken(String token, Integer type) {
    try {
      UserEntity userEntity = userService.getUserByToken(token);
      telexPatDao.deleteByUserIdAndType(userEntity.getId(), type);
      //清除统计信息
      TelexPatTrainStatisticalEntity statisticalEntity = statisticalDao.findByUserIdAndType(userEntity.getId(), type);
      statisticalEntity.setTotalTime("0");
      statisticalEntity.setTotalCount(0);
      statisticalEntity.setAvgSpeed(BigDecimal.ZERO);
      statisticalDao.save(statisticalEntity);
      return ResponseResult.success();
    } catch (Exception e) {
      log.error("deleteTexPatByToken：{}", e.getMessage());
      return ResponseResult.error();
    }
  }
}