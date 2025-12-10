package com.nip.ws.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@RegisterForReflection
public class GeneralTickerPatTrainRoomUserModel {
  /**
   * 组训人员
   */
  private GeneralTickerPatTrainUserModel groupUser;

  /**
   * 参训人员
   */
  private List<GeneralTickerPatTrainUserModel> joinUser = new ArrayList<>();
}
