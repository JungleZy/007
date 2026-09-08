package com.nip.service.event;

import com.nip.dao.TelegramTrainFloorContentDao;
import com.nip.dao.TelegramTrainLogDao;
import com.nip.entity.TelegramTrainFloorContentEntity;
import com.nip.entity.TelegramTrainLogEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Date;

/**
 * WebSocketService
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-01-13 13:54
 */
@ApplicationScoped
public class WebSocketEventService {
  private final TelegramTrainLogDao telegramTrainLogDao;
  private final TelegramTrainFloorContentDao floorContentDao;

  @Inject
  public WebSocketEventService(TelegramTrainLogDao telegramTrainLogDao, TelegramTrainFloorContentDao floorContentDao) {
    this.telegramTrainLogDao = telegramTrainLogDao;
    this.floorContentDao = floorContentDao;
  }


  //@EventListener
  //  @Singleton
  @Transactional
  public void saveTelegramTrainLog(TelegramTrainLogEntity trainLog) {
    trainLog.setCreatTime(String.valueOf(new Date().getTime()));
    telegramTrainLogDao.saveAndFlush(trainLog);
  }

  //@EventListener
  @Transactional
  public void saveTelegramTrainFloorContentEntity(TelegramTrainFloorContentEntity entity) {
    floorContentDao.update("moresValue=?1,moresTime=?2 where id =?3", entity.getMoresValue(), entity.getMoresTime(),
        entity.getId());
  }
}
