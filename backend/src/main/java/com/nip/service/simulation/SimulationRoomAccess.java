package com.nip.service.simulation;

import com.nip.common.exception.ForbiddenException;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.entity.UserEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomEntity;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import com.nip.service.UserService;
import com.nip.service.TrainWriteAccess;
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
  @Inject TrainWriteAccess trainWriteAccess;

  public boolean requireMember(HttpServerRequest request, Integer roomId) {
    return requireAnswer(request, roomId, null);
  }

  public boolean requireAnswer(HttpServerRequest request, Integer roomId, String targetUserId) {
    UserEntity user = users.getUserByToken(request.getHeader(TOKEN));
    SimulationRouterRoomEntity room = requireRoom(roomId);
    SimulationRouterRoomUserEntity member = members.findByUserIdAndRoomId(user.getId(), roomId);
    boolean teacher = manages(room, user.getId(), member);
    if (!teacher && member == null) throw new ForbiddenException("无权查看该训练");
    if (targetUserId != null) {
      if (!teacher && !Objects.equals(user.getId(), targetUserId)) {
        throw new ForbiddenException("无权查看其他学员答案");
      }
      if (!Objects.equals(room.getCreateUserId(), targetUserId)
          && members.findByUserIdAndRoomId(targetUserId, roomId) == null) {
        throw new IllegalArgumentException("该人员不属于此训练");
      }
    }
    return teacher;
  }

  public SimulationRouterRoomUserEntity requireChannelChange(HttpServerRequest request, Integer roomId,
      String targetUserId) {
    UserEntity user = users.getUserByToken(request.getHeader(TOKEN));
    SimulationRouterRoomEntity room = requireRoom(roomId);
    SimulationRouterRoomUserEntity target = members.findByUserIdAndRoomId(targetUserId, roomId);
    if (target == null) throw new IllegalArgumentException("该人员不属于此训练");
    SimulationRouterRoomUserEntity actor = Objects.equals(user.getId(), targetUserId)
        ? target : members.findByUserIdAndRoomId(user.getId(), roomId);
    boolean ownReceiverChannel = Objects.equals(room.getRoomType(), 0)
        && Objects.equals(user.getId(), targetUserId) && Objects.equals(target.getUserType(), 1);
    if (!ownReceiverChannel && !manages(room, user.getId(), actor)) {
      throw new ForbiddenException("无权更改该人员频道");
    }
    return target;
  }

  public SimulationRouterRoomEntity requireControl(HttpServerRequest request, Integer roomId) {
    UserEntity user = users.getUserByToken(request.getHeader(TOKEN));
    SimulationRouterRoomEntity room = requireRoom(roomId);
    trainWriteAccess.requireWritableTrain(user.getId(), room.getCreateUserId(),
        () -> isOrganizer(room, user.getId()), "仿真房间 " + roomId);
    return room;
  }

  private SimulationRouterRoomEntity requireRoom(Integer roomId) {
    return rooms.findByIdOptional(roomId)
        .orElseThrow(() -> new IllegalArgumentException("未查询到房间信息"));
  }

  private boolean manages(SimulationRouterRoomEntity room, String userId,
      SimulationRouterRoomUserEntity member) {
    return trainWriteAccess.manages(userId, room.getCreateUserId(), () -> organizer(room, userId, member));
  }

  /**
   * 房间的「组训位」，不包含系统管理员；管理员由 {@link TrainWriteAccess} 统一判定。
   *
   * <p>线路通报房（{@code roomType=0}）的建房人在 {@code simulation_router_room_user} 里没有行，
   * 只能靠 {@code createUserId} 认；其余房型里 {@code userType=0} 的发报位就是带训位。
   */
  public boolean isOrganizer(SimulationRouterRoomEntity room, String userId) {
    return organizer(room, userId, members.findByUserIdAndRoomId(userId, room.getId()));
  }

  private static boolean organizer(SimulationRouterRoomEntity room, String userId,
      SimulationRouterRoomUserEntity member) {
    return Objects.equals(room.getCreateUserId(), userId)
        || (!Objects.equals(room.getRoomType(), 0) && member != null && Objects.equals(member.getUserType(), 0));
  }
}
