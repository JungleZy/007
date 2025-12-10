package com.nip.ws;

import com.nip.common.utils.JSONUtils;
import com.nip.ws.model.ResponseModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@ServerEndpoint("/status")
@ApplicationScoped
@Slf4j
public class StatusWebSocket {
  /**
   * 与某个客户端的连接会话，需要通过它来给客户端发送数据
   */
  private Session session;

  @OnOpen
  public void onOpen(Session session) {
    this.session = session;
    log.info("Client Join: {}", session.getId());
  }

  @OnClose
  public void onClose() {
    log.info("Client Leave: {}", session.getId());
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    log.info("onError> : {}", String.valueOf(throwable));
  }

  @OnMessage
  public void onMessage(String message) {
    log.info("onMessage> : {}", message);
    session.getAsyncRemote().sendText("pong");
  }

  public void sendMessage(ResponseModel message) throws IOException {
    this.session.getBasicRemote().sendText(JSONUtils.toJson(message));
  }
}
