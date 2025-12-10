package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.TickerTapeTrainStageSettingDao;
import com.nip.dto.vo.TickerTapeTrainStageSettingVO;
import com.nip.entity.TickerTapeTrainStageSettingEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-02-21 17:12
 * @Description:
 */
@ApplicationScoped
public class TickerTapeTrainStageSettingService  {

  private final TickerTapeTrainStageSettingDao settingDao;

  // 使用构造函数注入
  @Inject
  public TickerTapeTrainStageSettingService(TickerTapeTrainStageSettingDao settingDao) {
    this.settingDao = settingDao;
  }


  /**
   * 添加或更新TickerTape列车阶段设置信息
   * 如果传入的对象ID为null，则视为新增；否则视为更新
   *
   * @param vo TickerTape列车阶段设置的视图对象，封装了要添加或更新的数据
   * @return 返回添加或更新后的视图对象
   */
  @Transactional
  public TickerTapeTrainStageSettingVO add(TickerTapeTrainStageSettingVO vo) {
    TickerTapeTrainStageSettingEntity entity;
    if (vo.getId()==null){
      // 新增情况：将视图对象转换为实体对象，准备持久化
      entity = PojoUtils.convertOne(vo, TickerTapeTrainStageSettingEntity.class);
    }else {
      // 更新情况：根据ID获取实体对象，并用视图对象中的阶段数组更新实体对象
      entity = settingDao.findById(vo.getId());
      entity.setStageArray(vo.getStageArray());
    }

    // 保存并刷新实体对象，确保数据一致性
    TickerTapeTrainStageSettingEntity save = settingDao.saveAndFlush(entity);
    // 将保存后的实体对象转换为视图对象，返回
    return PojoUtils.convertOne(save,TickerTapeTrainStageSettingVO.class);
  }

  /**
   * 查询所有TickerTape列车阶段设置信息
   * 如果未找到任何设置信息，则返回一个默认的设置信息对象
   *
   * @return 返回一个包含设置信息的视图对象，即使数据库中无数据，也会返回一个默认对象
   */
  public TickerTapeTrainStageSettingVO findAll() {
    List<TickerTapeTrainStageSettingEntity> all = settingDao.findAll().list();
    if (all.isEmpty()){
      // 如果数据库中没有任何设置信息，创建并添加一个默认的实体对象到列表中
      all.add(new TickerTapeTrainStageSettingEntity());
    }
    // 将第一个实体对象转换为视图对象并返回
    return PojoUtils.convertOne(all.getFirst(),TickerTapeTrainStageSettingVO.class);
  }
}
