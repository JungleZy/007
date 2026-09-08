package com.nip.ws;

import com.nip.common.constants.WsCode;
import com.nip.common.utils.JSONUtils;
import com.nip.ws.model.ResponseModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@ServerEndpoint("/startWebsocket/{sid}")
@ApplicationScoped
public class StartWebSocket {
  /**
   * 在线连接表：sid -> 该连接的 Session。
   * 端点是 @ApplicationScoped 单例，连接态一律挂在本表上；
   * 禁止实例字段（单例 this 进 Set 导致集合恒 1 元素，P1-10 根因）。
   */
  private static final ConcurrentMap<String, Session> CLIENTS = new ConcurrentHashMap<>();

  @OnOpen
  public void onOpen(Session session, @PathParam("sid") String sid) {
    Session old = CLIENTS.put(sid, session);
    if (old != null && old != session) {
      //踢掉同 sid 的旧连接：通知后由客户端自行断开
      send(old, new ResponseModel(WsCode.CLOSE.getCode(), WsCode.CLOSE.getContent()));
    }
    log.info("Client Join: {},Online Clients: {}", sid, CLIENTS.size());
  }

  @OnClose
  public void onClose(@PathParam("sid") String sid, Session session) {
    //条件移除：同 sid 重连后，旧 session 的 onClose 不得摘掉新连接
    CLIENTS.remove(sid, session);
    log.info("Client Leave: {}; Online Clients: {}", sid, CLIENTS.size());
  }

  @OnError
  public void onError(Session session, @PathParam("sid") String sid, Throwable throwable) {
    log.info("onError: {}, {}", sid, throwable);
  }

  @OnMessage
  public void onMessage(String message, @PathParam("sid") String sid) {
    log.info("onMessage: {}, {}", sid, message);
  }

  private static void send(Session session, ResponseModel message) {
    try {
      if (session.isOpen()) {
        session.getAsyncRemote().sendText(JSONUtils.toJson(message));
      }
    } catch (Exception e) {
      log.error("StartWebSocket send", e);
    }
  }
}
