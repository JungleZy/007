package com.nip.ws.model;

import jakarta.websocket.Session;

import java.util.Objects;

public record SimulationSessionHolder(Session session, SimulationUserModel userModel) {
  public SimulationSessionHolder {
    Objects.requireNonNull(session, "session");
    Objects.requireNonNull(userModel, "userModel");
  }
}
