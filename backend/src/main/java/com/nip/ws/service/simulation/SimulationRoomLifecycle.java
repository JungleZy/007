package com.nip.ws.service.simulation;

import com.nip.ws.model.SimulationSessionHolder;
import jakarta.websocket.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class SimulationRoomLifecycle {
  private SimulationRoomLifecycle() {
  }

  public static Replacement replace(
      ConcurrentMap<Integer, List<SimulationSessionHolder>> rooms,
      Integer roomId,
      SimulationSessionHolder current) {
    List<SimulationSessionHolder> replaced = new ArrayList<>();
    AtomicReference<List<SimulationSessionHolder>> members = new AtomicReference<>();
    rooms.compute(roomId, (key, existing) -> {
      List<SimulationSessionHolder> room = existing == null
          ? new CopyOnWriteArrayList<>()
          : existing;
      room.removeIf(holder -> {
        if (!Objects.equals(holder.userModel().getId(), current.userModel().getId())) {
          return false;
        }
        replaced.add(holder);
        return true;
      });
      room.add(current);
      members.set(room);
      return room;
    });
    return new Replacement(members.get(), List.copyOf(replaced));
  }

  public static Removal removeCurrent(
      ConcurrentMap<Integer, List<SimulationSessionHolder>> rooms,
      Integer roomId,
      String userId,
      Session session) {
    AtomicReference<Removal> removed = new AtomicReference<>();
    rooms.computeIfPresent(roomId, (key, members) -> {
      SimulationSessionHolder current = members.stream()
          .filter(holder -> sameConnection(holder, userId, session))
          .findFirst()
          .orElse(null);
      if (current == null) {
        return members;
      }
      members.remove(current);
      removed.set(new Removal(current, List.copyOf(members)));
      return members.isEmpty() ? null : members;
    });
    return removed.get();
  }

  public static boolean isCurrent(
      ConcurrentMap<Integer, List<SimulationSessionHolder>> rooms,
      Integer roomId,
      String userId,
      Session session) {
    List<SimulationSessionHolder> members = rooms.get(roomId);
    if (members == null) {
      return false;
    }
    for (SimulationSessionHolder holder : members) {
      if (sameConnection(holder, userId, session)) {
        return true;
      }
    }
    return false;
  }

  private static boolean sameConnection(
      SimulationSessionHolder holder,
      String userId,
      Session session) {
    return Objects.equals(holder.userModel().getId(), userId)
        && (holder.session() == session
        || Objects.equals(holder.session().getId(), session.getId()));
  }

  public record Replacement(
      List<SimulationSessionHolder> members,
      List<SimulationSessionHolder> replaced) {
  }

  public record Removal(
      SimulationSessionHolder holder,
      List<SimulationSessionHolder> remaining) {
  }
}
