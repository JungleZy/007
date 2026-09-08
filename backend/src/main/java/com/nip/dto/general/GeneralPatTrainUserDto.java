package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@RegisterForReflection
public class GeneralPatTrainUserDto {

  @Schema(title = "id")
  private String id;

  @Schema(title = "姓名")
  private String userName;

  @Schema(title = "头像")
  private String userImg;

  @Schema(title = "0参训人 1组训人")
  private Integer role;

  @Schema(title = "状态 0 离线 1在线 2 准备")
  private Integer status;

  public GeneralPatTrainUserDto() {
  }

  public GeneralPatTrainUserDto(String id, String userName, String userImg, Integer role) {
    this.id = id;
    this.userName = userName;
    this.userImg = userImg;
    this.role = role;
  }
}
