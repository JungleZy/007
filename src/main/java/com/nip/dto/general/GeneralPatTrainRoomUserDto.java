package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@RegisterForReflection
public class GeneralPatTrainRoomUserDto {
  /**
   * 组训人员
   */
  private GeneralPatTrainUserModelDto groupUser;

  /**
   * 参训人员
   */
  // ws 多线程并发迭代+增删，裸 ArrayList 有竞态——改 COW
  private List<GeneralPatTrainUserModelDto> joinUser = new CopyOnWriteArrayList<>();
}
