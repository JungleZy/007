package com.nip.service.simulation;

import com.google.gson.reflect.TypeToken;
import com.nip.common.exception.ForbiddenException;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.entity.simulation.router.SimulationRouterRoomUserEntity;
import com.nip.service.UserService;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Objects;
import static com.nip.common.constants.BaseConstants.TOKEN;

@ApplicationScoped
public class SimulationRouterPatRecordService {
  @Inject SimulationRouterRoomDao rooms;
  @Inject SimulationRouterRoomUserDao members;
  @Inject UserService users;
  @Inject SimulationRoomAccess roomAccess;

  public record Stroke(Double diff, Double gap, Integer pageNumber) {}

  public List<Stroke> find(HttpServerRequest request, Integer roomId, String targetUserId) {
    if (roomId == null || roomId <= 0) throw new IllegalArgumentException("房间编号不正确");
    boolean manager = roomAccess.requireMember(request, roomId);
    String actor = users.getUserByToken(request.getHeader(TOKEN)).getId();
    if (targetUserId == null || targetUserId.isBlank() || Objects.equals(actor, targetUserId)) {
      return read(requireSender(request.getHeader(TOKEN), roomId));
    }
    var room = rooms.findById(roomId);
    var member = members.findByUserIdAndRoomId(actor, roomId);
    if (!Objects.equals(room.getRoomType(), 0) || !Objects.equals(room.getStats(), 2)
        || (!manager && (member == null || !Objects.equals(member.getUserType(), 1)))) {
      throw new ForbiddenException("训练结束后接收人员可查看本房间拍发记录");
    }
    var target = members.findByUserIdAndRoomId(targetUserId, roomId);
    if (target == null || !Objects.equals(target.getUserType(), 0)) {
      throw new IllegalArgumentException("该人员不是本房间的拍发人员");
    }
    return read(target);
  }

  @Transactional
  public List<Stroke> save(String token, Integer roomId, List<Stroke> strokes) {
    rooms.lockRoom(roomId);
    SimulationRouterRoomUserEntity member = requireSender(token, roomId);
    if (strokes == null || strokes.size() > 100000) throw new IllegalArgumentException("拍发记录数量不正确");
    for (Stroke stroke : strokes) {
      if (stroke == null || stroke.diff() == null || !Double.isFinite(stroke.diff()) || stroke.diff() < 0 || stroke.diff() > Integer.MAX_VALUE
          || stroke.gap() == null || !Double.isFinite(stroke.gap()) || stroke.gap() < 0 || stroke.gap() > Integer.MAX_VALUE
          || stroke.pageNumber() == null || stroke.pageNumber() < 1) {
        throw new IllegalArgumentException("拍发记录时长或页码不正确");
      }
    }
    List<Stroke> previous = read(member);
    // 全量快照只允许追加，重复上传幂等；迟到的旧快照不能抹掉已经保存的记录。
    int common = Math.min(previous.size(), strokes.size());
    if (!previous.subList(0, common).equals(strokes.subList(0, common))) {
      throw new IllegalArgumentException("拍发记录与已保存内容不一致");
    }
    if (strokes.size() <= previous.size()) return previous;
    member.setPatCodeLog(JSONUtils.toJson(strokes));
    members.save(member);
    return strokes;
  }

  private SimulationRouterRoomUserEntity requireSender(String token, Integer roomId) {
    String userId = users.getUserByToken(token).getId();
    if (roomId == null || roomId <= 0) throw new IllegalArgumentException("房间编号不正确");
    var room = rooms.findByIdOptional(roomId).orElseThrow(() -> new IllegalArgumentException("未查询到房间信息"));
    var member = members.findByUserIdAndRoomId(userId, roomId);
    if (!Objects.equals(room.getRoomType(), 0) || member == null || !Objects.equals(member.getUserType(), 0)) {
      throw new ForbiddenException("仅线路通报拍发人员可读写本人的拍发记录");
    }
    return member;
  }

  private List<Stroke> read(SimulationRouterRoomUserEntity member) {
    if (member.getPatCodeLog() == null || member.getPatCodeLog().isBlank()) return List.of();
    return JSONUtils.fromJson(member.getPatCodeLog(), new TypeToken<List<Stroke>>() {});
  }
}
