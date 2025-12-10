package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
  private List<GeneralPatTrainUserModelDto> joinUser = new ArrayList<>();
}
