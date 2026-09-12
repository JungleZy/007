package com.nip.service;

import com.nip.dao.PostTelexPatTrainDao;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@ApplicationScoped
public class PostTelexPatTrainRecovery {
  private final PostTelexPatTrainDao trainDao;
  private final PostTelexPatTrainService trainService;

  @Inject
  public PostTelexPatTrainRecovery(PostTelexPatTrainDao trainDao, PostTelexPatTrainService trainService) {
    this.trainDao = trainDao;
    this.trainService = trainService;
  }

  void recoverOnStartup(@Observes StartupEvent event) {
    recover();
  }

  @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void recover() {
    List<String> due;
    try {
      due = trainDao.findDueIds(LocalDateTime.now().minusSeconds(60));
    } catch (RuntimeException failure) {
      log.error("电传/数据报倒计时恢复扫描失败，下一轮将重试", failure);
      return;
    }
    for (String trainId : due) {
      try {
        trainService.settleDue(trainId);
      } catch (RuntimeException failure) {
        log.error("电传/数据报倒计时结算失败，训练ID: {}；事务已回滚，下一轮将重试", trainId, failure);
      }
    }
  }
}
