package com.nip.dto;

import com.nip.entity.UserEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record UserSummary(String id, String userName, String userAccount, String userImg) {
  public static UserSummary from(UserEntity user) {
    return new UserSummary(user.getId(), user.getUserName(), user.getUserAccount(), user.getUserImg());
  }
}
