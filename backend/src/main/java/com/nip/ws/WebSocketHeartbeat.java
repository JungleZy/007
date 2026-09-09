package com.nip.ws;

import jakarta.websocket.Session;

/** Shared reserved heartbeat frame for repository-owned browser endpoints. */
public final class WebSocketHeartbeat {
  public static final String PING = "__nip_heartbeat_ping__";
  public static final String PONG = "__nip_heartbeat_pong__";
  private WebSocketHeartbeat() {}
  public static boolean respond(Session session, String message) {
    if (!PING.equals(message)) return false;
    try {
      if (session != null && session.isOpen()) session.getAsyncRemote().sendText(PONG);
    } catch (RuntimeException ignored) { }
    return true;
  }
}
