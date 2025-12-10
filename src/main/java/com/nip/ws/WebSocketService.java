package com.nip.ws;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.CodeConstants;
import com.nip.common.utils.JSONUtils;
import com.nip.entity.TelegramTrainFloorContentEntity;
import com.nip.entity.TelegramTrainLogEntity;
import com.nip.service.event.WebSocketEventService;
import com.nip.service.simulation.SimulationRouterRoomUserService;
import com.nip.ws.model.ResponseModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocketService
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2018-12-14 11:24
 */
@ServerEndpoint(value = "/websocket/{sid}")
@ApplicationScoped
@Data
@Slf4j
public class WebSocketService {

  @Inject
  private WebSocketEventService webSocketEventService;
  @Inject
  private SimulationRouterRoomUserService roomUserService;

  //静态变量，用来记录当前在线连接数
  private static List<String> onlineId = new ArrayList<>();
  /**
   * concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
   */
  private static WebSocketService webSocketServerSet;
  private static CopyOnWriteArraySet<WebSocketService> webSocketClientSet = new CopyOnWriteArraySet<>();

  /**
   * 与某个客户端的连接会话，需要通过它来给客户端发送数据
   */
  private Session session;

  /**
   * 接收sid
   */
  private String sid = "";

  /**
   * 连接建立成功调用的方法
   */
  @OnOpen
  public void onOpen(Session session, @PathParam("sid") String sid) throws IOException {
    for (WebSocketService item : webSocketClientSet) {
      if (sid.equals(item.sid)) {
        item.sendMessage(new ResponseModel(CodeConstants.CLOSE.getCode(), CodeConstants.CLOSE.getContent()));
        webSocketClientSet.remove(item);
        onlineId.remove(item.sid);
      }
    }
    this.session = session;
    this.sid = sid;
    WebSocketService service = new WebSocketService();
    service.setSession(session);
    service.setSid(sid);
    webSocketClientSet.add(service);
    onlineId.add(sid);
    log.info("Client Join: {},Online Clients: {}", sid, webSocketClientSet.size());
  }

  /**
   * 连接关闭调用的方法
   */
  @OnClose
  public void onClose(@PathParam(value = "sid") String sid) {
    removeClient(sid);
    webSocketClientSet.remove(this);
    onlineId.remove(this.sid);
    if (null != webSocketServerSet) {
      webSocketServerSet.sendMessage(
          new ResponseModel(CodeConstants.USER_LIST.getCode(), JSONUtils.toJson(onlineId)));
    }
    log.info("Client Leave: {}; Online Clients: {}", this.sid, onlineId.size());
  }

  /**
   * 收到客户端消息后调用的方法
   *
   * @param message 客户端发送过来的消息
   */
  @OnMessage
  public void onMessage(String message, Session session) {
    Map<String, Object> model = JSONUtils.fromJson(message, new TypeToken<>() {
    });

    if (model != null) {
      switch (new BigDecimal(model.get("code").toString()).intValue()) {
        case 2001:
          TelegramTrainLogEntity telegramTrainLogEntity = JSONUtils.fromJson(JSONUtils.toJson(model.get("data")), TelegramTrainLogEntity.class);
          CompletableFuture.runAsync(() -> {
            webSocketEventService.saveTelegramTrainLog(telegramTrainLogEntity);
            log.info("更新手键日志");
          });
          break;
        case 3001:
          TelegramTrainFloorContentEntity contentEntity = JSONUtils.fromJson(JSONUtils.toJson(model.get("data")), TelegramTrainFloorContentEntity.class);
          CompletableFuture.runAsync(() -> {
            webSocketEventService.saveTelegramTrainFloorContentEntity(contentEntity);
            log.info("更新key and time ");
          });
          break;
        default:
          break;
      }
    }
  }

  /**
   * @param session
   * @param error
   */
  @OnError
  public void onError(Session session, Throwable error) {
    log.error("WebSocketService onError:{}", error.getMessage());
  }

  /**
   * 迭代删除某个用户
   *
   * @param sid 用户id
   */
  public void removeClient(String sid) {
    for (WebSocketService next : webSocketClientSet) {
      if (Objects.equals(sid, next.getSid())) {
        webSocketClientSet.remove(next);
        onlineId.remove(sid);
      }
    }
  }

  /**
   * 实现服务器主动推送
   */
  public void sendMessage(ResponseModel message) {
    this.session.getAsyncRemote().sendText(JSONUtils.toJson(message));
  }

  public void sendMessage(String message) {
    this.session.getAsyncRemote().sendText(message);
  }


  /**
   * 群发自定义消息
   */
  public static void sendInfo(@PathParam("sid") String sid, ResponseModel message) {
    for (WebSocketService item : webSocketClientSet) {
      try {
        if (item.sid.equals(sid)) {
          String msg = JSONUtils.toJson(message);
          item.session.getAsyncRemote().sendText(msg);
        }
      } catch (Exception e) {
        log.error("WebSocketService sendInfo:{}", e.getMessage());
      }
    }
  }

  public static void sendInfoAll(@PathParam("sid") String sid, ResponseModel message) {
    for (WebSocketService item : webSocketClientSet) {
      item.sendMessage(message);
    }
  }

  public static void sendInfo(@PathParam("sid") String sid, String message) {
    for (WebSocketService item : webSocketClientSet) {
      if (item.sid.equals(sid)) {
        item.sendMessage(message);
      }
    }
  }
}
