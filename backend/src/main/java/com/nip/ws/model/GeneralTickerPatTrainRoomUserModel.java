package com.nip.ws.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@RegisterForReflection
public class GeneralTickerPatTrainRoomUserModel {
  /**
   * 组训人员
   */
  private GeneralTickerPatTrainUserModel groupUser;

  /**
   * 参训人员。WS onOpen/onClose 增删、onMessage 广播遍历、HTTP 线程拷贝读取
   * 三方向并发，必须是 CopyOnWriteArrayList（P1-6）
   */
  private List<GeneralTickerPatTrainUserModel> joinUser = new CopyOnWriteArrayList<>();
}
