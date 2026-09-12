package com.nip.service.general;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@ApplicationScoped
@Slf4j
public class GeneralSettlementRecovery {
  @Inject GeneralTickerPatService ticker;
  @Inject GeneralKeyPatService key;
  @Inject GeneralTelexPatService telex;

  void onStartup(@Observes StartupEvent event) {
    recover();
  }

  @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void recover() {
    recover("手键", ticker::closingTrainIds, ticker::settleExpired);
    recover("电子键", key::closingTrainIds, key::settleExpired);
    recover("数据报", telex::closingTrainIds, telex::settleExpired);
  }

  /**
   * 收尾失败一律不外抛：本方法同时挂在 StartupEvent 观察者和 5s 定时器上，
   * 抛出会中断整个应用启动（库不可达时进程直接退出，2026-09-12 native 冒烟即因此失败）。
   * 待收尾清单查不到就整轮跳过，单个训练失败保留待重试状态，均由下一轮定时器重试。
   * 口径与 {@link com.nip.service.PostTelexPatTrainRecovery#recover()} 一致。
   */
  private <I> void recover(String domain, Supplier<List<I>> closingTrainIds, Consumer<I> settleExpired) {
    List<I> trainIds;
    try {
      trainIds = closingTrainIds.get();
    } catch (RuntimeException failure) {
      log.error("{}训练待收尾清单查询失败，下一轮将重试", domain, failure);
      return;
    }
    for (I trainId : trainIds) {
      try {
        settleExpired.accept(trainId);
      } catch (RuntimeException failure) {
        log.error("{}训练收尾失败，保留待重试状态，训练ID: {}", domain, trainId, failure);
      }
    }
  }
}
