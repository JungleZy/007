package com.nip.service.simulation;

import com.nip.common.utils.JSONUtils;
import com.nip.dao.simulation.SimulationRouterRoomDao;
import com.nip.dao.simulation.SimulationRouterRoomUserDao;
import com.nip.ws.WebSocketSimulationService;
import com.nip.ws.model.SimulationSessionHolder;
import com.nip.ws.service.simulation.SimulationGlobal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class SimulationResultNotifier {
  @Inject Event<Result> results;
  @Inject SimulationRouterRoomDao rooms;
  @Inject SimulationRouterRoomUserDao members;

  public void publish(Integer roomId, String userId, Integer roomType) {
    Set<String> recipients = members.findByRoomId(roomId).stream()
        .filter(member -> !Objects.equals(roomType, 0) && Objects.equals(member.getUserType(), 0))
        .map(member -> member.getUserId()).collect(Collectors.toSet());
    String creator = rooms.findById(roomId).getCreateUserId();
    if (creator != null) recipients.add(creator);
    results.fire(new Result(roomId, userId, roomType, Set.copyOf(recipients)));
  }

  void afterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) Result result) {
    List<SimulationSessionHolder> sessions = switch (result.roomType()) {
      case 0 -> SimulationGlobal.routerRoom.get(result.roomId());
      case 1 -> SimulationGlobal.disturbRoom.get(result.roomId());
      case 2, 3 -> SimulationGlobal.reportRoom.get(result.roomId());
      default -> List.of();
    };
    if (sessions == null) return;
    String notification = JSONUtils.toJson(Map.of("type", "result", "topic", "result",
        "roomId", result.roomId(), "userId", result.userId()));
    for (SimulationSessionHolder session : sessions) {
      if (result.recipients().contains(session.userModel().getId())) {
        WebSocketSimulationService.sendMessage(session.session(), notification, "", "");
      }
    }
  }

  record Result(Integer roomId, String userId, Integer roomType, Set<String> recipients) {}
}
