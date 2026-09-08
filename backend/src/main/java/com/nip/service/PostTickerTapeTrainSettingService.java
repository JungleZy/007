package com.nip.service;

import com.nip.common.utils.PojoUtils;
import com.nip.dao.PostTickerTapeTrainSettingDao;
import com.nip.dto.vo.PostTickerTapeTrainSettingVO;
import com.nip.dto.vo.param.PostTickerTapeTrainSettingAddParam;
import com.nip.entity.PostTickerTapeTrainSettingEntity;
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
public class PostTickerTapeTrainSettingService {

  private final PostTickerTapeTrainSettingDao tickerTapeTrainSettingDao;

  @Inject
  public PostTickerTapeTrainSettingService(PostTickerTapeTrainSettingDao tickerTapeTrainSettingDao) {
    this.tickerTapeTrainSettingDao = tickerTapeTrainSettingDao;
  }

  public List<PostTickerTapeTrainSettingVO> findAll() {
    List<PostTickerTapeTrainSettingEntity> entity = tickerTapeTrainSettingDao.findAll().list();
    return PojoUtils.convert(entity, PostTickerTapeTrainSettingVO.class);
  }


  @Transactional
  public PostTickerTapeTrainSettingAddParam addOrUpdate(PostTickerTapeTrainSettingAddParam addParams) {
    tickerTapeTrainSettingDao.deleteAll();
    List<PostTickerTapeTrainSettingEntity> entityList = PojoUtils.convert(
        addParams.getParamList(), PostTickerTapeTrainSettingEntity.class);
    for (PostTickerTapeTrainSettingEntity entity : entityList) {
      entity.setDot(addParams.getDotStandardTime());
    }
    tickerTapeTrainSettingDao.save(entityList);
    return addParams;
  }


  public Integer getDotStandard() {
    return tickerTapeTrainSettingDao.findAll().list().stream().findFirst()
        .orElse(new PostTickerTapeTrainSettingEntity()).getDot();

  }
}
