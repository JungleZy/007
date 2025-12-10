package com.nip.service;

import cn.hutool.core.bean.BeanUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.PageInfo;
import com.nip.common.constants.TickerTapeTrainStatisticalTypeEnum;
import com.nip.common.constants.TickerTapeTrainStatusEnum;
import com.nip.common.constants.TickerTapeTrainTypeEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.TickerTapeTrainDao;
import com.nip.dao.TickerTapeTrainStatisticalDao;
import com.nip.dao.UserDao;
import com.nip.dto.Page;
import com.nip.dto.sql.TickerTapeTrainDaoCountBaseTrain;
import com.nip.dto.vo.TelexPatTrainStatisticalVO;
import com.nip.dto.vo.TickerTapeTrainVo;
import com.nip.dto.vo.param.TickerTapeBaseTrainAddParam;
import com.nip.dto.vo.param.TickerTapeTrainAddParam;
import com.nip.dto.vo.param.TickerTapeTrainUpdateParam;
import com.nip.entity.TickerTapeTrainEntity;
import com.nip.entity.TickerTapeTrainStatisticalEntity;
import com.nip.entity.UserEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 15:49
 * @Description:
 */
@ApplicationScoped
public class TickerTapeTrainService {

  private final TickerTapeTrainDao tickerTapeTrainDao;
  private final TickerTapeTrainStatisticalDao statisticalDao;
  private final UserDao userDao;

  // 使用构造函数注入依赖项
  @Inject
  public TickerTapeTrainService(TickerTapeTrainDao tickerTapeTrainDao, TickerTapeTrainStatisticalDao statisticalDao, UserDao userDao) {
    this.tickerTapeTrainDao = tickerTapeTrainDao;
    this.statisticalDao = statisticalDao;
    this.userDao = userDao;
  }

  @Transactional
  public TickerTapeTrainAddParam add(TickerTapeTrainAddParam param, String token) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    String codeMessage = JSONUtils.toJson(param.getCodeMessageBody());
    TickerTapeTrainEntity entity = BeanUtil.toBean(param, TickerTapeTrainEntity.class);
    entity.setUserId(userEntity.getId());
    //状体设为未开始
    entity.setStatus(TickerTapeTrainStatusEnum.NOT_STARTED.getCode());
    entity.setCodeMessageBody(codeMessage);
    entity.setSchedule(0);
    //有效时长
    entity.setValidTime("0");
    //语音播放初始位置
    entity.setMark("0,0");
    entity.setCreateTime(LocalDateTime.now());
    //查询上一次训练状态
    TickerTapeTrainEntity lastTrain = tickerTapeTrainDao.lastTrain(userEntity.getId(), param.getType());
    if (lastTrain != null) {
      //如果状态是未开始，删除如是暂停则改为已完成，并统计
      if (lastTrain.getStatus().compareTo(TickerTapeTrainStatusEnum.NOT_STARTED.getCode()) == 0) {
        tickerTapeTrainDao.deleteById(lastTrain.getId());
      } else if (lastTrain.getStatus().compareTo(TickerTapeTrainStatusEnum.PAUSE.getCode()) == 0) {
        lastTrain.setStatus(TickerTapeTrainStatusEnum.FINISH.getCode());
        TickerTapeTrainEntity save = tickerTapeTrainDao.save(lastTrain);
        finishStatistical(save);
      }
    }
    TickerTapeTrainEntity save = tickerTapeTrainDao.save(entity);
    param.setId(save.getId());
    return param;
  }

  public PageInfo<TickerTapeTrainVo> listPage(Page page, String token) throws Exception {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    PanacheQuery<TickerTapeTrainEntity> query = tickerTapeTrainDao.find("userId = ?1 order by createTime desc",
        userEntity.getId()
    );
    query.page(io.quarkus.panache.common.Page.ofSize(page.getRows()));
    int totalPages = query.pageCount();
    long totalNumber = query.count();
    List<TickerTapeTrainEntity> entities = query.page(
        io.quarkus.panache.common.Page.of(page.getPage() - 1, page.getRows())).list();
    List<TickerTapeTrainVo> convert = PojoUtils.convert(entities, TickerTapeTrainVo.class, (e, v) -> {
      List<Map<String, Object>> maps = JSONUtils.fromJson(e.getCodeMessageBody(),
          new TypeToken<>() {
          });
      v.setCodeMessageBody(maps);
    });
    return new PageInfo<>(totalPages, totalNumber, (page.getPage()), page.getRows(), convert);
  }

  @Transactional
  public TickerTapeTrainUpdateParam update(TickerTapeTrainUpdateParam param) {
    TickerTapeTrainEntity entity = BeanUtil.toBean(param, TickerTapeTrainEntity.class);
    TickerTapeTrainEntity save = tickerTapeTrainDao.save(entity);
    return BeanUtil.toBean(save, TickerTapeTrainUpdateParam.class);
  }

  public TickerTapeTrainVo getById(String id) {
    TickerTapeTrainEntity entity = tickerTapeTrainDao.findById(id);

    return PojoUtils.convertOne(entity, TickerTapeTrainVo.class, (e, v) -> {
      List<Map<String, Object>> maps = JSONUtils.fromJson(e.getCodeMessageBody(),
          new TypeToken<>() {
          });
      v.setCodeMessageBody(maps);
    });
  }

  public void begin(String id) {
    checkStatus(id);
    tickerTapeTrainDao.begin(id);
  }

  public void pause(TickerTapeTrainUpdateParam updateParam) {
    checkStatus(updateParam.getId());
    tickerTapeTrainDao.pause(updateParam.getId(), updateParam.getValidTime(), updateParam.getMark(),
        updateParam.getSchedule()
    );
  }

  public void goOn(String id) {
    checkStatus(id);
    tickerTapeTrainDao.goOn(id);
  }

  public void finish(TickerTapeTrainUpdateParam updateParam) {
    checkStatus(updateParam.getId());
    tickerTapeTrainDao.finish(updateParam.getId(), updateParam.getValidTime(), updateParam.getMark(),
        updateParam.getSchedule()
    );
    TickerTapeTrainEntity entity = tickerTapeTrainDao.findById(updateParam.getId());
    finishStatistical(entity);
  }

  public void saveBaseTrain(TickerTapeBaseTrainAddParam param, String token) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    TickerTapeTrainEntity entity = PojoUtils.convertOne(param, TickerTapeTrainEntity.class);
    entity.setStatus(TickerTapeTrainStatusEnum.FINISH.getCode());
    entity.setUserId(userEntity.getId());
    entity.setCreateTime(LocalDateTime.now());
    saveEntity(entity);
    saveStatistical(userEntity, entity);
  }

  @Transactional
  public void saveEntity(TickerTapeTrainEntity entity) {
    tickerTapeTrainDao.save(entity);
  }

  @Transactional
  public void saveStatistical(UserEntity userEntity, TickerTapeTrainEntity entity) {
    //统计科式/基础
    Map<String, Object> map = tickerTapeTrainDao.countBaseTrain(userEntity.getId(), entity.getType());
    TickerTapeTrainStatisticalEntity statisticalEntity = JSONUtils.fromJson(JSONUtils.toJson(map),
        new TypeToken<>() {
        }
    );
    TickerTapeTrainStatisticalEntity queryStatisticalEntity;
    if (entity.getType().compareTo(TickerTapeTrainTypeEnum.COD.getCode()) == 0) {
      queryStatisticalEntity = statisticalDao.findByUserIdAndType(
          userEntity.getId(), TickerTapeTrainStatisticalTypeEnum.COD.getCode());
      statisticalEntity.setType(TickerTapeTrainStatisticalTypeEnum.COD.getCode());

    } else {
      queryStatisticalEntity = statisticalDao.findByUserIdAndType(
          userEntity.getId(), TickerTapeTrainStatisticalTypeEnum.BASE.getCode());
      statisticalEntity.setType(TickerTapeTrainStatisticalTypeEnum.BASE.getCode());
    }

    queryStatisticalEntity = Optional.ofNullable(queryStatisticalEntity).map(
        temp -> temp.setTotalTime(statisticalEntity.getTotalTime()).setAvgSpeed(statisticalEntity.getAvgSpeed())
            .setTotalCount(statisticalEntity.getTotalCount())).orElse(
        new TickerTapeTrainStatisticalEntity()
            .setUserId(userEntity.getId())
            .setTotalTime(statisticalEntity.getTotalTime())
            .setAvgSpeed(statisticalEntity.getAvgSpeed())
            .setTotalCount(statisticalEntity.getTotalCount())
            .setType(statisticalEntity.getType()));

    statisticalDao.save(queryStatisticalEntity);
  }

  @Transactional
  public List<TelexPatTrainStatisticalVO> statisticalPage(String token) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    List<TickerTapeTrainStatisticalEntity> entities = statisticalDao.findByUserId(userEntity.getId());
    Map<Integer, List<TickerTapeTrainStatisticalEntity>> collect = entities.stream().collect(
        Collectors.groupingBy(TickerTapeTrainStatisticalEntity::getType));
    for (int i = 0; i < 3; i++) {
      if (collect.get(i) == null) {
        TickerTapeTrainStatisticalEntity entity = new TickerTapeTrainStatisticalEntity();
        entity.setType(i);
        entity.setUserId(userEntity.getId());
        entity.setTotalCount(0);
        entity.setAvgSpeed(new BigDecimal(0));
        entity.setTotalTime("0");
        statisticalDao.save(entity);
        entities.add(entity);
      }
    }
    List<TelexPatTrainStatisticalVO> convert = PojoUtils.convert(entities, TelexPatTrainStatisticalVO.class);
    Collections.swap(convert, 0, 1);
    return convert;
  }

  public TickerTapeTrainVo lastTrain(String token, Integer type) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    TickerTapeTrainEntity entity = tickerTapeTrainDao.lastTrain(userEntity.getId(), type);
    if (entity == null) {
      return new TickerTapeTrainVo();
    }
    return PojoUtils.convertOne(entity, TickerTapeTrainVo.class);
  }

  private void checkStatus(String id) {
    TickerTapeTrainEntity entity = tickerTapeTrainDao.findById(id);
    if (entity.getStatus().compareTo(TickerTapeTrainStatusEnum.FINISH.getCode()) == 0) {
      throw new TrainFinishedException("训练已结束");
    }
  }

  @Transactional
  public void finishStatistical(TickerTapeTrainEntity entity) {
    TickerTapeTrainDaoCountBaseTrain train = tickerTapeTrainDao.countLetterTrain(entity.getUserId());
    TickerTapeTrainStatisticalEntity letterCount = PojoUtils.convertOne(train, TickerTapeTrainStatisticalEntity.class);
    //先查询是否有记录
    TickerTapeTrainStatisticalEntity statisticalEntity = statisticalDao.findByUserIdAndType(
        entity.getUserId(), TickerTapeTrainStatisticalTypeEnum.LETTER.getCode());

    TickerTapeTrainStatisticalEntity trainStatisticalEntity = Optional.ofNullable(statisticalEntity)
        .map(temp -> temp.setTotalCount(letterCount.getTotalCount())
            .setTotalTime(letterCount.getTotalTime())
            .setAvgSpeed(letterCount.getAvgSpeed()))
        .orElse(new TickerTapeTrainStatisticalEntity()
            .setType(TickerTapeTrainStatisticalTypeEnum.LETTER.getCode())
            .setUserId(entity.getUserId())
            .setTotalCount(letterCount.getTotalCount())
            .setTotalTime(letterCount.getTotalTime())
            .setAvgSpeed(letterCount.getAvgSpeed())

        );
    statisticalDao.save(trainStatisticalEntity);
  }

  public static class TrainFinishedException extends RuntimeException {
    public TrainFinishedException(String message) {
      super(message);
    }
  }
}
