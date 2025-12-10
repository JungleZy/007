package com.nip.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(title = "获取GeneralTelexPatUser信息Dto")
@RegisterForReflection
public class GeneralTelexPatUserSimpleDto {
  /**
   * 用户ID
   */
  private String userId;
  private String userName;
  private String userImg;
  /**
   * 角色 0 参训人 1 组训人
   */
  private Integer role;

}
