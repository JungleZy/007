package com.nip.service;

import com.nip.common.constants.EnteringExerciseTypeEnum;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.EnteringExerciseWordStockDao;
import com.nip.dto.vo.EnteringExerciseWordStockVO;
import com.nip.entity.EnteringExerciseWordStockEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 14:03
 * @Description:
 */
@ApplicationScoped
public class EnteringExerciseWordStockService {

  private final EnteringExerciseWordStockDao enteringExerciseWordStockDao;
  @Inject
  public EnteringExerciseWordStockService(EnteringExerciseWordStockDao enteringExerciseWordStockDao) {
    this.enteringExerciseWordStockDao = enteringExerciseWordStockDao;
  }

  public EnteringExerciseWordStockVO findByType(Integer type) {
    if (EnteringExerciseTypeEnum.WBLYCZ.getCode().compareTo(type) == 0) {
      type = EnteringExerciseTypeEnum.LYCZ.getCode();
    }
    EnteringExerciseWordStockEntity entity = enteringExerciseWordStockDao.findByType(type);
    if (entity == null) {
      return new EnteringExerciseWordStockVO();
    }
    return PojoUtils.convertOne(entity, EnteringExerciseWordStockVO.class);
  }
}
