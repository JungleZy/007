package com.nip.service;

import com.nip.common.constants.TelexPatTrainEnum;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.TelexPatDao;
import com.nip.dao.TelexPatTrainDao;
import com.nip.dao.TelexPatTrainStatisticalDao;
import com.nip.dto.vo.TelexPatTrainStatisticalVO;
import com.nip.entity.TelexPatTrainEntity;
import com.nip.entity.TelexPatTrainStatisticalEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.nip.common.constants.TelexPatTrainStatisticalTypeEnum.WORD;

/**
 * @Author: wushilin
 * @Data: 2022-06-01 16:39
 * @Description:
 */
@ApplicationScoped
public class TelexPatTrainStatisticalService {
  private final TelexPatTrainStatisticalDao statisticalDao;
  private final TelexPatTrainDao trainDao;
  private final TelexPatDao patDao;
  private final UserService userService;

  @Inject
  public TelexPatTrainStatisticalService(TelexPatTrainStatisticalDao statisticalDao, TelexPatTrainDao trainDao, TelexPatDao patDao, UserService userService) {
    this.statisticalDao = statisticalDao;
    this.trainDao = trainDao;
    this.patDao = patDao;
    this.userService = userService;
  }

  /**
   * 根据用户ID和类型进行电报训练统计
   *
   * @param userId         用户ID，用于标识用户
   * @param type           训练类型，用于区分不同的训练模式
   * @param patTrainEntity 训练实体，包含训练的相关信息
   */
  public void statistical(String userId, Integer type, TelexPatTrainEntity patTrainEntity) {
    TelexPatTrainStatisticalEntity statistical = statisticalDao.getByUserIdAndType(userId, type);
    if (statistical == null && (type.compareTo(WORD.getType()) != 0)) {
      initStatistical(userId, type - 1);
      return;
    }
    if (type.compareTo(WORD.getType()) == 0) {
      if (statistical == null) {
        statistical = new TelexPatTrainStatisticalEntity();
        statistical.setTotalTime("0");
        statistical.setTotalCount(0);
        statistical.setType(WORD.getType());
      }
      statistical.setUserId(userId);
      //由于单子训练传入的时间是总时间，所以需要从数据库中计算统计
      Long duration = patDao.sumDurationByUserId(userId, type);
      statistical.setTotalTime(String.valueOf(duration));
    } else {
      //平均速率
      BigDecimal avgSpeed = trainDao.getAvgSpeed(userId, patTrainEntity.getType());
      assert statistical != null;
      statistical.setAvgSpeed(avgSpeed);
      //时长
      Integer duration = Integer.valueOf(patTrainEntity.getDuration());
      Integer totalTime = Integer.valueOf(statistical.getTotalTime());
      statistical.setTotalTime(String.valueOf(duration + totalTime));
    }
    //次数
    statistical.setTotalCount(statistical.getTotalCount() + 1);
    statisticalDao.save(statistical);
  }

  /**
   * 根据用户令牌统计信息分页查询
   * 此方法使用了事务注解，确保数据一致性
   *
   * @param token 用户令牌，用于识别用户
   * @return 返回统计信息的列表
   */
  @Transactional
  public List<TelexPatTrainStatisticalVO> statisticalPage(String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    List<TelexPatTrainStatisticalEntity> entities = statisticalDao.findAllByUserId(userEntity.getId());
    if (entities.size() != 4) {
      Map<Integer, List<TelexPatTrainStatisticalEntity>> listMap = entities.stream()
          .collect(Collectors.groupingBy(TelexPatTrainStatisticalEntity::getType));
      for (int i = 0; i < 4; i++) {
        if (listMap.get(i) == null) {
          TelexPatTrainStatisticalEntity entity = new TelexPatTrainStatisticalEntity();
          entity.setUserId(userEntity.getId());
          entity.setType(i);
          entity.setTotalCount(0);
          entity.setAvgSpeed(BigDecimal.ZERO);
          entity.setTotalTime("0");
          statisticalDao.save(entity);
          entities.add(entity);
        }
      }
    }
    return PojoUtils.convert(statisticalDao.findAllByUserId(userEntity.getId()), TelexPatTrainStatisticalVO.class);
  }

  /**
   * 初始化统计信息方法
   * 根据用户ID和类型统计训练信息的总时长、总次数和平均速率
   *
   * @param userId 用户ID，用于查询该用户的相关训练记录
   * @param type 类型，表示需要统计的训练类型
   */
  private void initStatistical(String userId, Integer type) {
    TelexPatTrainStatisticalEntity statistical;
    statistical = new TelexPatTrainStatisticalEntity();
    //当statistical 是空的时候全部统计一次
    List<TelexPatTrainEntity> patTrainList =
        trainDao.findAllByCreateUserIdAndTypeAndStatus(userId, type, TelexPatTrainEnum.FINISH.getStatus());
    //统计总时长和总速率
    Integer totalTime = 0;
    BigDecimal totalSpeed = new BigDecimal(0);
    for (TelexPatTrainEntity entity : patTrainList) {
      totalTime = totalTime + Integer.parseInt(entity.getDuration());
      totalSpeed = totalSpeed.add(new BigDecimal(entity.getSpeed()));
    }
    //计算平均速率
    BigDecimal avgSpeed = patTrainList.isEmpty() ?
        new BigDecimal("0.00") :
        totalSpeed.divide(new BigDecimal(patTrainList.size()), 0, RoundingMode.HALF_UP);

    statistical.setTotalTime(String.valueOf(totalTime));
    statistical.setTotalCount(patTrainList.size());
    statistical.setAvgSpeed(avgSpeed);
    statistical.setUserId(userId);
    //这里的type需要做转换
    statistical.setType(type + 1);
    statisticalDao.saveAndFlush(statistical);
  }
}
