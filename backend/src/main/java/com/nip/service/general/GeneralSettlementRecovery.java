package com.nip.service.general;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class GeneralSettlementRecovery {
  @Inject GeneralTickerPatService ticker;
  @Inject GeneralKeyPatService key;

  void onStartup(@Observes StartupEvent event) {
    recover();
  }

  @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void recover() {
    for (Integer trainId : ticker.closingTrainIds()) {
      try {
        ticker.settleExpired(trainId);
      } catch (RuntimeException failure) {
        log.error("手键训练收尾失败，保留待重试状态，训练ID: {}", trainId, failure);
      }
    }
    for (Integer trainId : key.closingTrainIds()) {
      try {
        key.settleExpired(trainId);
      } catch (RuntimeException failure) {
        log.error("电子键训练收尾失败，保留待重试状态，训练ID: {}", trainId, failure);
      }
    }
  }
}
