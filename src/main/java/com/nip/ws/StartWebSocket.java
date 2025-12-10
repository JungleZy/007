package com.nip.ws;

import com.nip.common.constants.WsCode;
import com.nip.common.utils.JSONUtils;
import com.nip.ws.model.ResponseModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@ServerEndpoint("/startWebsocket/{sid}")
@ApplicationScoped
public class StartWebSocket {
  //静态变量，用来记录当前在线连接数。
  private static final List<String> onlineId = new ArrayList<>();
  /**
   * concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
   */
  private static StartWebSocket webSocketServerSet;
  private static final CopyOnWriteArraySet<StartWebSocket> webSocketClientSet = new CopyOnWriteArraySet<>();
  /**
   * 与某个客户端的连接会话，需要通过它来给客户端发送数据
   */
  private Session session;

  /**
   * 接收sid
   */
  private String sid = "";

  @OnOpen
  public void onOpen(Session session, @PathParam("sid") String sid) throws IOException {
    for (StartWebSocket item : webSocketClientSet) {
      if (sid.equals(item.sid)) {
        item.sendMessage(new ResponseModel(WsCode.CLOSE.getCode(), WsCode.CLOSE.getContent()));
        webSocketClientSet.remove(item);
        onlineId.remove(item.sid);
      }
    }
    this.session = session;
    webSocketClientSet.add(this);
    this.sid = sid;
    onlineId.add(sid);
    log.info("Client Join: {},Online Clients: {}", sid, webSocketClientSet.size());
  }

  @OnClose
  public void onClose() {
    webSocketClientSet.remove(this);
    onlineId.remove(this.sid);
    try {
      if (null != webSocketServerSet) {
        webSocketServerSet.sendMessage(
            new ResponseModel(WsCode.USER_LIST.getCode(), JSONUtils.toJson(onlineId)));
      }
    } catch (IOException e) {
      log.error("send message error", e);
    }
    log.info("Client Leave: {}; Online Clients: {}", this.sid, onlineId.size());
  }

  @OnError
  public void onError(Session session, @PathParam("sid") String sid, Throwable throwable) {
    log.info("onError: {}, {}", sid, throwable);
  }

  @OnMessage
  public void onMessage(String message, @PathParam("sid") String sid) {
    log.info("onMessage: {}, {}", sid, message);
  }

  public void sendMessage(ResponseModel message) throws IOException {
    this.session.getBasicRemote().sendText(JSONUtils.toJson(message));
  }
}
