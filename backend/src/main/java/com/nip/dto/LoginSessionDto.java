package com.nip.dto;

import com.nip.entity.RoleEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public record LoginSessionDto(UserProfile user, RoleEntity role, List<MenusDto> menus,
                              String token, String deviceId) {
}
