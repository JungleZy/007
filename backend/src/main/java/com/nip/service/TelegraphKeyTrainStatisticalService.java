package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.TelegraphKeyTrainStatisticalDao;
import com.nip.dto.vo.TelegraphKeyTrainStatisticalVO;
import com.nip.entity.TelegraphKeyTrainStatisticalEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author: wushilin
 * @Data: 2022-06-09 11:59
 * @Description:
 */
@ApplicationScoped
public class TelegraphKeyTrainStatisticalService {

  private final TelegraphKeyTrainStatisticalDao statisticalDao;
  private final UserService userService;

  @Inject
  public TelegraphKeyTrainStatisticalService(TelegraphKeyTrainStatisticalDao statisticalDao, UserService userService) {
    this.statisticalDao = statisticalDao;
    this.userService = userService;
  }

  /**
   * 根据用户令牌统计电报键训练信息
   * 此方法首先通过用户服务获取与令牌关联的用户实体，然后从数据库中检索与用户ID关联的统计实体列表
   * 如果某些类型的统计信息缺失（总共需要3种类型），则创建并保存缺失类型的初始统计实体
   * 最后，将实体列表转换为VO列表，并按类型排序后返回
   *
   * @param token 用户令牌，用于识别和获取用户信息
   * @return 包含电报键训练统计信息的列表
   */
  @Transactional
  public List<TelegraphKeyTrainStatisticalVO> statisticalPage(String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    List<TelegraphKeyTrainStatisticalEntity> entities = statisticalDao.findByUserId(userEntity.getId());
    Map<Integer, List<TelegraphKeyTrainStatisticalEntity>> collect = entities.stream().collect(
        Collectors.groupingBy(TelegraphKeyTrainStatisticalEntity::getType));
    for (int i = 0; i < 3; i++) {
      if (collect.get(i) == null) {
        TelegraphKeyTrainStatisticalEntity entity = new TelegraphKeyTrainStatisticalEntity()
            .setTotalTime("0")
            .setAvgSpeed(BigDecimal.ZERO)
            .setTotalCount(0)
            .setType(i)
            .setUserId(userEntity.getId());
        TelegraphKeyTrainStatisticalEntity save = statisticalDao.save(entity);
        entities.add(save);
      }
    }
    List<TelegraphKeyTrainStatisticalVO> convert = PojoUtils.convert(entities, TelegraphKeyTrainStatisticalVO.class);
    convert.sort(Comparator.comparingInt(TelegraphKeyTrainStatisticalVO::getType));
    return convert;
  }
}
