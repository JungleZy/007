package com.nip.service.simulation;

import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import com.nip.service.UserService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;

import static com.nip.common.constants.BaseConstants.TOKEN;

@ApplicationScoped
public class SimulationRoomAccess {
  @Inject UserService users;
  @Inject SimulationRouterRoomDao rooms;
  @Inject SimulationRouterRoomUserDao members;

  public boolean requireMember(HttpServerRequest request, Integer roomId) {
    return requireAnswer(request, roomId, null);
  }

  public boolean requireAnswer(HttpServerRequest request, Integer roomId, String targetUserId) {
    UserEntity user = users.getUserByToken(request.getHeader(TOKEN));
    if (user == null) throw new IllegalArgumentException("登录已失效");
    SimulationRouterRoomEntity room = rooms.findByIdOptional(roomId)
        .orElseThrow(() -> new IllegalArgumentException("未查询到房间信息"));
    SimulationRouterRoomUserEntity member = members.findByUserIdAndRoomId(user.getId(), roomId);
    boolean teacher = Objects.equals(room.getCreateUserId(), user.getId())
        || (!Objects.equals(room.getRoomType(), 0) && member != null && Objects.equals(member.getUserType(), 0));
    if (!teacher && member == null) throw new IllegalArgumentException("无权查看该训练");
    if (targetUserId != null) {
      if (!teacher && !Objects.equals(user.getId(), targetUserId)) {
        throw new IllegalArgumentException("无权查看其他学员答案");
      }
      if (!Objects.equals(room.getCreateUserId(), targetUserId)
          && members.findByUserIdAndRoomId(targetUserId, roomId) == null) {
        throw new IllegalArgumentException("该人员不属于此训练");
      }
    }
    return teacher;
  }
}
