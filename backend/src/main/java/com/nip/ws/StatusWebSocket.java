package com.nip.ws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;

@ServerEndpoint("/status")
@ApplicationScoped
@Slf4j
public class StatusWebSocket {

  @OnOpen
  public void onOpen(Session session) {
    log.info("Client Join: {}", session.getId());
  }

  @OnClose
  public void onClose(Session session) {
    log.info("Client Leave: {}", session.getId());
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    log.info("onError> : {}", String.valueOf(throwable));
  }

  @OnMessage
  public void onMessage(String message, Session session) {
    log.info("onMessage> : {}", message);
    session.getAsyncRemote().sendText("pong");
  }
}
