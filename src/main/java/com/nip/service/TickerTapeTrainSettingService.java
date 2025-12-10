package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.TickerTapeTrainSettingDao;
import com.nip.dto.vo.TickerTapeTrainSettingVO;
import com.nip.dto.vo.param.TickerTapeTrainSettingAddParam;
import com.nip.entity.TickerTapeTrainSettingEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 15:52
 * @Description:
 */
@ApplicationScoped
public class TickerTapeTrainSettingService {
  private final TickerTapeTrainSettingDao tickerTapeTrainSettingDao;

  // 使用构造器注入
  @Inject
  public TickerTapeTrainSettingService(TickerTapeTrainSettingDao tickerTapeTrainSettingDao) {
    this.tickerTapeTrainSettingDao = tickerTapeTrainSettingDao;
  }

  /**
   * 获取所有的TickerTapeTrainSettingVO对象
   *
   * @return 包含所有TickerTapeTrainSettingVO对象的列表
   */
  public List<TickerTapeTrainSettingVO> findAll() {
    // 从数据库中获取所有设置实体
    List<TickerTapeTrainSettingEntity> entity = tickerTapeTrainSettingDao.findAll().list();
    // 将实体列表转换为VO列表并返回
    return PojoUtils.convert(entity, TickerTapeTrainSettingVO.class);
  }

  /**
   * 添加或更新TickerTapeTrainSettingAddParam对象
   * 此方法首先删除所有现有的设置，然后保存新的设置参数
   *
   * @param addParams 要添加或更新的设置参数
   * @return 返回添加或更新的设置参数
   */
  @Transactional
  public TickerTapeTrainSettingAddParam addOrUpdate(TickerTapeTrainSettingAddParam addParams) {
    // 删除所有现有的设置
    tickerTapeTrainSettingDao.deleteAll();
    // 将添加参数列表转换为实体列表
    List<TickerTapeTrainSettingEntity> entityList = PojoUtils.convert(addParams.getParamList(), TickerTapeTrainSettingEntity.class);
    // 为每个实体设置点标准时间
    entityList.forEach(item -> item.setDot(addParams.getDotStandardTime()));
    // 保存新的设置实体列表
    tickerTapeTrainSettingDao.save(entityList);
    // 返回添加或更新的参数
    return addParams;
  }

  /**
   * 获取点标准费率
   *
   * @return 返回第一个设置实体的点标准时间
   */
  public Integer getDotStandardRate() {
    // 获取第一个设置实体并返回其点标准时间
    return tickerTapeTrainSettingDao.findAll().list().getFirst().getDot();
  }
}
